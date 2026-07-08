package app.dift.system.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.dift.system.monitor.MonitorManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Re-arms monitoring after reboot. The accessibility service is rebound by the system on its
 * own; this only matters for the fallback FGS. Starting a specialUse FGS from BOOT_COMPLETED
 * is legal on Android 15 (ANDROID_CONSTRAINTS.md).
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var monitorManager: MonitorManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                monitorManager.sync()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
