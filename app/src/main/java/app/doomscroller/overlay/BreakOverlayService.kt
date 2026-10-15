package app.doomscroller.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * The close ritual. No warnings, no text, no color: when the limit is
 * passed, a black Bayer-dither disc blooms from the center, the rest
 * fills white into a full black-and-white dither field, the app is
 * closed underneath, then the same animation plays in reverse to
 * reveal the home screen.
 */
class BreakOverlayService : Service() {

    private var overlayView: ComposeView? = null
    private var overlayLifecycle: OverlayLifecycleOwner? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showOverlay()
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (overlayView != null) return
        // No overlay permission → fall back to the plain activity ritual
        // so the close still happens instead of silently doing nothing.
        if (!Settings.canDrawOverlays(this)) {
            BreakActivity.start(this)
            stopSelf()
            return
        }
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = ComposeView(this).apply {
            val owner = OverlayLifecycleOwner()
            owner.attach()
            overlayLifecycle = owner
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                DitherCloseScreen(
                    onCloseApp = {
                        val home = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(home)
                    },
                    onDone = { removeOverlay() },
                )
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }
        runCatching { wm.addView(view, params) }
            .onSuccess { overlayView = view }
            .onFailure {
                // Overlay rejected (e.g. permission revoked mid-flight):
                // still run the ritual as an activity so the app closes.
                BreakActivity.start(this)
                stopSelf()
            }
    }

    private fun removeOverlay() {
        overlayView?.let { v ->
            runCatching {
                (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
            }
        }
        overlayView = null
        overlayLifecycle?.detach()
        overlayLifecycle = null
        stopSelf()
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val channelId = "break_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Limit reached", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val notification: Notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("Time is up")
            .setContentText("Closing the app.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(2, notification)
    }

    companion object {
        fun start(context: Context) {
            try {
                androidx.core.content.ContextCompat.startForegroundService(
                    context,
                    Intent(context, BreakOverlayService::class.java),
                )
            } catch (_: Exception) {
                BreakActivity.start(context)
            }
        }
    }
}

/** Ordered 8x8 — finer grain, reads as dither rather than dots. */
private val BAYER_8 = arrayOf(
    intArrayOf(0, 32, 8, 40, 2, 34, 10, 42),
    intArrayOf(48, 16, 56, 24, 50, 18, 58, 26),
    intArrayOf(12, 44, 4, 36, 14, 46, 6, 38),
    intArrayOf(60, 28, 52, 20, 62, 30, 54, 22),
    intArrayOf(3, 35, 11, 43, 1, 33, 9, 41),
    intArrayOf(51, 19, 59, 27, 49, 17, 57, 25),
    intArrayOf(15, 47, 7, 39, 13, 45, 5, 37),
    intArrayOf(63, 31, 55, 23, 61, 29, 53, 21),
)

/**
 * Close ritual: an ordered-dither density sweep. A full-screen Bayer-8
 * field thickens from transparent to solid black in 12 steps, holds
 * while the app closes underneath, then sweeps back out to reveal home.
 * Frames are precomputed per density step and cached — one drawImage
 * per frame, never per-cell work on the UI thread.
 */
@Composable
fun DitherCloseScreen(
    onCloseApp: () -> Unit,
    onDone: () -> Unit,
) {
    var elapsed by remember { mutableLongStateOf(0L) }
    var closed by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val start = android.os.SystemClock.uptimeMillis()
        while (isActive) {
            val t = android.os.SystemClock.uptimeMillis() - start
            elapsed = t
            if (t >= CLOSE_AT_MS && !closed) {
                closed = true
                onCloseApp()
            }
            if (t >= DONE_AT_MS) break
            delay(50L)
        }
        if (!done) {
            done = true
            onDone()
        }
    }

    // Density frames, built once per visited step, quarter-res chunky.
    val frames = remember { mutableMapOf<Int, androidx.compose.ui.graphics.ImageBitmap>() }
    fun frame(density: Int): androidx.compose.ui.graphics.ImageBitmap =
        frames.getOrPut(density.coerceIn(0, STEPS)) {
            val w = 270
            val h = 600
            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val px = IntArray(w * h)
            // DotForge-style: cell goes black once the threshold passes it.
            val gate = density * 64 / STEPS
            for (y in 0 until h) {
                for (x in 0 until w) {
                    px[y * w + x] = if (BAYER_8[y and 7][x and 7] < gate) -0x1000000 else 0x00000000
                }
            }
            bmp.setPixels(px, 0, w, 0, 0, w, h)
            bmp.asImageBitmap()
        }

    val density = when {
        elapsed < SWEEP_MS -> (elapsed.toFloat() / SWEEP_MS * STEPS).toInt()
        elapsed < CLOSE_AT_MS -> STEPS
        else -> STEPS - ((elapsed - CLOSE_AT_MS).toFloat() / SWEEP_MS * STEPS).toInt()
    }.coerceIn(0, STEPS)

    if (density > 0) {
        Canvas(Modifier.fillMaxSize()) {
            drawImage(
                frame(density),
                dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
            )
        }
    }
}

private const val STEPS = 12
private const val SWEEP_MS = 960L
private const val CLOSE_AT_MS = 1260L
private const val DONE_AT_MS = 2220L
