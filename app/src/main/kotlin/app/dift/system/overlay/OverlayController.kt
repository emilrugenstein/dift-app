package app.dift.system.overlay

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns exactly one blocking overlay window, wrapping [OverlayComposeHost]. Idempotent and
 * main-thread-safe: callers (the coordinator, off the main thread) can call [show]/[hide]
 * freely. Blocking UI never launches an Activity (ADR-0003).
 */
@Singleton
class OverlayController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var host: OverlayComposeHost? = null

    @Volatile
    var currentContent: (@Composable () -> Unit)? = null
        private set

    val isShowing: Boolean get() = currentContent != null

    fun show(content: @Composable () -> Unit) {
        currentContent = content
        runOnMain {
            val activeHost = host ?: OverlayComposeHost(context).also { host = it }
            if (activeHost.isShowing) activeHost.hide()
            activeHost.show(content)
        }
    }

    fun hide() {
        currentContent = null
        runOnMain { host?.hide() }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }
}
