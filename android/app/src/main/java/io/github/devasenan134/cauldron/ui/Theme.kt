package io.github.devasenan134.cauldron.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The website's palette (web/src/index.css).
val Cream = Color(0xFFFAF6F0)
val Ink = Color(0xFF1F1B16)
val Ember = Color(0xFFC2410C)
val EmberSoft = Color(0xFFFFF1E6)
val Stone = Color(0xFF78716C)
val StoneLight = Color(0xFFE7E5E4)
val Amber = Color(0xFFB45309)
val AmberSoft = Color(0xFFFFFBEB)
val Sky = Color(0xFF0369A1)
val SkySoft = Color(0xFFF0F9FF)
val Danger = Color(0xFFB91C1C)

private val colors = lightColorScheme(
    primary = Ink,
    onPrimary = Cream,
    secondary = Ember,
    onSecondary = Color.White,
    secondaryContainer = EmberSoft,
    onSecondaryContainer = Ember,
    tertiary = Ember,
    inversePrimary = Color(0xFFFDBA74), // snackbar action text
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF1ECE4),
    onSurfaceVariant = Stone,
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color(0xFFF5F0E8),
    outline = Color(0xFFD6D3D1),
    outlineVariant = StoneLight,
    error = Danger,
)

@Composable
fun CauldronTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = colors, content = content)
