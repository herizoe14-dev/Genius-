package com.genius.downloader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF07070B)
val Surface1 = Color(0xFF111118)
val Surface2 = Color(0xFF1A1A24)
val Cyan = Color(0xFF00E5FF)
val Magenta = Color(0xFFFF2D95)
val Violet = Color(0xFF7C4DFF)
val TextDim = Color(0xFF9A9AB0)
val Danger = Color(0xFFFF5370)
val Success = Color(0xFF3DDC97)

private val scheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Color.Black,
    secondary = Magenta,
    onSecondary = Color.White,
    tertiary = Violet,
    background = Bg,
    onBackground = Color.White,
    surface = Surface1,
    onSurface = Color.White,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextDim,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface2,
    outline = Color(0xFF2E2E3E),
    error = Danger,
)

@Composable
fun GeniusTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
