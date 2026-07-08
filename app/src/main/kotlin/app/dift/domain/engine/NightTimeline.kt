package app.dift.domain.engine

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Pure derivation for the night-aligned usage chart (docs/features/usage-overview.md).
 *
 * Builds seven noon→noon columns for an ISO week. Each column places device-usage intervals by
 * time of day (midnight at the centre) and infers the night's sleep as the longest no-use gap.
 * Time is injected (the week's Monday + zone); no wall-clock reads, no Android.
 */
object NightTimeline {

    /** A half-open interval of device use, in epoch millis. */
    data class UsageInterval(val startMs: Long, val endMs: Long)

    /** A span within a column, in minutes from the top (noon); 0..[MINUTES_PER_DAY], 720 = midnight. */
    data class Span(val startMinute: Int, val endMinute: Int)

    /**
     * One night. [morningDate] is the weekday the night leads into (the column label); the window
     * is [morningDate]−1 12:00 → [morningDate] 12:00. [sleep] is the inferred sleep gap, null only
     * when the column has no no-use gap at all.
     */
    data class NightColumn(
        val morningDate: LocalDate,
        val usage: List<Span>,
        val sleep: Span?,
    )

    /**
     * @param intervals raw per-app session intervals (any order); merged internally.
     * @param weekMonday the ISO week's Monday. Column 0 is the night whose morning is this Monday
     *   (Sunday → Monday); column 6 is Saturday → Sunday.
     */
    fun buildWeek(
        intervals: List<UsageInterval>,
        weekMonday: LocalDate,
        zone: ZoneId,
    ): List<NightColumn> {
        val merged = merge(intervals)
        return (0 until DAYS_PER_WEEK).map { i ->
            val morning = weekMonday.plusDays(i.toLong())
            buildColumn(merged, morning, zone)
        }
    }

    private fun buildColumn(merged: List<UsageInterval>, morning: LocalDate, zone: ZoneId): NightColumn {
        val startMs = morning.minusDays(1).atTime(NOON_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
        val endMs = morning.atTime(NOON_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
        val total = (endMs - startMs).toDouble()

        fun toMinute(ms: Long): Int =
            ((ms - startMs) / total * MINUTES_PER_DAY).roundToInt().coerceIn(0, MINUTES_PER_DAY)

        val spans = merged
            .filter { it.endMs > startMs && it.startMs < endMs }
            .map { Span(toMinute(it.startMs), toMinute(it.endMs)) }
            .filter { it.endMinute > it.startMinute }

        return NightColumn(morningDate = morning, usage = spans, sleep = midnightGap(spans))
    }

    /**
     * The no-use gap that contains local midnight (the column's centre) — the last evening use to
     * the first morning use. This ignores daytime idle gaps, which is the whole point: the alarm
     * wakes the screen each morning, so the gap straddling midnight is the inferred sleep. Null
     * when the phone was in use across midnight (no clear sleep); the whole column when unused.
     */
    private fun midnightGap(spans: List<Span>): Span? {
        val midnight = MINUTES_PER_DAY / 2
        if (spans.isEmpty()) return Span(0, MINUTES_PER_DAY)
        val sorted = spans.sortedBy { it.startMinute }
        val gaps = mutableListOf<Span>()
        var cursor = 0
        for (span in sorted) {
            if (span.startMinute > cursor) gaps.add(Span(cursor, span.startMinute))
            cursor = maxOf(cursor, span.endMinute)
        }
        if (cursor < MINUTES_PER_DAY) gaps.add(Span(cursor, MINUTES_PER_DAY))
        return gaps.firstOrNull { it.startMinute <= midnight && midnight <= it.endMinute }
    }

    /** Merge overlapping or near-adjacent (gap ≤ [MERGE_GAP_MS]) intervals into device-usage runs. */
    private fun merge(intervals: List<UsageInterval>): List<UsageInterval> {
        val sorted = intervals.filter { it.endMs > it.startMs }.sortedBy { it.startMs }
        val out = mutableListOf<UsageInterval>()
        for (interval in sorted) {
            val last = out.lastOrNull()
            if (last != null && interval.startMs <= last.endMs + MERGE_GAP_MS) {
                out[out.lastIndex] = last.copy(endMs = maxOf(last.endMs, interval.endMs))
            } else {
                out.add(interval)
            }
        }
        return out
    }

    const val MINUTES_PER_DAY = 24 * 60
    private const val NOON_HOUR = 12
    private const val DAYS_PER_WEEK = 7
    private const val MERGE_GAP_MS = 60_000L
}
