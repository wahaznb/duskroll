package app.doomscroller.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.doomscroller.ui.DoomFire

/**
 * The fire that fights back: a top-anchored wall of live Doom fire
 * drawn OVER the scrolling app, starting below the status bar.
 * Starts at zero past the limit, burns down to 30% of the screen
 * as the ramp completes.
 * Visual interference only — never touchable, never focusable.
 */
class FireWallManager(private val context: Context) {

    private var view: ComposeView? = null
    private var viewLifecycle: OverlayLifecycleOwner? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var progress by mutableFloatStateOf(0f)
    private var fxLevel by mutableStateOf(2)
    private var fireColor by mutableStateOf(app.doomscroller.ui.DEFAULT_FIRE)

    fun show(prog: Float, fx: Int, fireColor: Int) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(prog, fx, fireColor) }
            return
        }
        progress = prog.coerceIn(0f, 1f)
        fxLevel = fx
        this.fireColor = fireColor
        if (!Settings.canDrawOverlays(context)) return
        if (view != null) return

        val wm = context.getSystemService(WindowManager::class.java) ?: return
        val screenH = screenHeightPx()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (screenH * MAX_COVER).toInt(),
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
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusBarHeightPx()
        }

        val compose = ComposeView(context).apply {
            val owner = OverlayLifecycleOwner()
            owner.attach()
            viewLifecycle = owner
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                FireWall(
                    progress = progress,
                    fx = fxLevel,
                    fireColor = fireColor,
                )
            }
        }
        runCatching { wm.addView(compose, params) }.onSuccess { view = compose }
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

    private fun screenHeightPx(): Int {
        val wm = context.getSystemService(WindowManager::class.java) ?: return 2400
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getMetrics(m)
            m.heightPixels
        }
    }

    /** Fire starts below the status bar, never over it. */
    private fun statusBarHeightPx(): Int {
        val res = context.resources
        val id = res.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) res.getDimensionPixelSize(id) else 0
    }

    companion object {
        /** Reel coverage cap when the ramp completes: 30% of the screen. */
        const val MAX_COVER = 0.30f
    }
}

@Composable
private fun FireWall(progress: Float, fx: Int, fireColor: Int) {
    // Fire hangs from the window's top edge and burns downward in
    // proportion to session progress.
    // No fade animation — fire only increases, never fades in/out.
    val fill = progress.coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (fill > 0.01f && fx >= 1) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(fill, fill = true)) {
                    DoomFire(
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.6f },
                        fx = fx,
                        intensity = 0.5f + fill * 0.9f,
                        baseColor = fireColor,
                    )
                }
                Spacer(Modifier.fillMaxWidth().weight(1f - fill, fill = false))
            }
        }
    }
}