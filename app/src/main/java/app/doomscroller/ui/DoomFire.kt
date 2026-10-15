package app.doomscroller.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.random.Random

// ---------- minimal Doom fire ----------
@Composable
fun DoomFire(
    modifier: Modifier = Modifier,
    fx: Int,
    intensity: Float = 1f,
    cellPx: Int = 10,
    colorLow: Int = DEFAULT_FIRE_LOW,
    colorMid: Int = DEFAULT_FIRE,
    colorHigh: Int = DEFAULT_FIRE_HIGH,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(1)
        val hPx = with(density) { maxHeight.toPx() }.toInt().coerceAtLeast(1)
        val gw = (wPx / cellPx).coerceIn(8, 320)
        val gh = (hPx / cellPx).coerceIn(8, 320)
        var frame by remember { mutableStateOf(0) }
        val fire = remember(gw, gh, colorLow, colorMid, colorHigh) {
            DoomFireState(gw, gh, colorLow, colorMid, colorHigh)
        }
        val latestIntensity by rememberUpdatedState(intensity)

        if (fx == 2) {
            LaunchedEffect(gw, gh) {
                while (isActive) {
                    delay(60L)
                    withContext(Dispatchers.Default) {
                        fire.spread(latestIntensity)
                    }
                    frame += 1
                }
            }
        } else {
            LaunchedEffect(gw, gh) {
                repeat(40) { fire.spread(intensity) }
                frame = 1
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            frame
            val bmp = fire.snapshot()
            val scale = minOf(size.width / gw, size.height / gh).coerceAtLeast(1f)
            val dw = (gw * scale).toInt().coerceAtLeast(1)
            val dh = (gh * scale).toInt().coerceAtLeast(1)
            val ox = ((size.width - dw) / 2).toInt()
            val oy = ((size.height - dh) / 2).toInt()
            drawImage(
                image = bmp,
                dstOffset = IntOffset(ox, oy),
                dstSize = IntSize(dw, dh),
                filterQuality = FilterQuality.None,
            )
        }
        DisposableEffect(gw, gh, colorLow, colorMid, colorHigh) {
            onDispose { fire.recycle() }
        }
    }
}

private class DoomFireState(val w: Int, val h: Int, low: Int, mid: Int, high: Int) {
    private val heat = IntArray(w * h)
    private val palette = buildPalette(low, mid, high)
    private var bitmap: Bitmap? = null
    private var pixels = IntArray(0)
    private val random = Random(System.nanoTime())

    init {
        for (x in 0 until w) heat[x] = PALETTE_LAST
    }

    fun spread(intensity: Float = 1f) {
        for (y in h - 1 downTo 1) {
            for (x in 0 until w) {
                val src = (y - 1) * w + x
                val decay = random.nextInt(3)
                val dstX = (x - decay + 1 + w) % w
                val dst = y * w + dstX
                heat[dst] = (heat[src] - decay).coerceAtLeast(0)
            }
        }
        val stock = (PALETTE_LAST * intensity).toInt().coerceIn(8, PALETTE_LAST)
        for (x in 0 until w) {
            heat[x] = stock - random.nextInt(4).coerceAtMost(stock)
        }
    }

    fun snapshot(): ImageBitmap {
        if (bitmap == null || bitmap?.width != w || bitmap?.height != h) {
            bitmap?.recycle()
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            pixels = IntArray(w * h)
        }
        val bmp = bitmap!!
        for (i in heat.indices) pixels[i] = palette[heat[i]]
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        return bmp.asImageBitmap()
    }

    fun recycle() {
        bitmap?.recycle()
        bitmap = null
    }
}

const val DEFAULT_FIRE = 0xFFFF3B2C.toInt() // gradient MID default
const val DEFAULT_FIRE_LOW = 0xFF7A0D00.toInt() // gradient LOW default (deep red)
const val DEFAULT_FIRE_HIGH = 0xFFFFE9A8.toInt() // gradient HIGH default (pale yellow)

/** Classic DOOM-style gradient from three user stops: heat 0 is
 * transparent, heat 1..18 ramps LOW to MID, heat 19..36 ramps MID
 * to HIGH — dark base, saturated body, hot tips.
 */
private fun buildPalette(low: Int, mid: Int, high: Int): IntArray {
    val lo = intArrayOf((low shr 16) and 0xFF, (low shr 8) and 0xFF, low and 0xFF)
    val mm = intArrayOf((mid shr 16) and 0xFF, (mid shr 8) and 0xFF, mid and 0xFF)
    val hi = intArrayOf((high shr 16) and 0xFF, (high shr 8) and 0xFF, high and 0xFF)
    val n = 37
    val result = IntArray(n)
    for (i in 0 until n) {
        if (i == 0) {
            result[i] = 0x00000000.toInt()
        } else if (i <= 18) {
            val t = (i - 1) / 17f
            result[i] = lerpColor(lo, mm, t)
        } else {
            val t = (i - 18) / 18f
            result[i] = lerpColor(mm, hi, t)
        }
    }
    return result
}

private fun lerpColor(a: IntArray, b: IntArray, t: Float): Int {
    val r = (a[0] + (b[0] - a[0]) * t).toInt().coerceIn(0, 255)
    val g = (a[1] + (b[1] - a[1]) * t).toInt().coerceIn(0, 255)
    val bl = (a[2] + (b[2] - a[2]) * t).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
}

private const val PALETTE_LAST = 37