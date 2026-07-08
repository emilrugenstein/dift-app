package app.dift.domain.model

/**
 * A usage-debt block (docs/features/usage-debt.md) — Dift's only blocking primitive.
 *
 * The block is active while [now] falls in its window on an enabled weekday. Windows may wrap
 * midnight; a wrapping window belongs to its START day's [daysMask] (bit 0 = Monday). While
 * active, continuous use of any *blockable* app (not in [exemptPackages], not on the
 * SafetyDenylist) accrues a burst; hitting [maxBurstSeconds] locks the device for an equal
 * cooldown. Blocks are always device-wide and HARD — there is no unblock path.
 */
data class Block(
    val id: Long,
    val name: String,
    val enabled: Boolean,
    /** Bit 0 = Monday … bit 6 = Sunday. The day the window STARTS on. */
    val daysMask: Int,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val maxBurstSeconds: Int = DEFAULT_MAX_BURST_SECONDS,
    /** Apps usable during the block that never accrue debt. SafetyDenylist is exempt on top. */
    val exemptPackages: Set<String> = emptySet(),
) {
    companion object {
        const val DEFAULT_MAX_BURST_SECONDS = 60
        const val ALL_DAYS = 0b1111111
        const val DEFAULT_START_MINUTE = 22 * 60 + 30 // 22:30
        const val DEFAULT_END_MINUTE = 6 * 60 // 06:00
    }
}
