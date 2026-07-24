package app.dift.system.detect

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether one of Dift's own activities is in the foreground. [ForegroundAppTracker] deliberately
 * ignores Dift's package (its overlay windows would loop back into the blocking pipeline), so the
 * whole-screen cooldown lockout needs this signal to keep Dift itself usable — the app is the
 * deliberate escape valve (docs/features/usage-debt.md §5).
 */
@Singleton
class OwnAppForegroundTracker @Inject constructor() : Application.ActivityLifecycleCallbacks {

    private val mutableForeground = MutableStateFlow(false)
    val foreground: StateFlow<Boolean> = mutableForeground.asStateFlow()

    private var startedActivities = 0

    fun register(application: Application) = application.registerActivityLifecycleCallbacks(this)

    override fun onActivityStarted(activity: Activity) {
        startedActivities++
        mutableForeground.value = true
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        mutableForeground.value = startedActivities > 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
