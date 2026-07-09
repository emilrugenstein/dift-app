package app.dift.system.overlay

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the small top-right status indicator window (docs/features/usage-debt.md). Separate from
 * the full-screen [OverlayController] because it is click-through and stays up while a block
 * window is active or a cooldown is draining. Its content is set once and re-renders itself from
 * the coordinator's [app.dift.system.overlay.IndicatorState] flow.
 */
@Singleton
class IndicatorOverlayController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var host: OverlayComposeHost? = null

    @Volatile
    var isShowing: Boolean = false
        private set

    fun show(content: @Composable () -> Unit) {
        if (isShowing) return
        isShowing = true
        runOnMain {
            val activeHost = host
                ?: OverlayComposeHost(context, OverlayComposeHost.indicatorParams()).also { host = it }
            activeHost.show(content)
        }
    }

    fun hide() {
        if (!isShowing) return
        isShowing = false
        runOnMain { host?.hide() }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }
}
