package app.dift.system.monitor

import android.content.Context
import android.content.Intent
import app.dift.data.datastore.SettingsRepository
import app.dift.system.permissions.PermissionsChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides which detection source runs. Accessibility is preferred and needs no service start
 * (the system binds it). The fallback FGS runs only when accessibility is off (or the debug
 * force-polling toggle is set). Called at app start and after boot.
 */
@Singleton
class MonitorManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionsChecker: PermissionsChecker,
    private val settings: SettingsRepository,
) {
    suspend fun sync() {
        permissionsChecker.refresh()
        val accessibilityOn = permissionsChecker.state.value.accessibility
        val forcePolling = settings.forcePollingMode.first()
        if (!accessibilityOn || forcePolling) {
            startFallback()
        } else {
            stopFallback()
        }
    }

    fun startFallback() {
        context.startForegroundService(Intent(context, FallbackMonitorService::class.java))
    }

    fun stopFallback() {
        context.stopService(Intent(context, FallbackMonitorService::class.java))
    }
}
