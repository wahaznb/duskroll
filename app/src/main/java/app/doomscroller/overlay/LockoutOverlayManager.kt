package app.doomscroller.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.doomscroller.ui.AppFont

/**
 * Lockout banner shown OVER the watched app (not inside duskroll):
 * when the user reopens Instagram/YouTube during a lockout they see
 * the warning + live countdown where they are. Never touchable.
 */
class LockoutOverlayManager(private val context: Context) {

    private var view: ComposeView? = null
    private var viewLifecycle: OverlayLifecycleOwner? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var line1 by mutableStateOf("LOCKED OUT")
    private var line2 by mutableStateOf("")

    fun show(remainingMs: Long) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(remainingMs) }
            return
        }
        val mins = (remainingMs.coerceAtLeast(0L)) / 60_000L
        val secs = ((remainingMs.coerceAtLeast(0L)) % 60_000L) / 1000L
        line1 = "LOCKED OUT"
        line2 = "%d:%02d LEFT".format(mins, secs)
        ensureWindow()
    }

    private fun ensureWindow() {
        if (!Settings.canDrawOverlays(context)) return
        if (view != null) return
        val wm = context.getSystemService(WindowManager::class.java) ?: return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val res = context.resources
        val statusBar = res.getIdentifier("status_bar_height", "dimen", "android")
            .takeIf { it > 0 }?.let { res.getDimensionPixelSize(it) } ?: 0
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusBar + 16
        }
        val compose = ComposeView(context).apply {
            val owner = OverlayLifecycleOwner()
            owner.attach()
            viewLifecycle = owner
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                Column(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.9f))
                        .border(2.dp, Color.Red)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(text = line1, color = Color.Red, fontSize = 14.sp, fontFamily = AppFont)
                    Text(text = line2, color = Color.White, fontSize = 18.sp, fontFamily = AppFont)
                }
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
}
