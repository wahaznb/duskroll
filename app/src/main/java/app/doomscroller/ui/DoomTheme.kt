package app.doomscroller.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import app.doomscroller.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// ---------- palette: pure black & white. ink() is white in dark mode, ----------
// ---------- black in light mode. Fire keeps its user-chosen color. ----------

@Composable
fun ink(): Color = MaterialTheme.colorScheme.onBackground

private val BlackScheme = darkColorScheme(
    background = Color(0xFF111118),
    surface = Color(0xFF17171F),
    onBackground = Color.White,
    onSurface = Color.White,
    primary = Color.White,
    secondary = Color.White,
    tertiary = Color.White,
)
private val WhiteScheme = lightColorScheme(
    background = Color.White,
    surface = Color(0xFFEFEDE8),
    onBackground = Color.Black,
    onSurface = Color.Black,
    primary = Color.Black,
    secondary = Color.Black,
    tertiary = Color.Black,
)

// ---------- one family, two weights: Regular for data, Bold for headers ----------

val AppFont = FontFamily(
    Font(R.font.plexmono, FontWeight.Normal),
    Font(R.font.plexmono_bold, FontWeight.Bold),
)

@Composable
fun DoomTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) BlackScheme else WhiteScheme, content = content)
}

/**
 * JoJo-style title card text: over-the-top display face, hard outline,
 * hard offset shadow. Every glyph stays consistent (single font, no warp).
 */
@Composable
fun JoJoTitle(text: String, modifier: Modifier = Modifier) {
    val paper = MaterialTheme.colorScheme.background
    val style = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.Bold,
        color = ink(),
        textAlign = TextAlign.Center,
    )
    Box(modifier) {
        Text(
            text = text, style = style.copy(
                color = ink().copy(alpha = 0.35f),
                drawStyle = Stroke(width = 10f),
            ),
            modifier = Modifier.offset(4.dp, 4.dp),
        )
        Text(
            text = text, style = style.copy(
                color = paper,
                drawStyle = Stroke(width = 6f),
            ),
        )
        Text(text = text, style = style)
    }
}

// ---------- ordered bayer dither ----------

private val BAYER_4 = arrayOf(
    intArrayOf(0, 8, 2, 10),
    intArrayOf(12, 4, 14, 6),
    intArrayOf(3, 11, 1, 9),
    intArrayOf(15, 7, 13, 5),
)

private data class Ripple(val at: Offset, val startMs: Long)

private fun bayer(x: Int, y: Int): Float =
    BAYER_4[y and 3][x and 3] / 16f

/**
 * Full-screen field: faint static dither + expanding dither ring wherever
 * the user touches. Minimal alpha so content stays king.
 */
@Composable
fun BoxScope.DitherField(fx: Int, dotColor: Color) {
    if (fx == 0) return
    val ripples = remember { mutableStateListOf<Ripple>() }
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableStateOf(0L) }

    // Ripple decay only runs while ripples exist — no idle ticker.
    LaunchedEffect(ripples.size) {
        if (ripples.isEmpty()) return@LaunchedEffect
        while (isActive && ripples.isNotEmpty()) {
            delay(66L)
            frame += 1
            val now = System.currentTimeMillis()
            ripples.removeAll { now - it.startMs > 900 }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { sizePx = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if (change.pressed) {
                                if (fx == 2) {
                                    ripples.add(Ripple(change.position, System.currentTimeMillis()))
                                    if (ripples.size > 4) ripples.removeAt(0)
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            }
    ) {
        frame // read to invalidate each tick
        val step = 7f
        val now = System.currentTimeMillis()
        var y = 0f
        var iy = 0
        while (y < size.height) {
            var x = 0f
            var ix = 0
            while (x < size.width) {
                var a = 0.05f // faint static grain
                for (r in ripples) {
                    val d = (Offset(x, y) - r.at).getDistance()
                    val age = (now - r.startMs) / 900f
                    if (age in 0f..1f) {
                        val ring = age * 420f
                        val band = 70f
                        val dist = kotlin.math.abs(d - ring)
                        if (dist < band) {
                            a = maxOf(a, 0.35f * (1f - dist / band) * (1f - age))
                        }
                    }
                }
                if (bayer(ix, iy) < a * 4f) {
                    drawCircle(dotColor.copy(alpha = a.coerceIn(0f, 0.4f)), radius = 1.4f, center = Offset(x, y))
                }
                x += step
                ix += 1
            }
            y += step
            iy += 1
        }
    }
}

/** CRT shell removed for performance: plain pass-through, no per-frame work. */
@Composable
fun CrtShell(fx: Int, flickerSeed: Long = 0L, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()
    }
}

/**
 * True pixelation-to-clarity: text is rasterized tiny, upscaled with
 * nearest-neighbour, resolution animating up in retro steps whenever
 * [text] changes. Real-time, no prerendered assets.
 */
@Composable
fun PixelRevealText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    steps: Int = 6,
    durationMs: Int = 650,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val layout = remember(text, style) { measurer.measure(text, style) }
    var progress by remember(text) { mutableFloatStateOf(0f) }
    LaunchedEffect(text) {
        animate(0f, 1f, animationSpec = tween(durationMs)) { v, _ -> progress = v }
    }
    val q = (((progress * steps).toInt() + 1).coerceAtMost(steps)) / steps.toFloat()
    val drawScope = remember { CanvasDrawScope() }
    val fullSize = layout.size.toSize()
    // Reused scratch bitmap per resolution step — no per-frame allocation.
    val w = (fullSize.width * q).toInt().coerceAtLeast(2)
    val h = (fullSize.height * q).toInt().coerceAtLeast(2)
    val bmp = remember(w, h) { ImageBitmap(w, h) }
    val c = remember(w, h) { Canvas(bmp) }
    Canvas(
        modifier.then(
            Modifier.size(with(density) { fullSize.toDpSize() })
        )
    ) {
        drawScope.draw(density, layoutDirection, c, Size(w.toFloat(), h.toFloat())) {
            scale(q, q, pivot = Offset.Zero) {
                drawText(measurer, text, style = style, topLeft = Offset.Zero)
            }
        }
        drawImage(
            bmp,
            dstOffset = IntOffset.Zero,
            dstSize = androidx.compose.ui.unit.IntSize(fullSize.width.toInt(), fullSize.height.toInt()),
            filterQuality = FilterQuality.None,
        )
    }
}
