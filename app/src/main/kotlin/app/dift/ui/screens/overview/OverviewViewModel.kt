package app.dift.ui.screens.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dift.data.datastore.SettingsRepository
import app.dift.data.repo.UsageRepository
import app.dift.domain.engine.NightTimeline
import app.dift.domain.engine.NightTimeline.NightColumn
import app.dift.domain.engine.NightTimeline.UsageInterval
import app.dift.domain.engine.UsageAverages
import app.dift.system.ingest.UsageStatsIngester
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Drives the usage overview (docs/features/usage-overview.md): a night-aligned week chart or a
 * Monday-start totals chart over a selectable ISO week, with a data-bounded pager. Usage is split
 * into "Not bad" apps (blue) and everything else (violet) in both views.
 *
 * Closed sessions come from Room; sessions that are still open (checkpoint state) are added at
 * display time as `start → now` so "today" matches what Digital Wellbeing shows live. Nothing
 * is written back, so there is no double counting once the session closes.
 */
@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val usageRepository: UsageRepository,
    private val ingester: UsageStatsIngester,
    private val settings: SettingsRepository,
) : ViewModel() {

    enum class Mode { NIGHT, TOTALS }

    data class DayBar(val date: LocalDate, val notBadMs: Long, val otherMs: Long) {
        val totalMs: Long get() = notBadMs + otherMs
    }

    data class UiState(
        val mode: Mode = Mode.NIGHT,
        val weekMonday: LocalDate,
        val canGoPrev: Boolean = false,
        val canGoNext: Boolean = false,
        val nights: List<NightColumn> = emptyList(),
        val totals: List<DayBar> = emptyList(),
        val averages: UsageAverages.Result? = null,
    )

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private var weekMonday: LocalDate = isoMonday(LocalDate.now())
    private var mode: Mode = Mode.NIGHT
    private var earliestDate: LocalDate? = null

    private val state = MutableStateFlow(UiState(weekMonday = weekMonday))
    val uiState: StateFlow<UiState> = state.asStateFlow()

    init {
        refresh()
    }

    fun setMode(value: Mode) {
        mode = value
        reload()
    }

    fun prevWeek() {
        weekMonday = weekMonday.minusWeeks(1)
        reload()
    }

    fun nextWeek() {
        if (weekMonday.isBefore(isoMonday(LocalDate.now()))) {
            weekMonday = weekMonday.plusWeeks(1)
            reload()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            ingester.ingestNow()
            earliestDate = usageRepository.earliestSessionDay()?.let(LocalDate::parse)
            state.value = compute()
        }
    }

    private fun reload() {
        viewModelScope.launch { state.value = compute() }
    }

    private suspend fun compute(): UiState {
        val nights = if (mode == Mode.NIGHT) buildNights() else emptyList()
        val totals = if (mode == Mode.TOTALS) buildTotals() else null
        return UiState(
            mode = mode,
            weekMonday = weekMonday,
            canGoPrev = earliestDate?.isBefore(weekMonday.minusDays(1)) == true,
            canGoNext = weekMonday.isBefore(isoMonday(LocalDate.now())),
            nights = nights,
            totals = totals?.first ?: emptyList(),
            averages = totals?.second,
        )
    }

    private suspend fun buildNights(): List<NightColumn> {
        val notBad = settings.notBadApps.first()
        val fromMs = weekMonday.minusDays(1).atTime(NOON, 0).atZone(zone).toInstant().toEpochMilli()
        val toMs = weekMonday.plusDays(DAYS - 1L).atTime(NOON, 0).atZone(zone).toInstant().toEpochMilli()
        val closed = usageRepository.sessionsInRange(fromMs, toMs)
            .map { UsageInterval(it.startEpochMs, it.endEpochMs, it.packageName in notBad) }
        // In-progress sessions drawn up to "now" (buildWeek clips them to the columns).
        val nowMs = System.currentTimeMillis()
        val live = settings.openSessions.first().map { UsageInterval(it.startMs, nowMs, it.packageName in notBad) }
        return NightTimeline.buildWeek(closed + live, weekMonday, zone, nowMs)
    }

    /** The week's bars split by category, plus the per-day average with week/month comparisons. */
    private suspend fun buildTotals(): Pair<List<DayBar>, UsageAverages.Result?> {
        val notBad = settings.notBadApps.first()
        val today = LocalDate.now()
        val from = weekMonday.minusDays(UsageAverages.MONTH_DAYS.toLong())
        val to = weekMonday.plusDays(DAYS - 1L)
        val rows = usageRepository.dayAppTotalsInRange(from.toString(), to.toString())
        val (liveNotBadMs, liveOtherMs) = liveElapsedByCategory(today, notBad)

        val byDay = rows.groupBy { it.dayLocal }
        val bars = (0 until DAYS).map { offset ->
            val date = weekMonday.plusDays(offset.toLong())
            val dayRows = byDay[date.toString()].orEmpty()
            val notBadMs = dayRows.filter { it.packageName in notBad }.sumOf { it.totalMs } +
                if (date == today) liveNotBadMs else 0L
            val otherMs = dayRows.filterNot { it.packageName in notBad }.sumOf { it.totalMs } +
                if (date == today) liveOtherMs else 0L
            DayBar(date, notBadMs, otherMs)
        }

        val totalsByDay = byDay.entries.associate { (day, dayRows) ->
            LocalDate.parse(day) to dayRows.sumOf { it.totalMs }
        }.toMutableMap()
        totalsByDay[today] = (totalsByDay[today] ?: 0L) + liveNotBadMs + liveOtherMs
        val averages = UsageAverages.forWeek(totalsByDay, weekMonday, today, earliestDate)
        return bars to averages
    }

    /** Foreground time of still-open sessions since today's midnight, split by category. */
    private suspend fun liveElapsedByCategory(today: LocalDate, notBad: Set<String>): Pair<Long, Long> {
        val startOfDayMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val nowMs = System.currentTimeMillis()
        val open = settings.openSessions.first()
        fun elapsed(startMs: Long) = (nowMs - maxOf(startMs, startOfDayMs)).coerceAtLeast(0)
        val notBadMs = open.filter { it.packageName in notBad }.sumOf { elapsed(it.startMs) }
        val otherMs = open.filterNot { it.packageName in notBad }.sumOf { elapsed(it.startMs) }
        return notBadMs to otherMs
    }

    private fun isoMonday(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

    private companion object {
        const val NOON = 12
        const val DAYS = 7
    }
}
