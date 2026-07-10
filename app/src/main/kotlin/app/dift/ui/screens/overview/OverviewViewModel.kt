package app.dift.ui.screens.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dift.data.datastore.SettingsRepository
import app.dift.data.repo.UsageRepository
import app.dift.domain.engine.NightTimeline
import app.dift.domain.engine.NightTimeline.NightColumn
import app.dift.domain.engine.NightTimeline.UsageInterval
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
 * Monday-start totals chart over a selectable ISO week, with a data-bounded pager.
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

    data class DayBar(val date: LocalDate, val totalMs: Long)

    data class UiState(
        val mode: Mode = Mode.NIGHT,
        val weekMonday: LocalDate,
        val canGoPrev: Boolean = false,
        val canGoNext: Boolean = false,
        val nights: List<NightColumn> = emptyList(),
        val totals: List<DayBar> = emptyList(),
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
        val totals = if (mode == Mode.TOTALS) buildTotals() else emptyList()
        return UiState(
            mode = mode,
            weekMonday = weekMonday,
            canGoPrev = earliestDate?.isBefore(weekMonday.minusDays(1)) == true,
            canGoNext = weekMonday.isBefore(isoMonday(LocalDate.now())),
            nights = nights,
            totals = totals,
        )
    }

    private suspend fun buildNights(): List<NightColumn> {
        val fromMs = weekMonday.minusDays(1).atTime(NOON, 0).atZone(zone).toInstant().toEpochMilli()
        val toMs = weekMonday.plusDays(DAYS - 1L).atTime(NOON, 0).atZone(zone).toInstant().toEpochMilli()
        val closed = usageRepository.sessionsInRange(fromMs, toMs)
            .map { UsageInterval(it.startEpochMs, it.endEpochMs) }
        // In-progress sessions drawn up to "now" (buildWeek clips them to the columns).
        val nowMs = System.currentTimeMillis()
        val live = settings.openSessions.first().map { UsageInterval(it.startMs, nowMs) }
        return NightTimeline.buildWeek(closed + live, weekMonday, zone)
    }

    private suspend fun buildTotals(): List<DayBar> {
        val totalsByDay = usageRepository.observeDayTotals(weekMonday.toString()).first()
            .associate { it.dayLocal to it.totalMs }
        val today = LocalDate.now()
        val liveTodayMs = liveElapsedMs(today)
        return (0 until DAYS).map { offset ->
            val date = weekMonday.plusDays(offset.toLong())
            val stored = totalsByDay[date.toString()] ?: 0L
            DayBar(date, stored + if (date == today) liveTodayMs else 0L)
        }
    }

    /** Foreground time of still-open sessions since today's midnight — the live component. */
    private suspend fun liveElapsedMs(today: LocalDate): Long {
        val startOfDayMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val nowMs = System.currentTimeMillis()
        return settings.openSessions.first()
            .sumOf { (nowMs - maxOf(it.startMs, startOfDayMs)).coerceAtLeast(0) }
    }

    private fun isoMonday(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

    private companion object {
        const val NOON = 12
        const val DAYS = 7
    }
}
