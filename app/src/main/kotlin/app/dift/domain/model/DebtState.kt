package app.dift.domain.model

/**
 * State of the usage-debt machine (docs/features/usage-debt.md). At most one of {burst, cooldown}
 * is live at a time.
 *
 * The **burst** is the continuous-use accumulator and is ephemeral (in memory only): a process
 * restart breaks "continuous", which is correct. [burstElapsedMs] is time banked while paused;
 * [burstStartedAtMs] marks the currently-accruing segment (null = paused/idle). Live elapsed at
 * `now` = [burstElapsedMs] + (now − start).
 *
 * The **cooldown** is an absolute wall-clock deadline and IS persisted (DataStore), so it counts
 * down through locking, process death, and reboot. [cooldownStartedAtMs] is kept only so the
 * corner indicator can draw the drain fraction.
 */
data class DebtState(
    val burstElapsedMs: Long = 0,
    val burstStartedAtMs: Long? = null,
    val cooldownStartedAtMs: Long? = null,
    val cooldownUntilMs: Long? = null,
) {
    fun liveElapsedMs(nowMs: Long): Long =
        burstElapsedMs + (burstStartedAtMs?.let { (nowMs - it).coerceAtLeast(0) } ?: 0)

    fun inCooldown(nowMs: Long): Boolean = cooldownUntilMs != null && nowMs < cooldownUntilMs

    /** 1 → 0 as the cooldown drains; 0 when no live cooldown. */
    fun cooldownRemainingFraction(nowMs: Long): Float {
        val until = cooldownUntilMs ?: return 0f
        val started = cooldownStartedAtMs ?: return 0f
        val total = (until - started).toFloat()
        if (total <= 0f) return 0f
        return ((until - nowMs) / total).coerceIn(0f, 1f)
    }

    companion object {
        val IDLE = DebtState()
    }
}
