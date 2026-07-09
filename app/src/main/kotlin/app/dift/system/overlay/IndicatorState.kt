package app.dift.system.overlay

/**
 * What the top-right corner indicator shows (docs/features/usage-debt.md). Computed by the
 * coordinator each tick from the active block + [app.dift.domain.model.DebtState].
 */
sealed interface IndicatorState {
    /** No block window active and no cooldown — the window is hidden entirely. */
    data object Hidden : IndicatorState

    /** A block is active: the ring fills 0 → 1 as the burst approaches the cap. */
    data class Counting(val fraction: Float) : IndicatorState

    /** A cooldown is serving: the ring drains 1 → 0 as time is repaid. */
    data class Blocked(val fraction: Float) : IndicatorState
}
