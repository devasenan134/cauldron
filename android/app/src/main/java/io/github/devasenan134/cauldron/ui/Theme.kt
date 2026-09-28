package io.github.devasenan134.cauldron.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.devasenan134.cauldron.R

// The website's palette (web/src/index.css), warmed up a little for the app.
val Cream = Color(0xFFFAF6F0)
val Paper = Color(0xFFFFFFFF)
val Ink = Color(0xFF1F1B16)
val Ember = Color(0xFFC2410C)
val EmberBright = Color(0xFFEA580C)
val EmberSoft = Color(0xFFFFEDDF)
val Stone = Color(0xFF78716C)
val StoneLight = Color(0xFFE7E5E4)
val Amber = Color(0xFFB45309)
val AmberSoft = Color(0xFFFFF7E6)
val Sky = Color(0xFF0369A1)
val SkySoft = Color(0xFFEAF6FD)
val Leaf = Color(0xFF15803D)
val Danger = Color(0xFFB91C1C)

// Bricolage Grotesque, a variable font bundled in res/font (SIL Open Font License).
@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: FontWeight) =
    Font(R.font.bricolage_grotesque, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

/** Headings, numbers and the wordmark. */
val Display = FontFamily(bricolage(FontWeight.Medium), bricolage(FontWeight.SemiBold), bricolage(FontWeight.Bold), bricolage(FontWeight.ExtraBold))

private val base = Typography()
private fun TextStyle.display(weight: FontWeight = FontWeight.Bold) = copy(fontFamily = Display, fontWeight = weight, letterSpacing = (-0.01).em)

private val typography = base.copy(
    displayLarge = base.displayLarge.display(FontWeight.ExtraBold),
    displayMedium = base.displayMedium.display(FontWeight.ExtraBold),
    displaySmall = base.displaySmall.display(FontWeight.ExtraBold),
    headlineLarge = base.headlineLarge.display(FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.display(FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.display(),
    titleLarge = base.titleLarge.display(),
    titleMedium = base.titleMedium.display(FontWeight.SemiBold),
)

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
    surfaceContainer = Paper,
    surfaceContainerLow = Paper,
    surfaceContainerHigh = Color(0xFFF5F0E8),
    surfaceContainerHighest = Color(0xFFEFE9E0),
    outline = Color(0xFFD6D3D1),
    outlineVariant = StoneLight,
    error = Danger,
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun CauldronTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
