package app.dift.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A named period with a daily time goal (docs/features/challenges.md). Days are inclusive local
 * calendar days; the goal is an *average* — balance is judged over the elapsed days, not per day.
 */
data class Challenge(
    val id: Long = 0,
    val name: String,
    val startDay: LocalDate,
    val endDay: LocalDate,
    val goalMinutesPerDay: Int = DEFAULT_GOAL_MINUTES,
) {
    val totalDays: Int get() = (ChronoUnit.DAYS.between(startDay, endDay) + 1).toInt()

    fun covers(day: LocalDate): Boolean = !day.isBefore(startDay) && !day.isAfter(endDay)

    companion object {
        const val DEFAULT_GOAL_MINUTES = 30
        const val GOAL_STEP_MINUTES = 5
        const val MIN_GOAL_MINUTES = 5
        const val MAX_GOAL_MINUTES = 12 * 60
    }
}

/**
 * One logged stretch of challenge time. Timed entries carry their real clock position; a
 * [manual] entry (added in hindsight) is anchored at noon of [day] and only its duration matters.
 */
data class ChallengeEntry(
    val id: Long = 0,
    val challengeId: Long,
    val startMs: Long,
    val endMs: Long,
    val day: LocalDate,
    val manual: Boolean,
) {
    val durationMs: Long get() = endMs - startMs
}

/** The single running timer, persisted so it survives process death and reboot. */
data class ChallengeTimer(val challengeId: Long, val startedAtMs: Long)
