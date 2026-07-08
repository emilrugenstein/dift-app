package app.dift.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dift.data.repo.UsageRepository
import app.dift.system.ingest.UsageStatsIngester
import app.dift.system.packages.InstalledAppsProvider
import app.dift.system.permissions.PermissionsChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val usageRepository: UsageRepository,
    private val ingester: UsageStatsIngester,
    private val appsProvider: InstalledAppsProvider,
    private val permissionsChecker: PermissionsChecker,
) : ViewModel() {

    data class AppUsage(
        val packageName: String,
        val label: String,
        val totalMs: Long,
        val fractionOfTop: Float,
    )

    data class DayBar(val dayLocal: String, val totalMs: Long)

    data class UiState(
        val usageAccessGranted: Boolean = true,
        val todayTotalMs: Long = 0,
        val topApps: List<AppUsage> = emptyList(),
        val week: List<DayBar> = emptyList(),
    )

    private val today = MutableStateFlow(LocalDate.now().toString())
    private val labels = MutableStateFlow<Map<String, String>>(emptyMap())

    val state: StateFlow<UiState> = combine(
        today.flatMapLatest { usageRepository.observeDay(it) },
        today.flatMapLatest { day ->
            usageRepository.observeDayTotals(LocalDate.parse(day).minusDays(WEEK_SPAN).toString())
        },
        labels,
        permissionsChecker.state,
    ) { day, dayTotals, labelMap, permissions ->
        val topTotal = day.firstOrNull()?.totalMs ?: 0L
        val totalsByDay = dayTotals.associate { it.dayLocal to it.totalMs }
        val todayDate = LocalDate.parse(today.value)
        UiState(
            usageAccessGranted = permissions.usageAccess,
            todayTotalMs = day.sumOf { it.totalMs },
            topApps = day.take(TOP_APPS).map {
                AppUsage(
                    packageName = it.packageName,
                    label = labelMap[it.packageName] ?: it.packageName,
                    totalMs = it.totalMs,
                    fractionOfTop = if (topTotal > 0) it.totalMs.toFloat() / topTotal else 0f,
                )
            },
            week = (WEEK_SPAN downTo 0).map { offset ->
                val date = todayDate.minusDays(offset).toString()
                DayBar(date, totalsByDay[date] ?: 0L)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState())

    init {
        viewModelScope.launch {
            labels.value = appsProvider.launchableApps().associate { it.packageName to it.label }
        }
    }

    fun refresh() {
        today.value = LocalDate.now().toString()
        permissionsChecker.refresh()
        viewModelScope.launch { ingester.ingestNow() }
    }

    private companion object {
        const val TOP_APPS = 8
        const val WEEK_SPAN = 6L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
