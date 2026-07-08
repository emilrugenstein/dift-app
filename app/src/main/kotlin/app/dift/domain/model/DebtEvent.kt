package app.dift.domain.model

/**
 * Inputs to the pure [app.dift.domain.engine.DebtReducer] (docs/features/usage-debt.md).
 * The system layer (DeviceStateMonitor + coordinator) translates real device signals into
 * these; the reducer stays clock-free (time is the `nowMs` parameter).
 */
sealed interface DebtEvent {
    /** Keyguard dismissed. [inWindow] = the night schedule is currently active. */
    data class Unlock(val inWindow: Boolean) : DebtEvent

    /** Screen off or keyguard shown — ends the current usage burst. */
    data object Lock : DebtEvent

    /**
     * Periodic tick (~1 s) while the device is awake. Carries the live gating conditions so
     * the reducer can decide whether burst time accrues, the cap fired, or debt was served.
     */
    data class Tick(
        val inWindow: Boolean,
        val unlocked: Boolean,
        val overlayShowing: Boolean,
    ) : DebtEvent
}
