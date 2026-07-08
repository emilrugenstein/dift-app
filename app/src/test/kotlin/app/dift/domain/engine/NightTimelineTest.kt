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

    private fun ms(iso: String): Long =
        LocalDateTime.parse(iso).atZone(zone).toInstant().toEpochMilli()

    private fun interval(fromIso: String, toIso: String) = UsageInterval(ms(fromIso), ms(toIso))

    /** Column 0 = the night whose morning is [monday]: Sun 2026-07-05 12:00 → Mon 2026-07-06 12:00. */
    private fun firstNight(intervals: List<UsageInterval>) =
        NightTimeline.buildWeek(intervals, monday, zone).first()

    @Test
    fun `builds seven columns labelled Monday through Sunday`() {
        val week = NightTimeline.buildWeek(emptyList(), monday, zone)
        assertEquals(7, week.size)
        assertEquals(monday, week.first().morningDate)
        assertEquals(monday.plusDays(6), week.last().morningDate)
    }

    @Test
    fun `midnight sits at the centre of the column`() {
        // A one-minute blip exactly at midnight maps to minute 720.
        val night = firstNight(listOf(interval("2026-07-06T00:00:00", "2026-07-06T00:01:00")))
        assertEquals(720, night.usage.single().startMinute)
    }

    @Test
    fun `sleep is the gap straddling midnight, not the daytime idle gap`() {
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
    fun `an unused night is a full-height sleep span`() {
        val sleep = requireNotNull(firstNight(emptyList()).sleep)
        assertEquals(0, sleep.startMinute)
        assertEquals(NightTimeline.MINUTES_PER_DAY, sleep.endMinute)
    }

    @Test
    fun `phone used across midnight yields no clear sleep`() {
        val night = firstNight(listOf(interval("2026-07-05T23:50:00", "2026-07-06T00:10:00")))
        assertNull(night.sleep)
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
