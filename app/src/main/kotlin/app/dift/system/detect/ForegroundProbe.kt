package app.dift.system.detect

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ground-truth re-query of the current foreground app straight from UsageStats — the safety net
 * for a hung blocking pipeline (docs/features/usage-debt.md, "Hang safety net"). The accessibility service can
 * silently miss a window event; replaying the latest ACTIVITY_RESUMED into the tracker heals the
 * `using` signal within one resync period. Called only while a block window or cooldown is live
 * and the screen is interactive, so the battery cost is negligible.
 */
@Singleton
class ForegroundProbe @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tracker: ForegroundAppTracker,
) {
    fun resync() {
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val events = manager.queryEvents(now - LOOKBACK_MS, now) ?: return
        val event = UsageEvents.Event()
        var latestPackage: String? = null
        var latestClass: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                latestPackage = event.packageName
                latestClass = event.className
            }
        }
        if (latestPackage != null) tracker.onWindowStateChanged(latestPackage, latestClass)
    }

    private companion object {
        /** Long enough to always contain the last app switch that matters to a live burst. */
        const val LOOKBACK_MS = 6 * 60_000L
    }
}
