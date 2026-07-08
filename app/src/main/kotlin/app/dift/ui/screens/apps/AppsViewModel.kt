package app.dift.ui.screens.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dift.data.repo.UsageRepository
import app.dift.system.packages.InstalledApp
import app.dift.system.packages.InstalledAppsProvider
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
class AppsViewModel @Inject constructor(
    private val usageRepository: UsageRepository,
    private val appsProvider: InstalledAppsProvider,
) : ViewModel() {

    data class AppRow(val packageName: String, val label: String, val todayMs: Long)

    private val today = MutableStateFlow(LocalDate.now().toString())
    private val apps = MutableStateFlow<List<InstalledApp>>(emptyList())

    val state: StateFlow<List<AppRow>> = combine(
        today.flatMapLatest { usageRepository.observeDay(it) },
        apps,
    ) { day, installed ->
        val usage = day.associate { it.packageName to it.totalMs }
        installed.map { AppRow(it.packageName, it.label, usage[it.packageName] ?: 0L) }
            .sortedByDescending { it.todayMs }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    init {
        viewModelScope.launch { apps.value = appsProvider.launchableApps() }
    }

    fun refresh() {
        today.value = LocalDate.now().toString()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
