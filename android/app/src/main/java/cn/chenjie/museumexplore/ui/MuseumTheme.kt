package cn.chenjie.museumexplore.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

private val Paper = Color(0xFFF5F2EC)
private val Canvas = Color(0xFFFFFDF8)
private val Ink = Color(0xFF292722)
private val Muted = Color(0xFF817A70)
private val Cinnabar = Color(0xFFB64B3B)
private val Green = Color(0xFF426C5B)
private val Sand = Color(0xFFEAE2D6)

private val MuseumColors = lightColorScheme(
    primary = Cinnabar,
    onPrimary = Color.White,
    secondary = Green,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Canvas,
    onSurface = Ink,
    surfaceVariant = Sand,
    onSurfaceVariant = Muted,
    outline = Color(0xFFD8CEC0),
)

private val MuseumTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontFamily = FontFamily.Serif, color = Ink),
        headlineMedium = headlineMedium.copy(fontFamily = FontFamily.Serif, color = Ink),
        titleLarge = titleLarge.copy(fontFamily = FontFamily.Serif, color = Ink),
        titleMedium = titleMedium.copy(fontFamily = FontFamily.Serif, color = Ink),
        bodyLarge = bodyLarge.copy(color = Ink),
        bodyMedium = bodyMedium.copy(color = Muted),
        labelLarge = labelLarge.copy(color = Cinnabar),
    )
}

@Composable
fun MuseumTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MuseumColors, typography = MuseumTypography, content = content)
}
