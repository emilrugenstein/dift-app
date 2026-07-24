package app.dift.domain.engine

import app.dift.domain.engine.NightTimeline.UsageInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class NightTimelineTest {

    private val zone = ZoneId.of("UTC")
    private val monday = LocalDate.parse("2026-07-06") // 2026-07-07 is a Tuesday

    /** "Now" well past the whole test week, so no column is in the future unless a test says so. */
    private val weekOver = ms("2026-07-20T00:00:00")

    private fun ms(iso: String): Long =
        LocalDateTime.parse(iso).atZone(zone).toInstant().toEpochMilli()

    private fun interval(fromIso: String, toIso: String, notBad: Boolean = false) =
        UsageInterval(ms(fromIso), ms(toIso), notBad)

    /** Column 0 = the night whose morning is [monday]: Sun 2026-07-05 12:00 → Mon 2026-07-06 12:00. */
    private fun firstNight(intervals: List<UsageInterval>) =
        NightTimeline.buildWeek(intervals, monday, zone, weekOver).first()

    @Test
    fun `builds seven columns labelled Monday through Sunday`() {
        val week = NightTimeline.buildWeek(emptyList(), monday, zone, weekOver)
        assertEquals(7, week.size)
        assertEquals(monday, week.first().morningDate)
        assertEquals(monday.plusDays(6), week.last().morningDate)
    }

    @Test
    fun `a night whose 4-30 anchor is still in the future has no sleep span`() {
        // It is Sunday evening 22:00; Monday 04:30 has not happened yet.
        val week = NightTimeline.buildWeek(emptyList(), monday, zone, ms("2026-07-05T22:00:00"))
        assertNull(week.first().sleep)
        // Once 04:30 has passed, the (unused) night shows its full-height span again.
        val later = NightTimeline.buildWeek(emptyList(), monday, zone, ms("2026-07-06T05:00:00"))
        assertEquals(NightTimeline.MINUTES_PER_DAY, requireNotNull(later.first().sleep).endMinute)
        // ...but Tuesday's night is still in the future.
        assertNull(later[1].sleep)
    }

    @Test
    fun `midnight sits at the centre of the column`() {
        // A one-minute blip exactly at midnight maps to minute 720.
        val night = firstNight(listOf(interval("2026-07-06T00:00:00", "2026-07-06T00:01:00")))
        assertEquals(720, night.usage.single().startMinute)
    }

    @Test
    fun `sleep is the gap intersecting the detection window, not the daytime idle gap`() {
        val night = firstNight(
            listOf(
                interval("2026-07-05T22:00:00", "2026-07-05T22:30:00"), // evening
                interval("2026-07-06T07:00:00", "2026-07-06T07:15:00"), // morning (alarm)
            ),
        )
        // Evening use ends 22:30 = minute 630; morning use starts 07:00 = minute 1140.
        val sleep = requireNotNull(night.sleep)
        assertEquals(630, sleep.startMinute)
        assertEquals(1140, sleep.endMinute)
    }

    @Test
    fun `use past midnight only delays the sleep start`() {
        val night = firstNight(
            listOf(
                interval("2026-07-06T00:30:00", "2026-07-06T01:00:00"), // late night scrolling
                interval("2026-07-06T08:00:00", "2026-07-06T08:10:00"), // morning
            ),
        )
        // Sleep runs from 01:00 (minute 780) to 08:00 (minute 1200) — anchored at 04:30.
        val sleep = requireNotNull(night.sleep)
        assertEquals(780, sleep.startMinute)
        assertEquals(1200, sleep.endMinute)
    }

    @Test
    fun `an unused night is a full-height sleep span`() {
        val sleep = requireNotNull(firstNight(emptyList()).sleep)
        assertEquals(0, sleep.startMinute)
        assertEquals(NightTimeline.MINUTES_PER_DAY, sleep.endMinute)
    }

    @Test
    fun `phone used across the whole detection window yields no clear sleep`() {
        // 03:00–06:30 covers all of 03:30–06:00: neither surrounding gap touches the window.
        val night = firstNight(listOf(interval("2026-07-06T03:00:00", "2026-07-06T06:30:00")))
        assertNull(night.sleep)
    }

    @Test
    fun `a brief 4am wake-up no longer ends the night`() {
        val night = firstNight(
            listOf(
                interval("2026-07-05T22:30:00", "2026-07-05T23:00:00"), // evening
                interval("2026-07-06T04:00:00", "2026-07-06T04:05:00"), // brief wake-up
                interval("2026-07-06T09:30:00", "2026-07-06T09:40:00"), // morning
            ),
        )
        // Both gaps intersect 03:30–06:00; the post-wake-up one (04:05–09:30) is longer.
        val sleep = requireNotNull(night.sleep)
        assertEquals(16 * 60 + 5, sleep.startMinute)
        assertEquals(21 * 60 + 30, sleep.endMinute)
    }

    @Test
    fun `the longest intersecting gap wins even when it starts before the window`() {
        val night = firstNight(
            listOf(
                interval("2026-07-05T23:00:00", "2026-07-05T23:10:00"), // evening
                interval("2026-07-06T05:30:00", "2026-07-06T05:35:00"), // early alarm snooze
                interval("2026-07-06T07:00:00", "2026-07-06T07:10:00"), // up for real
            ),
        )
        // 23:10–05:30 (380 min) beats 05:35–07:00 (85 min).
        val sleep = requireNotNull(night.sleep)
        assertEquals(11 * 60 + 10, sleep.startMinute)
        assertEquals(17 * 60 + 30, sleep.endMinute)
    }

    @Test
    fun `usage spans carry their category and categories never merge together`() {
        val night = firstNight(
            listOf(
                interval("2026-07-05T20:00:00", "2026-07-05T20:10:00", notBad = false),
                interval("2026-07-05T20:10:30", "2026-07-05T20:20:00", notBad = true), // 30s gap
            ),
        )
        // Same-category sessions would merge across 30 s; different categories must not.
        assertEquals(2, night.usage.size)
        assertEquals(setOf(false, true), night.usage.map { it.notBad }.toSet())
    }

    @Test
    fun `sleep is derived from the union of both categories`() {
        val night = firstNight(
            listOf(
                interval("2026-07-05T22:00:00", "2026-07-05T22:30:00", notBad = true),
                interval("2026-07-06T07:00:00", "2026-07-06T07:15:00", notBad = false),
            ),
        )
        val sleep = requireNotNull(night.sleep)
        assertEquals(630, sleep.startMinute)
        assertEquals(1140, sleep.endMinute)
    }

    @Test
    fun `adjacent sessions merge into one usage span`() {
        val night = firstNight(
            listOf(
                interval("2026-07-05T20:00:00", "2026-07-05T20:10:00"),
                interval("2026-07-05T20:10:30", "2026-07-05T20:20:00"), // 30s gap -> merged
            ),
        )
        assertEquals(1, night.usage.size)
    }

    @Test
    fun `intervals outside the column are dropped`() {
        val night = firstNight(listOf(interval("2026-07-04T14:00:00", "2026-07-04T15:00:00")))
        assertTrue(night.usage.isEmpty())
    }
}
