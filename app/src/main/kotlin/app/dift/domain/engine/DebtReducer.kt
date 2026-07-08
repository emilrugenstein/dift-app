package app.dift.domain.engine

import app.dift.domain.model.DebtEvent
import app.dift.domain.model.DebtState

/**
 * Pure state machine for the night usage-debt mechanic (docs/features/usage-debt.md, ADR-0004).
 *
 * Model: at most one of {burst, debt} is active at a time. While debt is active
 * (`debtUntilMs` in the future) no burst accrues — overlay time is free. A burst converts to
 * debt only by hitting the cap (a Tick) or by locking/screen-off (a Lock). Leaving the window
 * merely cancels the burst (no debt); an already-running debt keeps counting down.
 */
object DebtReducer {

    data class Config(
        val maxBurstSeconds: Int,
        val debtRatio: Float,
    )

    fun reduce(state: DebtState, event: DebtEvent, nowMs: Long, config: Config): DebtState {
        val maxBurstMs = config.maxBurstSeconds * MILLIS_PER_SECOND
        return when (event) {
            is DebtEvent.Unlock -> onUnlock(state, event, nowMs)
            DebtEvent.Lock -> onLock(state, nowMs, maxBurstMs, config.debtRatio)
            is DebtEvent.Tick -> onTick(state, event, nowMs, maxBurstMs, config.debtRatio)
        }
    }

    private fun onUnlock(state: DebtState, event: DebtEvent.Unlock, nowMs: Long): DebtState =
        when {
            state.debtActiveAt(nowMs) -> state // serving debt: overlay shows, no burst
            event.inWindow && state.burstStartedAtMs == null -> state.copy(burstStartedAtMs = nowMs)
            else -> state
        }

    private fun onLock(state: DebtState, nowMs: Long, maxBurstMs: Long, ratio: Float): DebtState {
        if (state.debtActiveAt(nowMs)) return state.copy(burstStartedAtMs = null)
        val start = state.burstStartedAtMs ?: return state
        val elapsed = (nowMs - start).coerceIn(0, maxBurstMs)
        val debtMs = (elapsed * ratio).toLong()
        return DebtState(
            burstStartedAtMs = null,
            debtUntilMs = if (debtMs > 0) nowMs + debtMs else null,
        )
    }

    private fun onTick(
        state: DebtState,
        event: DebtEvent.Tick,
        nowMs: Long,
        maxBurstMs: Long,
        ratio: Float,
    ): DebtState {
        // 1) Serving debt: keep going until it expires, then clear it (DEBT_SERVED).
        if (state.debtUntilMs != null) {
            return if (nowMs >= state.debtUntilMs) state.copy(debtUntilMs = null) else state
        }
        // 2) Not eligible to accrue (out of window, locked, or overlay up): cancel any burst.
        if (!event.inWindow || !event.unlocked || event.overlayShowing) {
            return if (state.burstStartedAtMs != null) state.copy(burstStartedAtMs = null) else state
        }
        // 3) Eligible: start a burst, or fire the cap once it is reached.
        val start = state.burstStartedAtMs ?: return state.copy(burstStartedAtMs = nowMs)
        return if (nowMs - start >= maxBurstMs) {
            DebtState(burstStartedAtMs = null, debtUntilMs = nowMs + (maxBurstMs * ratio).toLong())
        } else {
            state
        }
    }

    private const val MILLIS_PER_SECOND = 1000L
}
