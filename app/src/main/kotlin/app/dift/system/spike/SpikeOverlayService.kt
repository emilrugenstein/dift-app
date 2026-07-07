package app.dift.system.spike

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import app.dift.R
import app.dift.system.overlay.OverlayComposeHost

/**
 * M0 THROWAWAY SPIKE — de-risks the single scariest unknown before any feature work:
 * can a specialUse foreground service draw a full-screen Compose overlay with a working
 * soft keyboard on the target device (Fairphone 6, Android 15/16)?
 *
 * Pass criteria (docs/SMOKE_TEST.md item 1): overlay covers the whole screen, the text
 * field accepts focus, the keyboard opens, Close removes the overlay and stops the service.
 *
 * Deleted in M2 when the real blocking pipeline replaces it. Do not grow features here.
 */
class SpikeOverlayService : Service() {

    private var overlayHost: OverlayComposeHost? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        val host = overlayHost ?: OverlayComposeHost(this).also { overlayHost = it }
        host.show {
            SpikeOverlayContent(onClose = { stopSelf() })
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        overlayHost?.hide()
        overlayHost = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_monitor),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.spike_notification_title))
            .setContentText(getString(R.string.spike_notification_body))
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "monitor"
        const val NOTIFICATION_ID = 1
    }
}
