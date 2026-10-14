package app.doomscroller.ui

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.random.Random

/** House easing: ease-out-back. Everything resolves in <=300ms. */
val EaseOutBack = Easing { x ->
    val c1 = 1.70158f
    val c3 = c1 + 1f
    (1 + c3 * (x - 1).pow(3) + c1 * (x - 1).pow(2)).toFloat()
}

private const val SCRAMBLE_GLYPHS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789/#<>*+|/\\"

/**
 * JoJo title card entrance: slams in from 2.6x scale with a red flash.
 */
@Composable
fun SlamTitle(text: String, modifier: Modifier = Modifier) {
    var p by remember(text) { mutableFloatStateOf(0f) }
    LaunchedEffect(text) {
        animate(0f, 1f, animationSpec = tween(450)) { v, _ -> p = v }
    }
    val scale = 2.6f - 1.6f * p
    Box(modifier) {
        if (p < 1f) {
            Box(
                Modifier.matchParentSize()
                    .alpha((1f - p) * 0.55f)
                    .background(ink())
            )
        }
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .alpha(p.coerceIn(0f, 1f))
        ) {
            JoJoTitle(text = text)
        }
    }
}

/**
 * Watch-Dogs decoding readout: random glyphs resolve left-to-right
 * into the real value whenever [text] changes.
 */
@Composable
fun ScrambleText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    durationMs: Int = 450,
) {
    var shown by remember(text) { mutableStateOf(scramble(text)) }
    LaunchedEffect(text) {
        val frames = (durationMs / 30).coerceAtLeast(1)
        repeat(frames) { f ->
            val reveal = ((f + 1) / frames.toFloat() * text.length).toInt()
            shown = text.take(reveal) + scramble(text.drop(reveal))
            delay(30L)
        }
        shown = text
    }
    Text(shown, style = style, modifier = modifier)
}

private fun scramble(s: String): String = buildString {
    for (c in s) {
        append(if (c == ' ') ' ' else SCRAMBLE_GLYPHS.random())
    }
}

/**
 * Screen shake. Set trauma to 1f to kick it; it decays on its own.
 */
fun Modifier.shake(trauma: Float): Modifier = composed {
    var offset by remember { mutableStateOf(IntOffset.Zero) }
    LaunchedEffect(trauma) {
        if (trauma <= 0f) {
            offset = IntOffset.Zero
            return@LaunchedEffect
        }
        var t = trauma.coerceIn(0f, 1f)
        val rnd = Random(System.nanoTime())
        while (t > 0.02f) {
            val mag = (t * t * 14f).toInt()
            offset = IntOffset(rnd.nextInt(-mag, mag + 1), rnd.nextInt(-mag, mag + 1))
            delay(33L)
            t *= 0.88f
        }
        offset = IntOffset.Zero
    }
    this.offset { offset }
}

/**
 * Corruption burst: 2–4 horizontal 8px slices displaced ±10px over
 * dither bars, ending with a 1-frame full inversion flash.
 */
@Composable
fun CorruptSlices(modifier: Modifier = Modifier) {
    var frame by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        repeat(5) {
            frame += 1
            delay(30L)
        }
    }
    val inkColor = ink()
    Canvas(modifier) {
        if (frame >= 5) {
            // 1-frame inversion: swap the field
            drawRect(Color.White.copy(alpha = 0.85f), size = size)
            return@Canvas
        }
        val rnd = Random(frame * 7919L)
        repeat(4) {
            val y = rnd.nextFloat() * (size.height - 8f)
            val dx = (rnd.nextInt(-10, 11)).toFloat()
            drawRect(
                Color.White.copy(alpha = 0.28f),
                topLeft = Offset(dx, y),
                size = Size(size.width, 8f),
            )
            drawRect(
                inkColor.copy(alpha = 0.5f),
                topLeft = Offset(-dx, y + 8f),
                size = Size(size.width, 2f),
            )
        }
    }
}

/**
 * Screen transition: cascade pop — scale 0.9→1, fade in over 300ms
 * with a 3px animated dither strip riding the top edge.
 */
@Composable
fun CascadeSwap(tab: Int, fx: Int, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    var p by remember { mutableFloatStateOf(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(tab) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        if (fx == 0) {
            p = 1f
            return@LaunchedEffect
        }
        animate(0f, 1f, animationSpec = tween(300, easing = EaseOutBack)) { v, _ -> p = v }
        p = 1f
    }
    Box(
        modifier
            .graphicsLayer {
                val s = 0.9f + 0.1f * p
                scaleX = s
                scaleY = s
                alpha = p
            }
    ) {
        content()
    }
    if (p < 1f && fx == 2) {
        val stripInk = ink()
        Canvas(Modifier.fillMaxSize()) {
            // 3px dither strip riding the reveal edge
            val y = size.height * (1f - p)
            var x = 0f
            var ix = (p * 40).toInt()
            while (x < size.width) {
                if ((ix * 7 + 3) % 5 < 2) {
                    drawRect(stripInk.copy(alpha = 0.8f), topLeft = Offset(x, y), size = Size(6f, 3f))
                }
                x += 6f
                ix += 1
            }
        }
    }
}

/**
 * Slab button: perfect hard rectangle, no ripple. Press = instant
 * full color-reverse + scale 0.95. Release = 80ms Bayer flash, then
 * ease-out-back to 100% in 200ms, plus a 60ms RGB channel split.
 */
@Composable
fun SlayerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    red: Boolean = true,
    label: String,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val paper = MaterialTheme.colorScheme.background
    // primary slab: ink fill, paper text; ghost: hairline. Full invert on press.
    val baseBg = if (red) ink() else Color.Transparent
    val baseFg = if (red) paper else ink()
    // inverted while held
    val bg = if (pressed) (if (red) paper else ink()) else baseBg
    val fg = if (pressed) (if (red) ink() else paper) else baseFg

    var scaleT by remember { mutableFloatStateOf(1f) }
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) {
            scaleT = 0.95f
        } else {
            // release: 80ms dither flash, ease-out-back to full in 200ms
            flash = true
            animate(scaleT, 1f, animationSpec = tween(200, easing = EaseOutBack)) { v, _ -> scaleT = v }
            delay(80L)
            flash = false
        }
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scaleT
                scaleY = scaleT
            }
            .background(bg)
            .border(1.dp, ink())
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = 13.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = fg)
        if (flash) DitherFlash(Modifier.matchParentSize())
    }
}

/** 1-frame-ish Bayer-4x4 flash at 30% for the tap release. */
@Composable
private fun DitherFlash(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        var y = 0f
        var iy = 0
        while (y < size.height) {
            var x = 0f
            var ix = 0
            while (x < size.width) {
                if ((ix * 5 + iy * 3) % 4 == 0) {
                    drawRect(Color.White.copy(alpha = 0.3f), topLeft = Offset(x, y), size = Size(3f, 3f))
                }
                x += 6f
                ix += 1
            }
            y += 6f
            iy += 1
        }
    }
}

/**
 * Staggered entrance for list rows: scale 0.9→1 + fade,
 * 50ms stagger, ~300ms total.
 */
fun Modifier.staggerIn(index: Int): Modifier = composed {
    var v by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        delay((index * 50L).coerceAtMost(500L))
        animate(0f, 1f, animationSpec = tween(250, easing = EaseOutBack)) { value, _ -> v = value }
    }
    this
        .graphicsLayer {
            val s = 0.9f + 0.1f * v
            scaleX = s
            scaleY = s
            alpha = v
        }
        .offset { IntOffset(0, ((1f - v) * 24).toInt()) }
}
