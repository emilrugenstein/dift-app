package app.dift.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class UsageAveragesTest {

    private val monday = LocalDate.parse("2026-07-06")
    private val hour = 3_600_000L

    /** Uniform totals over [from, to] so expected averages are trivial to read. */
    private fun days(from: LocalDate, to: LocalDate, totalMs: Long): Map<LocalDate, Long> =
        generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .associateWith { totalMs }

    @Test
    fun `a fully elapsed week averages over seven days`() {
        val totals = days(monday, monday.plusDays(6), 2 * hour) + mapOf(monday to 9 * hour)
        val result = UsageAverages.forWeek(totals, monday, today = monday.plusDays(20), earliestDay = monday)
        // Six days of 2h + one day of 9h = 21h / 7.
        assertEquals(3 * hour, requireNotNull(result).avgMsPerDay)
    }

    @Test
    fun `the current week only counts days up to today`() {
        val totals = days(monday, monday.plusDays(2), 3 * hour)
        val result = UsageAverages.forWeek(totals, monday, today = monday.plusDays(2), earliestDay = monday)
        // Mon–Wed only: 9h / 3, not 9h / 7.
        assertEquals(3 * hour, requireNotNull(result).avgMsPerDay)
    }

    @Test
    fun `missing days inside a covered range count as zero`() {
        val totals = mapOf(monday to 4 * hour) // Tue–Sun recorded nothing
        val result = UsageAverages.forWeek(totals, monday, today = monday.plusDays(20), earliestDay = monday)
        assertEquals(4 * hour / 7, requireNotNull(result).avgMsPerDay)
    }

    @Test
    fun `percent changes compare against last week and the previous 30 days`() {
        val earliest = monday.minusDays(UsageAverages.MONTH_DAYS.toLong())
        val totals = days(earliest, monday.minusDays(8), 2 * hour) + // older month days: 2h
            days(monday.minusDays(7), monday.minusDays(1), 4 * hour) + // last week: 4h
            days(monday, monday.plusDays(6), 3 * hour) // displayed week: 3h
        val result = requireNotNull(
            UsageAverages.forWeek(totals, monday, today = monday.plusDays(20), earliestDay = earliest),
        )
        assertEquals(3 * hour, result.avgMsPerDay)
        // 3h vs 4h = −25%. The 30-day reference mixes 23 days of 2h + 7 days of 4h = 2.4667h,
        // and 3h against that rounds to +22%.
        assertEquals(-25, result.vsPrevWeekPct)
        assertEquals(22, requireNotNull(result.vsPrevMonthPct))
    }

    @Test
    fun `reference ranges shrink to the days that can have data`() {
        // Installed Thursday of the previous week: the reference is 4 days, not 7.
        val earliest = monday.minusDays(4)
        val totals = days(earliest, monday.minusDays(1), 4 * hour) + days(monday, monday.plusDays(6), 2 * hour)
        val result = requireNotNull(
            UsageAverages.forWeek(totals, monday, today = monday.plusDays(20), earliestDay = earliest),
        )
        assertEquals(-50, result.vsPrevWeekPct)
    }

    @Test
    fun `no reference data means no percent comparison`() {
        val totals = days(monday, monday.plusDays(6), 2 * hour)
        val result = requireNotNull(
            UsageAverages.forWeek(totals, monday, today = monday.plusDays(20), earliestDay = monday),
        )
        assertNull(result.vsPrevWeekPct)
        assertNull(result.vsPrevMonthPct)
    }

    @Test
    fun `a week entirely before the first recorded data has no average`() {
        val result = UsageAverages.forWeek(
            emptyMap(),
            monday,
            today = monday.plusDays(20),
            earliestDay = monday.plusDays(10),
        )
        assertNull(result)
    }

    @Test
    fun `a week that has not started yet has no average`() {
        assertNull(UsageAverages.forWeek(emptyMap(), monday, today = monday.minusDays(1), earliestDay = null))
    }
}
