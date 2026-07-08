package app.dift.system.blocking

import app.dift.data.datastore.SettingsRepository
import app.dift.data.repo.BlockEventRepository
import app.dift.data.repo.GrantRepository
import app.dift.data.repo.RuleRepository
import app.dift.data.repo.UsageRepository
import app.dift.domain.engine.RuleEngine
import app.dift.domain.engine.Verdict
import app.dift.domain.model.BlockOutcome
import app.dift.domain.model.DebtState
import app.dift.domain.model.GrantMethod
import app.dift.domain.model.Rule
import app.dift.domain.model.RuleType
import app.dift.system.detect.ForegroundAppTracker
import app.dift.system.ingest.UsageStatsIngester
import app.dift.system.overlay.BlockScreenContent
import app.dift.system.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The blocking brain (ARCHITECTURE.md). Combines the foreground package with a hot in-memory
 * rule cache (never touches the DB on the hot path), evaluates the pure RuleEngine, and drives
 * the overlay + home-kick. A 1 s ticker re-evaluates while a limit/debt situation is live so
 * blocks fire mid-session, not only on app switch.
 *
 * Home-kick is optional: [homeKick] is wired by the accessibility service when bound, and null
 * in fallback (polling) mode where activity starts are BAL-restricted (ADR-0003).
 */
@Singleton
class BlockingCoordinator @Inject constructor(
    private val tracker: ForegroundAppTracker,
    private val ruleRepository: RuleRepository,
    private val grantRepository: GrantRepository,
    private val blockEventRepository: BlockEventRepository,
    private val settings: SettingsRepository,
    private val ingester: UsageStatsIngester,
    private val usageRepository: UsageRepository,
    private val overlayController: OverlayController,
) {
    private val scope = CoroutineScope(SupervisorJob())
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val rulesCache = ruleRepository.rules
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val debtState = MutableStateFlow(DebtState.IDLE)

    /** Set by the accessibility service; null while unbound (fallback mode). */
    @Volatile
    var homeKick: (() -> Unit)? = null

    private var currentBlockRuleId: Long? = null

    @Volatile
    private var started = false

    fun updateDebtState(state: DebtState) {
        debtState.value = state
    }

    /** Idempotent: called by whichever detection service is active; extra calls are no-ops. */
    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(tracker.foregroundPackage, rulesCache, debtState) { pkg, rules, debt ->
                Triple(pkg, rules, debt)
            }.collect { (pkg, rules, debt) ->
                evaluateAndAct(pkg, rules, debt)
            }
        }
        scope.launch { tickWhileActive() }
    }

    private suspend fun tickWhileActive() {
        while (scope.isActive) {
            delay(TICK_MS)
            val pkg = tracker.foregroundPackage.value
            if (pkg != null && (overlayController.isShowing || hasTimedRule(rulesCache.value))) {
                evaluateAndAct(pkg, rulesCache.value, debtState.value)
            }
        }
    }

    private suspend fun evaluateAndAct(packageName: String?, rules: List<Rule>, debt: DebtState) {
        if (packageName == null) {
            clearBlock()
            return
        }
        val now = ZonedDateTime.now(zone)
        val nowMs = now.toInstant().toEpochMilli()
        val usedTodayMs = usedTodayMs(packageName)
        val grants = grantRepository.activeAt(nowMs)

        val verdict = RuleEngine.evaluate(
            RuleEngine.EvaluationInput(
                packageName = packageName,
                rules = rules,
                usedTodayMs = usedTodayMs,
                activeGrants = grants,
                debtState = debt,
                runtimeDenylist = emptySet(),
                now = now,
            ),
        )

        when (verdict) {
            is Verdict.Allow -> clearBlock()
            is Verdict.Block -> applyBlock(packageName, verdict, nowMs)
        }
    }

    private suspend fun applyBlock(packageName: String, verdict: Verdict.Block, nowMs: Long) {
        homeKick?.invoke()
        if (currentBlockRuleId != verdict.ruleId || !overlayController.isShowing) {
            currentBlockRuleId = verdict.ruleId
            val phrase = settings.defaultFrictionPhrase.first()
            overlayController.show {
                BlockScreenContent(
                    verdict = verdict,
                    packageName = packageName,
                    frictionPhrase = phrase,
                    onUnblock = { method -> unblock(packageName, verdict, method) },
                )
            }
            blockEventRepository.log(
                packageName = packageName,
                ruleId = verdict.ruleId,
                reason = verdict.reason,
                outcome = BlockOutcome.SHOWN,
                nowMs = nowMs,
            )
        }
    }

    private fun unblock(packageName: String, verdict: Verdict.Block, method: GrantMethod) {
        scope.launch {
            val nowMs = System.currentTimeMillis()
            grantRepository.grant(packageName, verdict.ruleId, method, verdict.grantMinutes, nowMs)
            blockEventRepository.log(
                packageName = packageName,
                ruleId = verdict.ruleId,
                reason = verdict.reason,
                outcome = if (method == GrantMethod.TAP) {
                    BlockOutcome.UNBLOCKED_TAP
                } else {
                    BlockOutcome.UNBLOCKED_FRICTION
                },
                nowMs = nowMs,
            )
            clearBlock()
        }
    }

    private fun clearBlock() {
        if (overlayController.isShowing) overlayController.hide()
        currentBlockRuleId = null
    }

    private suspend fun usedTodayMs(packageName: String): Long {
        val dayLocal = java.time.LocalDate.now(zone).toString()
        val stored = usageRepository.totalFor(dayLocal, packageName)
        val open = ingester.openSessionElapsed(packageName, System.currentTimeMillis())
        return stored + open
    }

    private fun hasTimedRule(rules: List<Rule>): Boolean =
        rules.any {
            it.enabled && (it.type == RuleType.DAILY_LIMIT || it.type == RuleType.USAGE_DEBT)
        }

    private companion object {
        const val TICK_MS = 1_000L
    }
}
