package app.dift.system.detect

import android.content.Context
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodManager
import app.dift.domain.SafetyDenylist
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single funnel for foreground-package signals from both detection sources.
 *
 * Window-state events fire for a lot of non-app windows; this filters them (ADR-0002):
 * - SystemUI, the current IME, and Dift's own overlay are ignored (the last one would
 *   otherwise re-enter the blocking pipeline in a loop when we show the overlay);
 * - only classes that resolve to a real Activity are accepted (cached PackageManager lookups).
 */
@Singleton
class ForegroundAppTracker @Inject constructor(
    @ApplicationContext private val context: Context,
) : ForegroundAppDetector {

    private val mutableForeground = MutableStateFlow<String?>(null)
    override val foregroundPackage: StateFlow<String?> = mutableForeground.asStateFlow()

    private val activityClassCache = HashMap<String, Boolean>()

    /** Feed a window-state change. [className] may be null (polling source has no class). */
    fun onWindowStateChanged(packageName: String?, className: String?) {
        if (packageName == null) return
        if (shouldIgnore(packageName)) return
        if (className != null && !isActivity(packageName, className)) return
        mutableForeground.value = packageName
    }

    private fun shouldIgnore(packageName: String): Boolean {
        if (packageName == SafetyDenylist.DIFT_PACKAGE) return true
        if (packageName == SYSTEM_UI) return true
        val ime = currentImePackage()
        return ime != null && packageName == ime
    }

    private fun isActivity(packageName: String, className: String): Boolean {
        val key = "$packageName/$className"
        activityClassCache[key]?.let { return it }
        val resolved = try {
            val component = android.content.ComponentName(packageName, className)
            context.packageManager.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0L))
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        activityClassCache[key] = resolved
        return resolved
    }

    private fun currentImePackage(): String? {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return null
        return imm.enabledInputMethodList.firstOrNull()?.packageName
    }

    private companion object {
        const val SYSTEM_UI = "com.android.systemui"
    }
}
