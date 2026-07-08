package app.dift.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dift.data.datastore.SettingsRepository
import app.dift.system.monitor.MonitorManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Gates the app between onboarding and the main scaffold, and keeps monitoring in sync. */
@HiltViewModel
class RootViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val monitorManager: MonitorManager,
) : ViewModel() {

    /** null while the preference is still loading (renders nothing for a frame or two). */
    val onboardingCompleted: StateFlow<Boolean?> = settings.onboardingCompleted
        .map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun completeOnboarding() {
        viewModelScope.launch { settings.setOnboardingCompleted(true) }
    }

    /** Foreground-safe entry point to (re)start the correct detection source. */
    fun syncMonitoring() {
        viewModelScope.launch { monitorManager.sync() }
    }
}
