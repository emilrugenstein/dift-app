package app.dift.system.packages

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledApp(val packageName: String, val label: String)

/** Launchable apps with labels; requires QUERY_ALL_PACKAGES. Cached per process. */
@Singleton
class InstalledAppsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private var cache: List<InstalledApp>? = null

    suspend fun launchableApps(forceRefresh: Boolean = false): List<InstalledApp> = mutex.withLock {
        cache?.takeIf { !forceRefresh }?.let { return it }
        val apps = withContext(Dispatchers.IO) { query() }
        cache = apps
        apps
    }

    suspend fun labelFor(packageName: String): String =
        launchableApps().firstOrNull { it.packageName == packageName }?.label ?: packageName

    private fun query(): List<InstalledApp> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
