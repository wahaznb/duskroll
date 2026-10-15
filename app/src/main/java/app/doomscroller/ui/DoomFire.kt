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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

/**
 * Genuine DOOM fire, upside down: heat buffer, white-hot source row
 * at the top, each cell cools a random 0..2 and drifts sideways as
 * it falls.
 *
 * Pixels are perfect unfiltered squares via nearest-neighbor scaling.
 */
@Composable
fun DoomFire(
    modifier: Modifier = Modifier,
    fx: Int,
    intensity: Float = 1f,
    cellPx: Int = 12,
    baseColor: Int = DEFAULT_FIRE,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(1)
        val hPx = with(density) { maxHeight.toPx() }.toInt().coerceAtLeast(1)
        
        // Calculate cell count to maintain square pixels
        val cellCountW = (wPx / cellPx).coerceIn(8, 320)
        val cellCountH = (hPx / cellPx).coerceIn(8, 320)
        // Use minimum to ensure square cells
        val cellCount = minOf(cellCountW, cellCountH)
        
        var frame by remember { mutableStateOf(0) }
        val fire = remember(cellCount, baseColor) { DoomFireState(cellCount, cellCount, baseColor) }

        if (fx == 2) {
            LaunchedEffect(cellCount, intensity) {
                while (isActive) {
                    delay(60L)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        fire.spread(intensity)
                    }
                    frame += 1
                }
            }
        } else {
            LaunchedEffect(cellCount, intensity) {
                repeat(40) { fire.spread(intensity) }
                frame = 1
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            frame
            val bmp = fire.snapshot()
            drawImage(
                image = bmp,
                dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.None,
            )
        }
        androidx.compose.runtime.DisposableEffect(cellCount, baseColor) {
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
        // Descending order — fire flows downward
        for (y in h - 1 downTo 1) {
            for (x in 0 until w) {
                val src = (y - 1) * w + x
                val decay = random.nextInt(3)
                val dstX = (x - decay + 1 + w) % w
                val dst = y * w + dstX
                heat[dst] = (heat[src] - decay).coerceAtLeast(0)
            }
        }
        // Re-stoke top row with flicker
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

    companion object {
        const val PALETTE_LAST = 36
    }
}

const val DEFAULT_FIRE = 0xFFFF3B2C.toInt() // Bright red

private fun buildPalette(base: Int): IntArray {
    // Vibrant 3-color doom palette:
    //   0 = transparent (nothing)
    //   1-12 = deep black (shadows)
    //   13-24 = intense accent color (body of fire)
    //   25-36 = bright white-yellow (hot tips)
    val r = (base shr 16) and 0xFF
    val g = (base shr 8) and 0xFF
    val b = base and 0xFF
    
    // Boost saturation for vibrant look
    val brightR = minOf(255, r + 40)
    val brightG = minOf(255, g + 20)
    val brightB = minOf(255, b + 10)
    
    val n = 37
    val result = IntArray(n)
    for (i in 0 until n) {
        when (i) {
            0 -> result[i] = 0x00000000.toInt()  // transparent
            in 1..12 -> result[i] = 0xFF000000.toInt()  // pure black
            in 13..24 -> {
                // Blend between base color and brighter version for depth
                val t = (i - 12) / 12f
                val pr = (r + (brightR - r) * t).toInt().coerceIn(0, 255)
                val pg = (g + (brightG - g) * t).toInt().coerceIn(0, 255)
                val pb = (b + (brightB - b) * t).toInt().coerceIn(0, 255)
                result[i] = (0xFF shl 24) or (pr shl 16) or (pg shl 8) or pb
            }
            else -> {
                // White-yellow gradient for hot tips
                val t = (i - 24) / 12f
                val wr = (255 * t + 255 * (1-t) * 0.9f).toInt()
                val wg = (255 * t + 220 * (1-t)).toInt()
                val wb = (200 * t + 150 * (1-t)).toInt()
                result[i] = (0xFF shl 24) or (wr shl 16) or (wg shl 8) or wb
            }
        }
    }
    return result
}
