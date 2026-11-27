package app.yingyin.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

val Bg = Color(0xFF07080B)
val BgLift = Color(0xFF0B0D12)
val Panel = Color(0xFF11141B)
val CardBg = Color(0xFF171A22)
val Stroke = Color(0xFF262A34)
val TextMain = Color(0xFFF3F0E9)
val TextSub = Color(0xFF969AA5)
val TextDim = Color(0xFF666B78)
val Accent = Color(0xFFFF6B3D)
val AccentSoft = Color(0xFFFFB07A)
val AccentDark = Color(0xFF2B160F)
val Mint = Color(0xFF8FE1B3)

val Serif = FontFamily.Serif

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1A0B06),
    secondary = AccentSoft,
    background = Bg,
    surface = Panel,
    onBackground = TextMain,
    onSurface = TextMain,
    surfaceVariant = CardBg,
    onSurfaceVariant = TextSub
)

@Composable
fun YingyinTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = MaterialTheme.typography, content = content)
}
