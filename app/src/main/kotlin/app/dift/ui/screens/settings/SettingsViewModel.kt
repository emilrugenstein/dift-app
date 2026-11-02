package app.dift.ui.screens.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dift.data.datastore.SettingsRepository
import app.dift.data.db.DiftDatabase
import app.dift.data.export.DataExporter
import app.dift.domain.SafetyDenylist
import app.dift.system.packages.InstalledApp
import app.dift.system.packages.InstalledAppsProvider
import app.dift.system.permissions.PermissionsChecker
import app.dift.system.permissions.PermissionsState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionsChecker: PermissionsChecker,
    private val appsProvider: InstalledAppsProvider,
    private val settings: SettingsRepository,
    private val exporter: DataExporter,
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

    enum class ExportState { IDLE, RUNNING, DONE, FAILED }

    val permissions: StateFlow<PermissionsState> = permissionsChecker.state

    private val apps = MutableStateFlow(emptyList<InstalledApp>())
    private val query = MutableStateFlow("")

    val notBadState: StateFlow<NotBadUiState> =
        combine(apps, settings.notBadApps, query) { appList, notBad, filter ->
            NotBadUiState(apps = appList, notBad = notBad, query = filter)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NotBadUiState())

    private val export = MutableStateFlow(ExportState.IDLE)
    val exportState: StateFlow<ExportState> = export.asStateFlow()

    /** Size of the on-device database (main file + write-ahead log), in megabytes. */
    private val storage = MutableStateFlow(0.0)
    val storageMegabytes: StateFlow<Double> = storage.asStateFlow()

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

    fun refresh() {
        permissionsChecker.refresh()
        viewModelScope.launch { storage.value = withContext(Dispatchers.IO) { databaseMegabytes() } }
    }

    /** Writes the full export to [uri] (a document the user just created via the system picker). */
    fun export(uri: Uri, appVersion: String) {
        viewModelScope.launch {
            export.value = ExportState.RUNNING
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = context.contentResolver.openOutputStream(uri) ?: error("no output stream for $uri")
                    exporter.writeZip(stream, System.currentTimeMillis(), appVersion)
                }
            }
            result.onFailure { Log.w(TAG, "export failed", it) }
            export.value = if (result.isSuccess) ExportState.DONE else ExportState.FAILED
        }
    }

    private fun databaseMegabytes(): Double {
        val main = context.getDatabasePath(DiftDatabase.NAME)
        val bytes = listOf(main, File(main.path + "-wal"), File(main.path + "-shm"))
            .filter { it.exists() }
            .sumOf { it.length() }
        return bytes / BYTES_PER_MEGABYTE
    }

    private companion object {
        const val TAG = "SettingsViewModel"
        const val STOP_TIMEOUT_MS = 5_000L
        const val BYTES_PER_MEGABYTE = 1024.0 * 1024.0
    }
}
