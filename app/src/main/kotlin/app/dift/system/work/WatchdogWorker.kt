package app.dift.system.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.dift.R
import app.dift.data.repo.BlockRepository
import app.dift.system.monitor.MonitorManager
import app.dift.system.permissions.PermissionsChecker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Every 15 min: if any block is enabled but the accessibility service is off, make sure the
 * fallback is running and warn the user (blocking is degraded). ADR-0002.
 */
@HiltWorker
class WatchdogWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val permissionsChecker: PermissionsChecker,
    private val blockRepository: BlockRepository,
    private val monitorManager: MonitorManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        permissionsChecker.refresh()
        val hasEnabledBlock = blockRepository.blocks.first().any { it.enabled }
        val accessibilityOn = permissionsChecker.state.value.accessibility
        if (hasEnabledBlock && !accessibilityOn) {
            monitorManager.sync()
            notifyDegraded()
        }
        return Result.success()
    }

    private fun notifyDegraded() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_alerts),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.watchdog_title))
            .setContentText(context.getString(R.string.watchdog_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "alerts"
        const val NOTIFICATION_ID = 3
    }
}
