package app.dift.system.blocking

import android.util.Log
import app.dift.data.datastore.SettingsRepository
import app.dift.data.repo.BlockEventRepository
import app.dift.data.repo.BlockRepository
import app.dift.domain.engine.BlockEngine
import app.dift.domain.engine.DebtReducer
import app.dift.domain.model.Block
import app.dift.domain.model.BlockOutcome
import app.dift.domain.model.BlockReason
import app.dift.domain.model.DebtEvent
import app.dift.domain.model.DebtState
import app.dift.system.detect.ForegroundAppTracker
import app.dift.system.detect.ForegroundProbe
import app.dift.system.detect.OwnAppForegroundTracker
import app.dift.system.device.DeviceStateMonitor
import app.dift.system.overlay.BlockScreenContent
import app.dift.system.overlay.IndicatorContent
import app.dift.system.overlay.IndicatorOverlayController
import app.dift.system.overlay.IndicatorState
import app.dift.system.overlay.OverlayController
import app.dift.system.packages.RuntimeDenylistProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The blocking brain (ARCHITECTURE.md). Runs the pure usage-debt machine against live device
 * signals and drives two overlays: the full-screen lockout ([OverlayController]) while a cooldown
 * is served, and the small corner indicator ([IndicatorOverlayController]) whenever a block window
 * is active or a cooldown is draining. A 1 s ticker advances the burst/cooldown so blocks fire
 * mid-use, not only on app switch. No Activity is ever launched (ADR-0003).
 *
 * Robustness: every loop survives per-iteration failures (an exception must never silently kill
 * the ticker — that is a frozen ring), and a periodic resync re-reads lock/screen state and the
 * true foreground from UsageStats so a missed broadcast or accessibility event self-heals within
 * [RESYNC_MS] (docs/features/usage-debt.md, "Hang safety net").
 */
@Singleton
@Suppress("LongParameterList") // the hot-path brain wires every live signal; splitting hides that
class BlockingCoordinator @Inject constructor(
    private val tracker: ForegroundAppTracker,
    private val ownAppTracker: OwnAppForegroundTracker,
    private val foregroundProbe: ForegroundProbe,
    private val blockRepository: BlockRepository,
    private val blockEventRepository: BlockEventRepository,
    private val settings: SettingsRepository,
    private val overlayController: OverlayController,
    private val indicatorController: IndicatorOverlayController,
    private val runtimeDenylist: RuntimeDenylistProvider,
    private val deviceStateMonitor: DeviceStateMonitor,
) {
    private val scope = CoroutineScope(SupervisorJob())
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val blocksCache = blockRepository.blocks
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val debtState = MutableStateFlow(DebtState.IDLE)
    private val indicator = MutableStateFlow<IndicatorState>(IndicatorState.Hidden)

    private var wasAwakeUnlocked = false
    private var shownCooldownUntil: Long? = null

    @Volatile
    private var started = false

    /** Idempotent: called by whichever detection service is active; extra calls are no-ops. */
    @Synchronized
    fun start() {
        if (started) return
        started = true
        deviceStateMonitor.start()
        scope.launch { runCatching { runtimeDenylist.refresh() }.onFailure(::logFailure) }
        scope.launch { runCatching { restoreCooldown() }.onFailure(::logFailure) }
        collectSafely(combine(tracker.foregroundPackage, blocksCache) { pkg, _ -> pkg }) { evaluate(now()) }
        collectSafely(ownAppTracker.foreground) { evaluate(now()) }
        collectSafely(deviceStateMonitor.state) { onDeviceState(it) }
        loopSafely(TICK_MS) { tick() }
        loopSafely(RESYNC_MS) { resync() }
    }

    /** A collector whose per-item failure is logged, not fatal — the flow keeps being consumed. */
    private fun <T> collectSafely(flow: Flow<T>, action: (T) -> Unit) {
        scope.launch {
            flow.collect { runCatching { action(it) }.onFailure(::logFailure) }
        }
    }

    /** A periodic loop that cannot die: one failed iteration is logged and the next one runs. */
    private fun loopSafely(periodMs: Long, action: () -> Unit) {
        scope.launch {
            while (scope.isActive) {
                delay(periodMs)
                runCatching { action() }.onFailure(::logFailure)
            }
        }
    }

    private suspend fun restoreCooldown() {
        val until = settings.cooldownUntil.first()
        if (until != null) {
            debtState.value = DebtState(
                cooldownStartedAtMs = settings.cooldownStartedAt.first(),
                cooldownUntilMs = until,
            )
        }
        val device = deviceStateMonitor.state.value
        wasAwakeUnlocked = device.screenOn && device.unlocked
        evaluate(now())
    }

    private fun onDeviceState(device: DeviceStateMonitor.DeviceState) {
        val nowMs = now()
        val awakeUnlocked = device.screenOn && device.unlocked
        if (wasAwakeUnlocked && !awakeUnlocked) {
            // Locking / screen-off ends the burst and banks it as cooldown (spec §4).
            applyDebt(DebtReducer.reduce(debtState.value, DebtEvent.Lock, nowMs, configAt(nowMs)), nowMs)
        }
        wasAwakeUnlocked = awakeUnlocked
        evaluate(nowMs)
    }

    private fun tick() {
        val nowMs = now()
        val block = activeBlock(nowMs)
        val device = deviceStateMonitor.state.value
        val fg = tracker.foregroundPackage.value
        val using = block != null && device.unlocked && device.screenOn && !overlayController.isShowing &&
            fg != null && BlockEngine.isBlockable(fg, block, runtimeDenylist.current())
        val event = DebtEvent.Tick(inWindow = block != null, using = using)
        applyDebt(DebtReducer.reduce(debtState.value, event, nowMs, config(block)), nowMs)
        evaluate(nowMs)
    }

    /**
     * The hang safety net: re-read lock/screen state from the system (a missed broadcast otherwise
     * sticks forever) and, while a block window or cooldown is live, replay the true foreground
     * from UsageStats so a missed accessibility event cannot freeze the ring mid-fill.
     */
    private fun resync() {
        deviceStateMonitor.refresh()
        val nowMs = now()
        val enforcementLive = activeBlock(nowMs) != null || debtState.value.inCooldown(nowMs)
        if (enforcementLive && deviceStateMonitor.state.value.screenOn) {
            foregroundProbe.resync()
        }
    }

    /** Persist only the cooldown (the burst is ephemeral) and log when a cooldown is fully served. */
    private fun applyDebt(next: DebtState, nowMs: Long) {
        val prev = debtState.value
        if (next == prev) return
        debtState.value = next
        if (prev.cooldownUntilMs != next.cooldownUntilMs) {
            val served = prev.cooldownUntilMs != null && next.cooldownUntilMs == null
            scope.launch {
                settings.setCooldown(next.cooldownStartedAtMs, next.cooldownUntilMs)
                if (served) {
                    blockEventRepository.log("", null, BlockReason.USAGE_DEBT, BlockOutcome.DEBT_SERVED, nowMs)
                }
            }
        }
    }

    private fun evaluate(nowMs: Long) {
        val block = activeBlock(nowMs)
        val state = debtState.value
        enforce(nowMs, block, state)
        updateIndicator(nowMs, block, state)
    }

    /**
     * Cooldown enforcement is whole-screen (spec §5): while the device is awake and unlocked, the
     * lockout covers everything — home and recents included — except the safety escapes (dialer,
     * Settings, keyboards, a block's exempt apps) and Dift itself. Locking the phone is the way
     * out; the cooldown keeps draining either way.
     */
    private fun enforce(nowMs: Long, block: Block?, state: DebtState) {
        val fg = tracker.foregroundPackage.value
        val until = state.cooldownUntilMs
        val device = deviceStateMonitor.state.value
        val awakeUnlocked = device.unlocked && device.screenOn
        val escaped = ownAppTracker.foreground.value ||
            (fg != null && BlockEngine.cooldownEscaped(fg, block, runtimeDenylist.currentEscapes()))
        val blocked = until != null && nowMs < until && awakeUnlocked && !escaped
        if (blocked && until != null) {
            if (!overlayController.isShowing || shownCooldownUntil != until) {
                shownCooldownUntil = until
                overlayController.show { BlockScreenContent(blockName = block?.name, cooldownUntilMs = until) }
                logEvent(fg.orEmpty(), block?.id, BlockOutcome.SHOWN, nowMs)
            }
        } else {
            if (overlayController.isShowing) overlayController.hide()
            shownCooldownUntil = null
        }
    }

    private fun updateIndicator(nowMs: Long, block: Block?, state: DebtState) {
        val next = when {
            state.inCooldown(nowMs) -> IndicatorState.Blocked(state.cooldownRemainingFraction(nowMs))
            block != null -> {
                val maxMs = (block.maxBurstSeconds * MILLIS_PER_SECOND).toFloat()
                IndicatorState.Counting((state.liveElapsedMs(nowMs) / maxMs).coerceIn(0f, 1f))
            }
            else -> IndicatorState.Hidden
        }
        indicator.value = next
        if (next == IndicatorState.Hidden) {
            indicatorController.hide()
        } else {
            indicatorController.show { IndicatorContent(indicator) }
        }
    }

    private fun logEvent(pkg: String, blockId: Long?, outcome: BlockOutcome, nowMs: Long) {
        scope.launch { blockEventRepository.log(pkg, blockId, BlockReason.USAGE_DEBT, outcome, nowMs) }
    }

    private fun logFailure(failure: Throwable) {
        Log.w(TAG, "blocking pipeline iteration failed", failure)
    }

    private fun activeBlock(nowMs: Long): Block? =
        BlockEngine.activeBlock(blocksCache.value, zoned(nowMs))

    private fun config(block: Block?) =
        DebtReducer.Config(block?.maxBurstSeconds ?: Block.DEFAULT_MAX_BURST_SECONDS)

    private fun configAt(nowMs: Long) = config(activeBlock(nowMs))

    private fun zoned(nowMs: Long): ZonedDateTime = Instant.ofEpochMilli(nowMs).atZone(zone)

    private fun now(): Long = System.currentTimeMillis()

    private companion object {
        const val TAG = "BlockingCoordinator"
        const val TICK_MS = 1_000L
        const val RESYNC_MS = 5 * 60_000L
        const val MILLIS_PER_SECOND = 1_000L
    }
}
