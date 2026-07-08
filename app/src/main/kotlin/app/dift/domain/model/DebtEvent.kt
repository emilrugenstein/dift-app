package app.dift.domain.model

/**
 * Inputs to the pure [app.dift.domain.engine.DebtReducer] (docs/features/usage-debt.md).
 * The system layer (DeviceStateMonitor + coordinator) translates real device signals into these;
 * the reducer stays clock-free (time is the `nowMs` parameter).
 */
sealed interface DebtEvent {
    /**
     * Periodic tick (~1 s) while the process is alive.
     *
     * @param inWindow an enabled block's window is active now. When it flips false the burst is
     *   cancelled with no debt — the night is over (spec §7).
     * @param using the user is actively using a *blockable* app: unlocked AND screen interactive
     *   AND foreground is not exempt / not on the SafetyDenylist AND no block overlay showing.
     *   `inWindow && !using` (e.g. an exempt app is foreground) *pauses* the burst without
     *   ending it, so "continuous" survives a glance at an allowed app.
     */
    data class Tick(val inWindow: Boolean, val using: Boolean) : DebtEvent

    /** Screen off or keyguard shown — ends the current burst and banks it as cooldown. */
    data object Lock : DebtEvent
}
