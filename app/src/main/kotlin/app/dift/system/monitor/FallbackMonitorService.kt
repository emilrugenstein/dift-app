package app.dift.system.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import app.dift.R
import app.dift.system.blocking.BlockingCoordinator
import app.dift.system.detect.ForegroundAppTracker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fallback detector when accessibility is disabled (ADR-0002). Polls queryEvents ~1 s while
 * the screen is interactive; stops when the screen goes off. specialUse FGS. No home-kick in
 * this mode — overlay-only blocking (ADR-0003 / ANDROID_CONSTRAINTS.md).
 */
@AndroidEntryPoint
class FallbackMonitorService : Service() {

    @Inject lateinit var tracker: ForegroundAppTracker

    @Inject lateinit var coordinator: BlockingCoordinator

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null
    private var lastEventTime = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> startPolling()
                Intent.ACTION_SCREEN_OFF -> stopPolling()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        coordinator.start()
        startPolling()
        return START_STICKY
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            val manager = getSystemService(UsageStatsManager::class.java)
            lastEventTime = System.currentTimeMillis() - INITIAL_WINDOW_MS
            while (isActive) {
                pollOnce(manager)
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun pollOnce(manager: UsageStatsManager) {
        val now = System.currentTimeMillis()
        val events = manager.queryEvents(lastEventTime, now) ?: return
        val event = UsageEvents.Event()
        var latestResumedPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                latestResumedPackage = event.packageName
            }
        }
        lastEventTime = now
        if (latestResumedPackage != null) {
            tracker.onWindowStateChanged(latestResumedPackage, className = null)
        }
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_monitor),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.monitor_notification_title))
            .setContentText(getString(R.string.monitor_notification_body))
            .build()
    }

    override fun onDestroy() {
        stopPolling()
        unregisterReceiver(screenReceiver)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val CHANNEL_ID = "monitor"
        const val NOTIFICATION_ID = 2
        const val POLL_INTERVAL_MS = 1_000L
        const val INITIAL_WINDOW_MS = 10_000L
    }
}
