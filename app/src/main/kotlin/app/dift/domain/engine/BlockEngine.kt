package app.dift.domain.engine

import app.dift.domain.SafetyDenylist
import app.dift.domain.model.Block
import java.time.DayOfWeek
import java.time.ZonedDateTime

/**
 * Pure resolution of which block regime is in force (docs/features/usage-debt.md, ADR-0004).
 * Time is injected; no I/O, no wall-clock reads.
 */
object BlockEngine {

    /**
     * The strictest block whose window is active at [now]: enabled, in-window, smallest
     * `maxBurstSeconds` (ties → lowest id). Null when no block is active.
     */
    fun activeBlock(blocks: List<Block>, now: ZonedDateTime): Block? =
        blocks.filter { it.enabled && windowActive(it, now) }
            .minWithOrNull(compareBy({ it.maxBurstSeconds }, { it.id }))

    /** Is [block]'s window active at [now]? Handles midnight wrap + start-day weekday mask. */
    fun windowActive(block: Block, now: ZonedDateTime): Boolean {
        val start = block.startMinuteOfDay
        val end = block.endMinuteOfDay
        val minuteOfDay = now.hour * MINUTES_PER_HOUR + now.minute
        return if (start <= end) {
            dayEnabled(block.daysMask, now.dayOfWeek) && minuteOfDay in start until end
        } else {
            // Wrapping window (22:30 → 06:00): the stretch after `start` belongs to TODAY's mask;
            // the stretch before `end` belongs to YESTERDAY's mask (the window's start day).
            (minuteOfDay >= start && dayEnabled(block.daysMask, now.dayOfWeek)) ||
                (minuteOfDay < end && dayEnabled(block.daysMask, now.dayOfWeek.minus(1)))
        }
    }

    /**
     * A package is blockable under [block] when it is neither on the SafetyDenylist nor exempt.
     * When no block is active (a cooldown bleeding past the window), pass `block = null`: only the
     * SafetyDenylist is spared.
     */
    fun isBlockable(packageName: String, block: Block?, runtimeDenylist: Set<String>): Boolean {
        if (SafetyDenylist.isNeverBlockable(packageName, runtimeDenylist)) return false
        return block == null || packageName !in block.exemptPackages
    }

    /**
     * During a cooldown the lockout covers the whole screen (docs/features/usage-debt.md §5) —
     * home and recents get no pass; locking the phone is the way out. [packageName] escapes only
     * when it is a static safety package (dialer, Settings, SystemUI, Dift), one of
     * [escapeDenylist] (the runtime denylist WITHOUT launchers — dialer/keyboards), or an exempt
     * app of the active [block]. A cooldown outliving its window (block == null) keeps only the
     * safety escapes.
     */
    fun cooldownEscaped(packageName: String, block: Block?, escapeDenylist: Set<String>): Boolean =
        SafetyDenylist.isNeverBlockable(packageName, escapeDenylist) ||
            (block != null && packageName in block.exemptPackages)

    private fun dayEnabled(mask: Int, day: DayOfWeek): Boolean =
        (mask shr (day.value - 1)) and 1 == 1 // bit 0 = Monday

    private const val MINUTES_PER_HOUR = 60
}
