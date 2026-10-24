package id.karaoke.timerlauncher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgDark = Color(0xFF0F1117)
val SurfaceDark = Color(0xFF1A1D29)
val PrimaryIndigo = Color(0xFF6366F1)
val SecondaryPurple = Color(0xFFA855F7)
val AccentAmber = Color(0xFFF59E0B)
val DangerRed = Color(0xFFEF4444)
val SuccessGreen = Color(0xFF10B981)
val TextPrimary = Color(0xFFF3F4F6)
val TextMuted = Color(0xFF9CA3AF)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryIndigo,
    secondary = SecondaryPurple,
    background = BgDark,
    surface = SurfaceDark,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun KaraokeLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
