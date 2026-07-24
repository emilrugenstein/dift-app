package app.dift.system.packages

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the packages that are only knowable at runtime but must never be blocked
 * (SafetyDenylist's dynamic half): every installed launcher, the default dialer, and all
 * enabled keyboards. Without the launcher here, a device-wide rule (night usage-debt) would
 * block the home screen itself and trap the user inside the overlay.
 */
@Singleton
class RuntimeDenylistProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var cached: Set<String> = emptySet()

    @Volatile
    private var cachedEscapes: Set<String> = emptySet()

    fun current(): Set<String> {
        if (cached.isEmpty()) refresh()
        return cached
    }

    /**
     * The runtime denylist WITHOUT launchers: the packages that still escape the whole-screen
     * cooldown lockout (docs/features/usage-debt.md §5). The home screen deliberately gets no
     * pass there — it would allow browsing recents previews mid-cooldown.
     */
    fun currentEscapes(): Set<String> {
        if (cached.isEmpty()) refresh()
        return cachedEscapes
    }

    fun refresh() {
        val pm = context.packageManager
        val escapes = mutableSetOf<String>()

        context.getSystemService(TelecomManager::class.java)
            ?.defaultDialerPackage?.let(escapes::add)

        context.getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList?.forEach { escapes.add(it.packageName) }

        val launchers = mutableSetOf<String>()
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        pm.queryIntentActivities(homeIntent, PackageManager.ResolveInfoFlags.of(0L))
            .forEach { launchers.add(it.activityInfo.packageName) }

        cachedEscapes = escapes
        cached = escapes + launchers
    }
}
