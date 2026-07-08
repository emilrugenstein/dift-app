package app.dift.system.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Hosts a Compose UI inside a raw [WindowManager] window. This is the ONLY sanctioned way to put
 * blocking UI on screen: blocking must never launch an Activity (ADR-0003,
 * docs/ANDROID_CONSTRAINTS.md). ComposeView outside an Activity needs hand-wired ViewTree owners —
 * that plumbing lives here and nowhere else.
 *
 * The window's [params] are supplied by the caller so the same host serves both the full-screen
 * block overlay and the small top-right indicator (see the companion factories).
 */
class OverlayComposeHost(
    private val context: Context,
    private val params: WindowManager.LayoutParams,
) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var owner: OverlayViewTreeOwner? = null

    val isShowing: Boolean get() = view != null

    /** Idempotent: calling show while a window is up is a no-op. Must run on the main thread. */
    fun show(content: @Composable () -> Unit) {
        if (view != null) return

        val treeOwner = OverlayViewTreeOwner().also { owner = it }
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(treeOwner)
            setViewTreeViewModelStoreOwner(treeOwner)
            setViewTreeSavedStateRegistryOwner(treeOwner)
            setContent(content)
        }

        windowManager.addView(composeView, params)
        treeOwner.moveToResumed()
        view = composeView
    }

    /** Idempotent: calling hide with no window is a no-op. Must run on the main thread. */
    fun hide() {
        val current = view ?: return
        view = null
        owner?.moveToDestroyed()
        owner = null
        windowManager.removeViewImmediate(current)
    }

    companion object {
        /** Full-screen, edge-to-edge, touch-consuming, non-focusable (no unblock keyboard). */
        fun blockParams() = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // True edge-to-edge: draw behind the system bars and into the cutout. Content stays
            // readable via safeDrawingPadding() inside the composable.
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        /** Small, top-right, click-through status pip (never steals touch or focus). */
        fun indicatorParams() = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = INDICATOR_MARGIN_PX
            y = INDICATOR_TOP_PX
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        private const val INDICATOR_MARGIN_PX = 24
        private const val INDICATOR_TOP_PX = 96
    }
}

/**
 * Minimal ViewTree owner trio for a window that lives outside any Activity.
 * Compose's window recomposer is driven by the lifecycle set on the view tree.
 */
private class OverlayViewTreeOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    init {
        savedStateRegistryController.performRestore(null)
    }

    fun moveToResumed() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun moveToDestroyed() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}
