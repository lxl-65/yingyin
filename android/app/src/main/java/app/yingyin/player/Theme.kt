package app.yingyin.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFFF5D3B)
val Bg = Color(0xFF0C0D11)
val CardBg = Color(0xFF16181F)
val TextMain = Color(0xFFE8E9EE)
val TextSub = Color(0xFF8B8F9A)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Bg,
    onBackground = TextMain,
    surface = CardBg,
    onSurface = TextMain,
    surfaceVariant = Color(0xFF1B1D25),
    onSurfaceVariant = TextSub
)

@Composable
fun YingyinTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
