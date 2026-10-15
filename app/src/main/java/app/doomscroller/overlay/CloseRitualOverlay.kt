package app.doomscroller.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Close ritual shown directly from the accessibility-service process.
 *
 * BreakOverlayService/BreakActivity are blocked on modern Android by
 * background-start restrictions (FGS start denied on 12+, BAL denied
 * on 10+), so the ritual is added as an overlay view instead — the
 * same mechanism the fire wall already uses reliably. Needs overlay
 * permission; returns false when it cannot draw.
 */
class CloseRitualOverlay(private val context: Context) {

    private var view: ComposeView? = null
    private var viewLifecycle: OverlayLifecycleOwner? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Show the fullscreen dither ritual. Main-thread only (the service
     * scope runs on Main); returns false when there is no overlay.
     */
    fun show(onCloseApp: () -> Unit, onDone: () -> Unit): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper()) { "CloseRitualOverlay.show must run on Main" }
        if (view != null) return true
        if (!Settings.canDrawOverlays(context)) return false
        val wm = context.getSystemService(WindowManager::class.java) ?: return false
        val compose = ComposeView(context).apply {
            val owner = OverlayLifecycleOwner()
            owner.attach()
            viewLifecycle = owner
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                DitherCloseScreen(
                    onCloseApp = onCloseApp,
                    onDone = {
                        hide()
                        onDone()
                    },
                )
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }
        return runCatching {
            wm.addView(compose, params)
            view = compose
            true
        }.getOrDefault(false)
    }

    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hide() }
            return
        }
        val v = view ?: return
        view = null
        runCatching {
            context.getSystemService(WindowManager::class.java)?.removeView(v)
        }
        viewLifecycle?.detach()
        viewLifecycle = null
    }

    fun destroy() = hide()
}
