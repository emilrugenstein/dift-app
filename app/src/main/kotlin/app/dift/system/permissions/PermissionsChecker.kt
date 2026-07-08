package app.dift.system.permissions

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PermissionsState(
    val usageAccess: Boolean = false,
    val notifications: Boolean = false,
    val overlay: Boolean = false,
    val accessibility: Boolean = false,
    val batteryExempt: Boolean = false,
)

/**
 * Single source of truth for the special-permission states (docs/features/onboarding.md).
 * There are no callbacks for most of these — call [refresh] on every screen resume.
 */
@Singleton
class PermissionsChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutableState = MutableStateFlow(compute())
    val state: StateFlow<PermissionsState> = mutableState

    fun refresh() {
        mutableState.value = compute()
    }

    private fun compute(): PermissionsState {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val usageAccess = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED

        val notifications = context.getSystemService(NotificationManager::class.java)
            .areNotificationsEnabled()

        val overlay = Settings.canDrawOverlays(context)

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val accessibility = enabledServices.split(':')
            .any { it.startsWith("${context.packageName}/") }

        val batteryExempt = context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

        return PermissionsState(usageAccess, notifications, overlay, accessibility, batteryExempt)
    }
}
