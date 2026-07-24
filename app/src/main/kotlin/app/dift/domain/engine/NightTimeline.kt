package app.dift.domain.engine

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Pure derivation for the night-aligned usage chart (docs/features/usage-overview.md).
 *
 * Builds seven noon→noon columns for an ISO week. Each column places device-usage intervals by
 * time of day (midnight at the centre) and infers the night as the longest no-use gap that
 * intersects the 03:30–06:00 detection window. Usage carries a "not bad" category flag through
 * to the spans so the chart can color the two kinds of use differently.
 * Time is injected (the week's Monday + zone); no wall-clock reads, no Android.
 */
object NightTimeline {

    /** A half-open interval of device use, in epoch millis. [notBad] tags "Not bad" app usage. */
    data class UsageInterval(val startMs: Long, val endMs: Long, val notBad: Boolean = false)

    /**
     * A span within a column, in minutes from the top (noon); 0..[MINUTES_PER_DAY], 720 = midnight.
     * [notBad] carries the usage category; it is meaningless on the sleep span.
     */
    data class Span(val startMinute: Int, val endMinute: Int, val notBad: Boolean = false)

    /**
     * One night. [morningDate] is the weekday the night leads into (the column label); the window
     * is [morningDate]−1 12:00 → [morningDate] 12:00. [sleep] is the inferred night gap, null when
     * the night has not happened yet or the phone was in use across the whole detection window.
     */
    data class NightColumn(
        val morningDate: LocalDate,
        val usage: List<Span>,
        val sleep: Span?,
    )

    /** The two usage categories merged separately + their union (the union defines the gaps). */
    private class Merged(intervals: List<UsageInterval>) {
        val all = merge(intervals)
        val notBad = merge(intervals.filter { it.notBad })
        val other = merge(intervals.filter { !it.notBad })
    }

    /**
     * @param intervals raw per-app session intervals (any order); merged internally per category.
     * @param weekMonday the ISO week's Monday. Column 0 is the night whose morning is this Monday
     *   (Sunday → Monday); column 6 is Saturday → Sunday.
     * @param nowMs the current instant. A night whose 04:30 anchor is still in the future has no
     *   sleep gap yet — the night has not happened, so nothing is highlighted.
     */
    fun buildWeek(
        intervals: List<UsageInterval>,
        weekMonday: LocalDate,
        zone: ZoneId,
        nowMs: Long,
    ): List<NightColumn> {
        val merged = Merged(intervals)
        return (0 until DAYS_PER_WEEK).map { i ->
            val morning = weekMonday.plusDays(i.toLong())
            buildColumn(merged, morning, zone, nowMs)
        }
    }

    private fun buildColumn(
        merged: Merged,
        morning: LocalDate,
        zone: ZoneId,
        nowMs: Long,
    ): NightColumn {
        val startMs = morning.minusDays(1).atTime(NOON_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
        val endMs = morning.atTime(NOON_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
        val total = (endMs - startMs).toDouble()

        fun toMinute(ms: Long): Int =
            ((ms - startMs) / total * MINUTES_PER_DAY).roundToInt().coerceIn(0, MINUTES_PER_DAY)

        fun spansOf(intervals: List<UsageInterval>, notBad: Boolean): List<Span> = intervals
            .filter { it.endMs > startMs && it.startMs < endMs }
            .map { Span(toMinute(it.startMs), toMinute(it.endMs), notBad) }
            .filter { it.endMinute > it.startMinute }

        val anchorMs = morning.atTime(ANCHOR_HOUR, ANCHOR_MINUTE_OF_HOUR)
            .atZone(zone).toInstant().toEpochMilli()
        val sleep = if (anchorMs > nowMs) null else sleepGap(spansOf(merged.all, false))
        return NightColumn(
            morningDate = morning,
            usage = spansOf(merged.other, false) + spansOf(merged.notBad, true),
            sleep = sleep,
        )
    }

    /**
     * The night: the longest no-use gap that intersects the 03:30–06:00 detection window. The
     * window (not a single anchor) makes the pick dynamic: a brief 4 a.m. wake-up no longer ends
     * the night, and use running past midnight only delays its start. Null when the phone was in
     * use across the whole window (no clear night); the whole column when unused.
     */
    private fun sleepGap(spans: List<Span>): Span? {
        if (spans.isEmpty()) return Span(0, MINUTES_PER_DAY)
        val sorted = spans.sortedBy { it.startMinute }
        val gaps = mutableListOf<Span>()
        var cursor = 0
        for (span in sorted) {
            if (span.startMinute > cursor) gaps.add(Span(cursor, span.startMinute))
            cursor = maxOf(cursor, span.endMinute)
        }
        if (cursor < MINUTES_PER_DAY) gaps.add(Span(cursor, MINUTES_PER_DAY))
        return gaps
            .filter { it.startMinute < NIGHT_WINDOW_END_MINUTE && it.endMinute > NIGHT_WINDOW_START_MINUTE }
            .maxByOrNull { it.endMinute - it.startMinute }
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

    /** Night-detection window, minutes from the column top (noon): 03:30–06:00 local. */
    const val NIGHT_WINDOW_START_MINUTE = 15 * 60 + 30
    const val NIGHT_WINDOW_END_MINUTE = 18 * 60

    private const val ANCHOR_HOUR = 4
    private const val ANCHOR_MINUTE_OF_HOUR = 30
    private const val NOON_HOUR = 12
    private const val DAYS_PER_WEEK = 7
    private const val MERGE_GAP_MS = 60_000L
}
