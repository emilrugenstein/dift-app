package app.dift.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dift.data.repo.RuleRepository
import app.dift.domain.model.Rule
import app.dift.domain.model.RuleType
import app.dift.domain.model.Strictness
import app.dift.system.permissions.PermissionsChecker
import app.dift.system.permissions.PermissionsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val permissionsChecker: PermissionsChecker,
    private val ruleRepository: RuleRepository,
) : ViewModel() {

    val permissions: StateFlow<PermissionsState> = permissionsChecker.state

    /** Whether the night usage-debt rule exists and is enabled. */
    val nightModeEnabled: StateFlow<Boolean> = ruleRepository.rules
        .map { rules -> rules.any { it.type == RuleType.USAGE_DEBT && it.enabled } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    fun refresh() = permissionsChecker.refresh()

    /** Creates the default night rule on first enable, then just flips its enabled flag. */
    fun setNightMode(enabled: Boolean) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val current = ruleRepository.rules.first().firstOrNull { it.type == RuleType.USAGE_DEBT }
            if (current == null) {
                if (enabled) ruleRepository.upsert(defaultNightRule(), now)
            } else {
                ruleRepository.setEnabled(current.id, enabled, now)
            }
        }
    }

    private fun defaultNightRule() = Rule(
        id = 0,
        name = "Night mode",
        type = RuleType.USAGE_DEBT,
        enabled = true,
        strictness = Strictness.HARD,
        packages = emptySet(),
        deviceWide = true,
        scheduleStartMinuteOfDay = DEFAULT_START_MINUTE,
        scheduleEndMinuteOfDay = DEFAULT_END_MINUTE,
        maxBurstSeconds = Rule.DEFAULT_MAX_BURST_SECONDS,
        debtRatio = Rule.DEFAULT_DEBT_RATIO,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val DEFAULT_START_MINUTE = 22 * 60 + 30 // 22:30
        const val DEFAULT_END_MINUTE = 6 * 60 // 06:00
    }
}
