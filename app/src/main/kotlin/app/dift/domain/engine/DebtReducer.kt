package app.dift.domain.engine

import app.dift.domain.model.DebtEvent
import app.dift.domain.model.DebtState

/**
 * Pure state machine for usage-debt (docs/features/usage-debt.md, ADR-0004).
 *
 * A burst accumulates while a tick is `using`; pausing (`using = false`, still in-window) banks the
 * segment without resetting it, so "continuous" survives a glance at an exempt app. Leaving the
 * window cancels the burst with no debt. Reaching the cap — or a [DebtEvent.Lock] mid-burst —
 * converts the elapsed time 1:1 into a cooldown: an absolute deadline that counts down regardless
 * of screen/lock state. While serving a cooldown no burst accrues (overlay time is free).
 */
object DebtReducer {

    data class Config(val maxBurstSeconds: Int)

    fun reduce(state: DebtState, event: DebtEvent, nowMs: Long, config: Config): DebtState {
        // A finished cooldown clears first, so both branches see a clean base (and the coordinator
        // can detect DEBT_SERVED by the cooldown going null).
        val base = if (state.cooldownUntilMs != null && nowMs >= state.cooldownUntilMs) {
            state.copy(cooldownStartedAtMs = null, cooldownUntilMs = null)
        } else {
            state
        }
        val maxBurstMs = config.maxBurstSeconds * MILLIS_PER_SECOND
        return when (event) {
            DebtEvent.Lock -> onLock(base, nowMs, maxBurstMs)
            is DebtEvent.Tick -> onTick(base, event.inWindow, event.using, nowMs, maxBurstMs)
        }
    }

    private fun onLock(state: DebtState, nowMs: Long, maxBurstMs: Long): DebtState {
        if (state.inCooldown(nowMs)) return state.resetBurst()
        val elapsed = state.liveElapsedMs(nowMs)
        return if (elapsed <= 0) DebtState.IDLE else cooldown(nowMs, elapsed, maxBurstMs)
    }

    private fun onTick(
        state: DebtState,
        inWindow: Boolean,
        using: Boolean,
        nowMs: Long,
        maxBurstMs: Long,
    ): DebtState {
        if (state.inCooldown(nowMs)) return state.resetBurst()
        // Window over: cancel any burst without creating debt (the night is done).
        if (!inWindow) return state.resetBurst()
        if (!using) {
            // Pause: bank the running segment, keep the accumulator.
            return if (state.burstStartedAtMs != null) {
                state.copy(
                    burstElapsedMs = state.liveElapsedMs(nowMs),
                    burstStartedAtMs = null,
                )
            } else {
                state
            }
        }
        val started = state.burstStartedAtMs ?: nowMs
        val elapsed = state.burstElapsedMs + (nowMs - started)
        return if (elapsed >= maxBurstMs) {
            cooldown(nowMs, elapsed, maxBurstMs)
        } else {
            state.copy(burstStartedAtMs = started)
        }
    }

    private fun cooldown(nowMs: Long, elapsedMs: Long, maxBurstMs: Long) = DebtState(
        cooldownStartedAtMs = nowMs,
        cooldownUntilMs = nowMs + elapsedMs.coerceAtMost(maxBurstMs),
    )

    private fun DebtState.resetBurst(): DebtState =
        if (burstElapsedMs == 0L && burstStartedAtMs == null) this
        else copy(burstElapsedMs = 0, burstStartedAtMs = null)

    private const val MILLIS_PER_SECOND = 1000L
}
