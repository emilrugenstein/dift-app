package app.dift.ui.screens.ruleeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dift.data.repo.RuleRepository
import app.dift.domain.SafetyDenylist
import app.dift.domain.model.Rule
import app.dift.domain.model.RuleType
import app.dift.domain.model.Strictness
import app.dift.system.packages.InstalledApp
import app.dift.system.packages.InstalledAppsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RuleEditorViewModel @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val appsProvider: InstalledAppsProvider,
) : ViewModel() {

    data class UiState(
        val ruleId: Long = 0,
        val name: String = "",
        val type: RuleType = RuleType.ALWAYS,
        val strictness: Strictness = Strictness.FRICTION,
        val selectedPackages: Set<String> = emptySet(),
        val limitMinutes: Int = 30,
        val scheduleStartMinute: Int = DEFAULT_START,
        val scheduleEndMinute: Int = DEFAULT_END,
        val daysMask: Int = ALL_DAYS,
        val apps: List<InstalledApp> = emptyList(),
    ) {
        /** Per-app rules must target at least one app; USAGE_DEBT is device-wide and not edited here. */
        val isValid: Boolean
            get() = name.isNotBlank() &&
                selectedPackages.isNotEmpty() &&
                (type != RuleType.DAILY_LIMIT || limitMinutes > 0)
    }

    private val state = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = state.asStateFlow()

    fun load(ruleId: Long) {
        viewModelScope.launch {
            val apps = appsProvider.launchableApps().filter {
                it.packageName !in SafetyDenylist.staticPackages
            }
            val existing = if (ruleId != 0L) {
                ruleRepository.rules.first().firstOrNull { it.id == ruleId }
            } else {
                null
            }
            state.value = if (existing != null) {
                existing.toUiState(apps)
            } else {
                UiState(apps = apps)
            }
        }
    }

    fun setName(value: String) = state.update { it.copy(name = value) }

    fun setType(value: RuleType) = state.update { it.copy(type = value) }

    fun setStrictness(value: Strictness) = state.update { it.copy(strictness = value) }

    fun setLimitMinutes(value: Int) = state.update { it.copy(limitMinutes = value.coerceAtLeast(0)) }

    fun setScheduleStart(minute: Int) = state.update { it.copy(scheduleStartMinute = minute) }

    fun setScheduleEnd(minute: Int) = state.update { it.copy(scheduleEndMinute = minute) }

    fun toggleDay(dayIndex: Int) = state.update {
        it.copy(daysMask = it.daysMask xor (1 shl dayIndex))
    }

    fun togglePackage(packageName: String) = state.update {
        val next = if (packageName in it.selectedPackages) {
            it.selectedPackages - packageName
        } else {
            it.selectedPackages + packageName
        }
        it.copy(selectedPackages = next)
    }

    fun save(onSaved: () -> Unit) {
        val current = state.value
        if (!current.isValid) return
        viewModelScope.launch {
            ruleRepository.upsert(current.toRule(), System.currentTimeMillis())
            onSaved()
        }
    }

    private fun UiState.toRule() = Rule(
        id = ruleId,
        name = name.trim(),
        type = type,
        enabled = true,
        strictness = strictness,
        packages = selectedPackages,
        deviceWide = false,
        limitMinutes = limitMinutes.takeIf { type == RuleType.DAILY_LIMIT },
        scheduleStartMinuteOfDay = scheduleStartMinute.takeIf { type == RuleType.SCHEDULE },
        scheduleEndMinuteOfDay = scheduleEndMinute.takeIf { type == RuleType.SCHEDULE },
        scheduleDaysMask = daysMask.takeIf { type == RuleType.SCHEDULE },
    )

    private fun Rule.toUiState(apps: List<InstalledApp>) = UiState(
        ruleId = id,
        name = name,
        type = type,
        strictness = strictness,
        selectedPackages = packages,
        limitMinutes = limitMinutes ?: 30,
        scheduleStartMinute = scheduleStartMinuteOfDay ?: DEFAULT_START,
        scheduleEndMinute = scheduleEndMinuteOfDay ?: DEFAULT_END,
        daysMask = scheduleDaysMask ?: ALL_DAYS,
        apps = apps,
    )

    private companion object {
        const val DEFAULT_START = 9 * 60
        const val DEFAULT_END = 17 * 60
        const val ALL_DAYS = 0b1111111
    }
}
