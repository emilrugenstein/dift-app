package app.dift.system.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import app.dift.system.blocking.BlockingCoordinator
import app.dift.system.detect.ForegroundAppTracker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Primary foreground-app detector (ADR-0002). Deliberately thin: it forwards window-state
 * events to the tracker and starts the coordinator. No business logic here (agent-safety:
 * services stay dumb). Config keeps canRetrieveWindowContent="false" (CLAUDE.md invariant #8).
 *
 * Usage-debt blocks enforce purely through the overlay (no home-kick): pressing home is always
 * allowed because the launcher is on the SafetyDenylist, so the corner indicator shows the
 * remaining cooldown while any app launch re-raises the full-screen lockout.
 */
@AndroidEntryPoint
class DiftAccessibilityService : AccessibilityService() {

    @Inject lateinit var tracker: ForegroundAppTracker

    @Inject lateinit var coordinator: BlockingCoordinator

    override fun onServiceConnected() {
        super.onServiceConnected()
        coordinator.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        tracker.onWindowStateChanged(
            packageName = event.packageName?.toString(),
            className = event.className?.toString(),
        )
    }

    override fun onInterrupt() = Unit
}
