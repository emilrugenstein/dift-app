package app.dift.domain.engine

import app.dift.domain.model.Challenge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChallengeProgressTest {

    private val minute = 60_000L
    private val november = Challenge(
        id = 1,
        name = "Mobility",
        startDay = LocalDate.parse("2026-11-01"),
        endDay = LocalDate.parse("2026-11-30"),
        goalMinutesPerDay = 30,
    )

    private fun day(iso: String) = LocalDate.parse(iso)

    @Test
    fun `the goal is an average - a short day and a long day balance out`() {
        val totals = mapOf(day("2026-11-01") to 25 * minute, day("2026-11-02") to 35 * minute)
        val s = ChallengeProgress.snapshot(november, totals, today = day("2026-11-02"))
        assertEquals(2, s.elapsedDays)
        assertEquals(60 * minute, s.totalMs)
        assertEquals(60 * minute, s.targetSoFarMs)
        assertEquals(0L, s.balanceMs)
        assertTrue(s.onTrack)
        assertEquals(30 * minute, s.averagePerDayMs)
        assertEquals(35 * minute, s.todayMs)
    }

    @Test
    fun `falling behind shows a negative balance and a higher daily need`() {
        val totals = mapOf(day("2026-11-01") to 10 * minute)
        val s = ChallengeProgress.snapshot(november, totals, today = day("2026-11-02"))
        assertEquals(-50 * minute, s.balanceMs)
        assertFalse(s.onTrack)
        assertEquals(29, s.remainingDays) // Nov 2 … Nov 30, today included
        // (30 days × 30 min − 10 min) / 29 days = 890 / 29 ≈ 30.69 min
        assertEquals(890 * minute / 29, s.neededPerRemainingDayMs)
        assertEquals(30, s.totalDays)
        assertEquals(900 * minute, s.targetTotalMs)
    }

    @Test
    fun `days outside the period are ignored`() {
        val totals = mapOf(day("2026-10-31") to 60 * minute, day("2026-11-01") to 30 * minute)
        val s = ChallengeProgress.snapshot(november, totals, today = day("2026-11-01"))
        assertEquals(30 * minute, s.totalMs)
    }

    @Test
    fun `before the start nothing has elapsed and everything remains`() {
        val s = ChallengeProgress.snapshot(november, emptyMap(), today = day("2026-10-20"))
        assertEquals(0, s.elapsedDays)
        assertEquals(30, s.remainingDays)
        assertEquals(0L, s.averagePerDayMs)
        assertEquals(0L, s.todayMs)
        assertTrue(s.onTrack)
    }

    @Test
    fun `after the end the whole period counts and nothing remains`() {
        val totals = (1..30).associate { day("2026-11-%02d".format(it)) to 30 * minute }
        val s = ChallengeProgress.snapshot(november, totals, today = day("2026-12-05"))
        assertEquals(30, s.elapsedDays)
        assertEquals(0, s.remainingDays)
        assertEquals(0L, s.neededPerRemainingDayMs)
        assertEquals(1f, s.totalFraction)
    }

    @Test
    fun `a one-week challenge works the same way`() {
        val week = november.copy(startDay = day("2026-11-02"), endDay = day("2026-11-08"), goalMinutesPerDay = 20)
        val s = ChallengeProgress.snapshot(week, mapOf(day("2026-11-02") to 10 * minute), today = day("2026-11-02"))
        assertEquals(7, s.totalDays)
        assertEquals(0.5f, s.todayFraction)
        assertEquals((140 - 10) * minute / 7, s.neededPerRemainingDayMs)
    }
}
