package app.dift.domain.engine

import app.dift.domain.model.Challenge
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Pure progress maths for a challenge (docs/features/challenges.md). The goal is an average:
 * being "on track" means the total so far is at least `elapsedDays × goal`, so a 25-minute day
 * followed by a 35-minute day is fine. Time is injected ([snapshot] takes `today`).
 */
object ChallengeProgress {

    data class Snapshot(
        val todayMs: Long,
        val totalMs: Long,
        val elapsedDays: Int,
        val totalDays: Int,
        val remainingDays: Int,
        val goalMs: Long,
        val targetSoFarMs: Long,
        val targetTotalMs: Long,
        val averagePerDayMs: Long,
        val balanceMs: Long,
        val neededPerRemainingDayMs: Long,
    ) {
        val onTrack: Boolean get() = balanceMs >= 0L

        val todayFraction: Float
            get() = if (goalMs <= 0L) 0f else (todayMs.toFloat() / goalMs).coerceIn(0f, 1f)

        val totalFraction: Float
            get() = if (targetTotalMs <= 0L) 0f else (totalMs.toFloat() / targetTotalMs).coerceIn(0f, 1f)
    }

    /**
     * @param dayTotals logged ms per local day; the caller adds a running timer to today first.
     *   Days outside the challenge period are ignored.
     */
    fun snapshot(challenge: Challenge, dayTotals: Map<LocalDate, Long>, today: LocalDate): Snapshot {
        val goalMs = challenge.goalMinutesPerDay * MINUTE_MS
        val totalDays = challenge.totalDays
        val elapsedDays = when {
            today.isBefore(challenge.startDay) -> 0
            today.isAfter(challenge.endDay) -> totalDays
            else -> (ChronoUnit.DAYS.between(challenge.startDay, today) + 1).toInt()
        }
        val remainingDays = when {
            today.isAfter(challenge.endDay) -> 0
            today.isBefore(challenge.startDay) -> totalDays
            else -> (ChronoUnit.DAYS.between(today, challenge.endDay) + 1).toInt()
        }
        val totalMs = dayTotals.filterKeys { challenge.covers(it) }.values.sum()
        val todayMs = if (challenge.covers(today)) dayTotals[today] ?: 0L else 0L
        val targetSoFarMs = elapsedDays * goalMs
        val targetTotalMs = totalDays * goalMs
        val remainingMs = (targetTotalMs - totalMs).coerceAtLeast(0L)
        return Snapshot(
            todayMs = todayMs,
            totalMs = totalMs,
            elapsedDays = elapsedDays,
            totalDays = totalDays,
            remainingDays = remainingDays,
            goalMs = goalMs,
            targetSoFarMs = targetSoFarMs,
            targetTotalMs = targetTotalMs,
            averagePerDayMs = if (elapsedDays == 0) 0L else totalMs / elapsedDays,
            balanceMs = totalMs - targetSoFarMs,
            neededPerRemainingDayMs = if (remainingDays == 0) 0L else remainingMs / remainingDays,
        )
    }

    private const val MINUTE_MS = 60_000L
}
