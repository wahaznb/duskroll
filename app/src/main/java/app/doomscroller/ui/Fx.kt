package app.doomscroller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- plain slab button ----------
@Composable
fun SlayerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    filled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val paper = MaterialTheme.colorScheme.background
    val ink = ink()
    val bg = if (filled) ink else Color.Transparent
    val fg = if (filled) paper else ink
    val bg2 = if (pressed) fg else bg
    val fg2 = if (pressed) bg else fg
    Box(
        modifier = modifier
            .background(bg2)
            .border(1.dp, ink)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = 13.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 15.sp, color = fg2)
    }
}