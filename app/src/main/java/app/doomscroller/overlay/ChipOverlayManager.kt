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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.doomscroller.data.SettingsRepository
import app.doomscroller.ui.AppFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Live counter slab: two lines, minutes + reels/scrolls, hard rectangle.
 * Draggable anywhere — position persists. Never auto-hides; idles in
 * place when no session runs. Hidden only by toggle-off or destroy.
 */
class ChipOverlayManager(private val context: Context) {

    private var view: ComposeView? = null
    private var viewLifecycle: OverlayLifecycleOwner? = null
    private var winParams: WindowManager.LayoutParams? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repo by lazy { SettingsRepository(context) }
    private var posLoaded = false

    private var line1 by mutableStateOf("◉ IDLE")
    private var line2 by mutableStateOf("")
    private var hot by mutableStateOf(false)

    fun show(reelCount: Long, sessionMs: Long, thresholdMs: Long) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(reelCount, sessionMs, thresholdMs) }
            return
        }
        val mins = sessionMs / 60_000L
        line1 = (if (thresholdMs > 0 && sessionMs >= thresholdMs * 4 / 5) "!! " else "") +
            if (mins < 1) "<1 MIN" else "$mins MIN"
        line2 = "${reelCount}R"
        hot = thresholdMs > 0 && sessionMs >= thresholdMs * 4 / 5
        ensureWindow()
    }

    fun showIdle() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showIdle() }
            return
        }
        line1 = "◉ IDLE"
        line2 = ""
        hot = false
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
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 120
        }
        winParams = params
        // Restore the user's dragged position.
        if (!posLoaded) {
            posLoaded = true
            io.launch {
                val (px, py) = runCatching { repo.chipPos() }.getOrDefault(16 to 120)
                mainHandler.post {
                    // Sanitize: a stale position could park the chip off-screen.
                    params.x = px.coerceIn(0, 900)
                    params.y = py.coerceIn(0, 2200)
                    view?.let { v ->
                        runCatching {
                            wm.updateViewLayout(v, params)
                        }
                    }
                }
            }
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
                        .background(Color.Black.copy(alpha = 0.85f))
                        .border((if (hot) 2.dp else 1.dp), Color.White)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    val p = winParams ?: return@detectDragGestures
                                    io.launch {
                                        runCatching { repo.setChipPos(p.x, p.y) }
                                    }
                                },
                                onDrag = { change, drag ->
                                    change.consume()
                                    val v = view ?: return@detectDragGestures
                                    val p = winParams ?: return@detectDragGestures
                                    // Clamped: with END gravity, +x runs toward the
                                    // left edge, so the finger delta is negated;
                                    // y runs naturally downward. Both stay on-screen.
                                    p.x = (p.x - drag.x.toInt()).coerceIn(0, 900)
                                    p.y = (p.y + drag.y.toInt()).coerceIn(0, 2200)
                                    runCatching { wm.updateViewLayout(v, p) }
                                },
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = line1,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = AppFont,
                    )
                    if (line2.isNotEmpty()) {
                        Text(
                            text = line2,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = AppFont,
                        )
                    }
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

    fun destroy() {
        hide()
        io.cancel()
    }
}
