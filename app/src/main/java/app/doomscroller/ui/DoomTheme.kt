package app.doomscroller.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import app.doomscroller.R

// ---------- palette: pure black & white ----------
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

// ---------- thin font: one family, Light weight ----------
val AppFont = FontFamily(
    Font(R.font.plexmono, FontWeight.Light),
    Font(R.font.plexmono_bold, FontWeight.Normal),
)

@Composable
fun DoomTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) BlackScheme else WhiteScheme, content = content)
}

// ---------- plain title ----------
@Composable
fun DoomTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontFamily = AppFont,
            fontWeight = FontWeight.Normal,
            color = ink(),
            textAlign = TextAlign.Center,
            fontSize = 20.sp,
        ),
    )
}

// ---------- plain dither field (faint static grain) ----------
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

@Composable
fun BoxScope.DitherField(fx: Int, dotColor: Color) {
    if (fx == 0) return
    Canvas(
        Modifier.fillMaxSize(),
    ) {
        val step = 8f
        var y = 0f
        var iy = 0
        while (y < size.height) {
            var x = 0f
            var ix = 0
            while (x < size.width) {
                if (BAYER_8[iy and 7][ix and 7] < 16) {
                    drawCircle(dotColor.copy(alpha = 0.04f), radius = 1f, center = Offset(x, y))
                }
                x += step
                ix += 1
            }
            y += step
            iy += 1
        }
    }
}

@Composable
fun CrtShell(fx: Int, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) { content() }
}