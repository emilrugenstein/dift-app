package app.dift.ui.screens.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dift.data.repo.RuleRepository
import app.dift.data.repo.UsageRepository
import app.dift.domain.model.RuleType
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
    private val ruleRepository: RuleRepository,
    private val appsProvider: InstalledAppsProvider,
) : ViewModel() {

    data class AppRow(
        val packageName: String,
        val label: String,
        val todayMs: Long,
        val blockedAlways: Boolean,
    )

    private val today = MutableStateFlow(LocalDate.now().toString())
    private val apps = MutableStateFlow<List<InstalledApp>>(emptyList())

    val state: StateFlow<List<AppRow>> = combine(
        today.flatMapLatest { usageRepository.observeDay(it) },
        apps,
        ruleRepository.rules,
    ) { day, installed, rules ->
        val usage = day.associate { it.packageName to it.totalMs }
        val alwaysBlocked = rules
            .filter { it.type == RuleType.ALWAYS }
            .flatMap { it.packages }
            .toSet()
        installed.map {
            AppRow(
                packageName = it.packageName,
                label = it.label,
                todayMs = usage[it.packageName] ?: 0L,
                blockedAlways = it.packageName in alwaysBlocked,
            )
        }.sortedByDescending { it.todayMs }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    init {
        viewModelScope.launch { apps.value = appsProvider.launchableApps() }
    }

    fun refresh() {
        today.value = LocalDate.now().toString()
    }

    fun toggleBlock(row: AppRow) {
        viewModelScope.launch {
            ruleRepository.quickBlockToggle(row.packageName, row.label, System.currentTimeMillis())
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
