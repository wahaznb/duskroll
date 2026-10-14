package app.doomscroller.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Genuine DOOM fire, upside down: heat buffer, white-hot source row
 * at the top, each cell cools a random 0..2 and drifts sideways as
 * it falls.
 *
 * Pixels are perfect unfiltered squares: the sim grid is derived from
 * the canvas size ([cellPx] device pixels per cell) and upscaled with
 * nearest neighbour, so every flame block is crisp and square.
 * Heat 0 is transparent — flames lick out of nothing, seamlessly.
 *
 * fx: 1 = single static frame; 2 = animated.
 */
@Composable
fun DoomFire(
    modifier: Modifier = Modifier,
    fx: Int,
    intensity: Float = 1f,
    cellPx: Int = 10,
    baseColor: Int = DEFAULT_FIRE,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(1)
        val hPx = with(density) { maxHeight.toPx() }.toInt().coerceAtLeast(1)
        // Strictly square cells: use the same cell count for both dimensions
        // so every pixel is a perfect square when upscaled.
        val gw = (wPx / cellPx).coerceIn(8, 320)
        val gh = (hPx / cellPx).coerceIn(8, 320)
        val cellCount = minOf(gw, gh)
        var frame by remember { mutableStateOf(0) }
        val fire = remember(cellCount, cellCount, baseColor) { DoomFireState(cellCount, cellCount, baseColor) }

        if (fx == 2) {
            LaunchedEffect(cellCount, cellCount, intensity) {
                while (isActive) {
                    delay(80L)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        fire.spread(intensity)
                    }
                    frame += 1
                }
            }
        } else {
            LaunchedEffect(cellCount, cellCount, intensity) {
                repeat(40) { fire.spread(intensity) } // settle one static frame
                frame = 1
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            frame // invalidate
            drawImage(
                fire.snapshot(),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.None,
            )
        }
        androidx.compose.runtime.DisposableEffect(cellCount, cellCount, baseColor) {
            onDispose { fire.recycle() }
        }
    }
}

private class DoomFireState(val w: Int, val h: Int, baseColor: Int) {
    private val heat = IntArray(w * h)
    private val palette = buildPalette(baseColor)
    private var bitmap: Bitmap? = null
    private var pixels = IntArray(0)
    private val random = Random(System.nanoTime())

    init {
        for (x in 0 until w) heat[x] = PALETTE_LAST
    }

    fun spread(intensity: Float = 1f) {
        // Descending order so each row consumes last frame's heat from
        // the row above it — fire advances one row per tick, not instantly.
        for (y in h - 1 downTo 1) {
            for (x in 0 until w) {
                val src = (y - 1) * w + x
                val decay = random.nextInt(3) // 0..2, the doom way
                val dstX = (x - decay + 1 + w) % w // wind drift
                val dst = y * w + dstX
                heat[dst] = (heat[src] - decay).coerceAtLeast(0)
            }
        }
        // re-stoke the source row at the top, with flicker — hotter when intensity rises
        val stock = (PALETTE_LAST * intensity).toInt().coerceIn(8, PALETTE_LAST)
        for (x in 0 until w) {
            heat[x] = stock - random.nextInt(4).coerceAtMost(stock)
        }
    }

    /** Fixed-size heat snapshot; upscaling to the canvas happens in drawImage. */
    fun snapshot(): androidx.compose.ui.graphics.ImageBitmap {
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

    companion object {
        const val PALETTE_LAST = 36
    }
}

const val DEFAULT_FIRE = 0xFFFF3B2C.toInt() // Bright red for minimal doom palette

private fun buildPalette(base: Int): IntArray {
    // Minimal 3-color doom palette: transparent, black background, accent fire, white tips
    val r = (base shr 16) and 0xFF
    val g = (base shr 8) and 0xFF
    val b = base and 0xFF
    val n = 37
    val result = IntArray(n)
    for (i in 0 until n) {
        result[i] = when (i) {
            0 -> 0x00000000  // transparent — flames emerge from nothing
            in 1..12 -> 0xFF000000  // black — shadow/background areas
            in 13..24 -> (0xFF shl 24) or (r shl 16) or (g shl 8) or b  // accent fire
            else -> 0xFFFFFFFF  // white — hottest tips
        }
    }
    return result
}
