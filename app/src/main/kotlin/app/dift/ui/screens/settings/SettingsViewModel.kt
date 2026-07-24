package app.dift.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dift.data.datastore.SettingsRepository
import app.dift.domain.SafetyDenylist
import app.dift.system.packages.InstalledApp
import app.dift.system.packages.InstalledAppsProvider
import app.dift.system.permissions.PermissionsChecker
import app.dift.system.permissions.PermissionsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val permissionsChecker: PermissionsChecker,
    private val appsProvider: InstalledAppsProvider,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** The "Not bad" picker (docs/features/usage-overview.md): all apps + the selected set. */
    data class NotBadUiState(
        val apps: List<InstalledApp> = emptyList(),
        val notBad: Set<String> = emptySet(),
        val query: String = "",
    ) {
        val filtered: List<InstalledApp>
            get() = if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    val permissions: StateFlow<PermissionsState> = permissionsChecker.state

    private val apps = MutableStateFlow(emptyList<InstalledApp>())
    private val query = MutableStateFlow("")

    val notBadState: StateFlow<NotBadUiState> =
        combine(apps, settings.notBadApps, query) { appList, notBad, filter ->
            NotBadUiState(apps = appList, notBad = notBad, query = filter)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NotBadUiState())

    init {
        viewModelScope.launch {
            apps.value = appsProvider.launchableApps()
                .filter { it.packageName !in SafetyDenylist.staticPackages }
        }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun toggleNotBad(packageName: String) {
        viewModelScope.launch { settings.toggleNotBadApp(packageName) }
    }

    fun refresh() = permissionsChecker.refresh()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
