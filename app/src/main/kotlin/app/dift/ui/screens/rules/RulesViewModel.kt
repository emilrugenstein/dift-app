package app.dift.ui.screens.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dift.data.repo.RuleRepository
import app.dift.domain.model.Rule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val ruleRepository: RuleRepository,
) : ViewModel() {

    val rules: StateFlow<List<Rule>> = ruleRepository.rules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    fun setEnabled(ruleId: Long, enabled: Boolean) {
        viewModelScope.launch {
            ruleRepository.setEnabled(ruleId, enabled, System.currentTimeMillis())
        }
    }

    fun delete(ruleId: Long) {
        viewModelScope.launch { ruleRepository.delete(ruleId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
