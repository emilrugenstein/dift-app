package app.dift.ui.screens.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 */
@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val usageRepository: UsageRepository,
    private val ingester: UsageStatsIngester,
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
        val intervals = usageRepository.sessionsInRange(fromMs, toMs)
            .map { UsageInterval(it.startEpochMs, it.endEpochMs) }
        return NightTimeline.buildWeek(intervals, weekMonday, zone)
    }

    private suspend fun buildTotals(): List<DayBar> {
        val totalsByDay = usageRepository.observeDayTotals(weekMonday.toString()).first()
            .associate { it.dayLocal to it.totalMs }
        return (0 until DAYS).map { offset ->
            val date = weekMonday.plusDays(offset.toLong())
            DayBar(date, totalsByDay[date.toString()] ?: 0L)
        }
    }

    private fun isoMonday(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

    private companion object {
        const val NOON = 12
        const val DAYS = 7
    }
}
