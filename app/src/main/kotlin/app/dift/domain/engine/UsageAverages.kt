package app.dift.domain.engine

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Pure averages for the daily-totals view (docs/features/usage-overview.md): the displayed week's
 * usage per day, compared to the previous week's and the previous 30 days' averages.
 *
 * Denominators only count days that can have data: the current week stops at [today], and days
 * before [earliestDay] (first recorded data) are excluded — a fresh install must not drag the
 * reference averages toward zero. Time is injected; no wall-clock reads, no Android.
 */
object UsageAverages {

    data class Result(
        val avgMsPerDay: Long,
        val vsPrevWeekPct: Int?,
        val vsPrevMonthPct: Int?,
    )

    /**
     * @param totalsByDay total usage per local day; missing days inside a covered range count as 0.
     * @param weekMonday the displayed ISO week's Monday.
     * @param today the current local day (bounds the current week's denominator).
     * @param earliestDay first day with recorded data; null means "today only" (nothing stored yet).
     */
    fun forWeek(
        totalsByDay: Map<LocalDate, Long>,
        weekMonday: LocalDate,
        today: LocalDate,
        earliestDay: LocalDate?,
    ): Result? {
        if (today.isBefore(weekMonday)) return null
        val earliest = earliestDay ?: today
        val weekEnd = minOf(weekMonday.plusDays(DAYS_PER_WEEK - 1L), today)
        val weekAvg = averageOver(totalsByDay, weekMonday, weekEnd, earliest) ?: return null
        val prevWeekAvg =
            averageOver(totalsByDay, weekMonday.minusDays(DAYS_PER_WEEK.toLong()), weekMonday.minusDays(1), earliest)
        val prevMonthAvg =
            averageOver(totalsByDay, weekMonday.minusDays(MONTH_DAYS.toLong()), weekMonday.minusDays(1), earliest)
        return Result(
            avgMsPerDay = weekAvg,
            vsPrevWeekPct = percentChange(weekAvg, prevWeekAvg),
            vsPrevMonthPct = percentChange(weekAvg, prevMonthAvg),
        )
    }

    /** Mean over the days of [from]..[to] that are ≥ [earliest]; null when none qualify. */
    private fun averageOver(
        totalsByDay: Map<LocalDate, Long>,
        from: LocalDate,
        to: LocalDate,
        earliest: LocalDate,
    ): Long? {
        val days = generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .filter { !it.isBefore(earliest) }
            .toList()
        if (days.isEmpty()) return null
        return days.sumOf { totalsByDay[it] ?: 0L } / days.size
    }

    /** Signed percent change of [current] vs [reference]; null without a usable reference. */
    private fun percentChange(current: Long, reference: Long?): Int? {
        if (reference == null || reference <= 0L) return null
        return ((current - reference) * PERCENT / reference.toDouble()).roundToInt()
    }

    const val MONTH_DAYS = 30

    private const val DAYS_PER_WEEK = 7
    private const val PERCENT = 100.0
}
