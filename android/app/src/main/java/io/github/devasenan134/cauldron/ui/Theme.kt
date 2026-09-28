package io.github.devasenan134.cauldron.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
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

/**
 * The app's colours, in a light and a dark version. Clean and mostly monochrome (white or
 * near-black, with grey for secondary text and hairlines), plus a green for "go" actions and
 * soft pastels that tag things: blue = leftovers, purple = batch cooking, pink = eat soon.
 * The website uses the same values (web/src/index.css).
 */
@Immutable
data class Palette(
    val dark: Boolean,
    val bg: Color, val surface: Color, val surfaceAlt: Color,
    val ink: Color, val muted: Color, val faint: Color, val line: Color,
    val go: Color, val onGo: Color, val goText: Color, val goSoft: Color,
    val blueBg: Color, val blueFg: Color,
    val purpleBg: Color, val purpleFg: Color,
    val pinkBg: Color, val pinkFg: Color,
    val yellowBg: Color, val yellowFg: Color,
    val danger: Color,
)

val LightPalette = Palette(
    dark = false,
    bg = Color(0xFFF7F7F7), surface = Color(0xFFFFFFFF), surfaceAlt = Color(0xFFF0F0F0),
    ink = Color(0xFF0A0A0A), muted = Color(0xFF707070), faint = Color(0xFFA3A3A3), line = Color(0xFFE5E5E5),
    go = Color(0xFF4CC734), onGo = Color(0xFFFFFFFF), goText = Color(0xFF2F7D1F), goSoft = Color(0xFFE3F4DC),
    blueBg = Color(0xFFDFEAF4), blueFg = Color(0xFF2F6694),
    purpleBg = Color(0xFFECE6FA), purpleFg = Color(0xFF6A48C2),
    pinkBg = Color(0xFFFAE3EC), pinkFg = Color(0xFFC2386B),
    yellowBg = Color(0xFFFAF6C8), yellowFg = Color(0xFF8A7A00),
    danger = Color(0xFFD92D20),
)

val DarkPalette = Palette(
    dark = true,
    bg = Color(0xFF0B0B0C), surface = Color(0xFF161618), surfaceAlt = Color(0xFF1F1F22),
    ink = Color(0xFFF4F4F5), muted = Color(0xFFA1A1AA), faint = Color(0xFF71717A), line = Color(0xFF27272A),
    go = Color(0xFF5FD543), onGo = Color(0xFF0B0B0C), goText = Color(0xFF86D96F), goSoft = Color(0xFF1B2B17),
    blueBg = Color(0xFF16263A), blueFg = Color(0xFF7FB2E6),
    purpleBg = Color(0xFF231D36), purpleFg = Color(0xFFB59CF5),
    pinkBg = Color(0xFF34182A), pinkFg = Color(0xFFF28AB5),
    yellowBg = Color(0xFF2E2A10), yellowFg = Color(0xFFE3D35C),
    danger = Color(0xFFF97066),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

/** The current palette: `C.ink`, `C.surface`, … */
val C: Palette @Composable get() = LocalPalette.current

// Macro colours (protein, carbs, fat), the same in both themes.
val ProteinColor = Color(0xFF60A5FA)
val CarbsColor = Color(0xFFFBBF24)
val FatColor = Color(0xFFF472B6)

// Inter Tight for headings and numbers, Inter for text: variable fonts bundled in res/font (SIL OFL).
@OptIn(ExperimentalTextApi::class)
private fun font(res: Int, weight: FontWeight) =
    Font(res, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val Display = FontFamily(listOf(FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold).map { font(R.font.inter_tight, it) })
val Body = FontFamily(listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { font(R.font.inter, it) })

private val base = Typography()
private fun TextStyle.display(weight: FontWeight = FontWeight.Bold) = copy(fontFamily = Display, fontWeight = weight, letterSpacing = (-0.025).em)
private fun TextStyle.body() = copy(fontFamily = Body)

private val typography = Typography(
    displayLarge = base.displayLarge.display(FontWeight.ExtraBold),
    displayMedium = base.displayMedium.display(FontWeight.ExtraBold),
    displaySmall = base.displaySmall.display(FontWeight.ExtraBold),
    headlineLarge = base.headlineLarge.display(FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.display(FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.display(),
    titleLarge = base.titleLarge.display(),
    titleMedium = base.titleMedium.display(FontWeight.SemiBold),
    titleSmall = base.titleSmall.body(),
    bodyLarge = base.bodyLarge.body(),
    bodyMedium = base.bodyMedium.body(),
    bodySmall = base.bodySmall.body(),
    labelLarge = base.labelLarge.body(),
    labelMedium = base.labelMedium.body(),
    labelSmall = base.labelSmall.body(),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private fun scheme(p: Palette) = (if (p.dark) darkColorScheme() else lightColorScheme()).copy(
    primary = p.ink, onPrimary = p.bg,
    secondary = p.go, onSecondary = p.onGo,
    secondaryContainer = p.goSoft, onSecondaryContainer = p.goText,
    tertiary = p.go,
    inversePrimary = p.go, // snackbar action text
    background = p.bg, onBackground = p.ink,
    surface = p.bg, onSurface = p.ink,
    surfaceVariant = p.surfaceAlt, onSurfaceVariant = p.muted,
    surfaceContainerLowest = p.surface, surfaceContainerLow = p.surface, surfaceContainer = p.surface,
    surfaceContainerHigh = p.surface, surfaceContainerHighest = p.surfaceAlt,
    outline = p.line, outlineVariant = p.line,
    error = p.danger,
)

/** [theme]: "system", "light" or "dark" (a setting saved on the account). */
@Composable
fun CauldronTheme(theme: String = "system", content: @Composable () -> Unit) {
    val dark = when (theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val palette = if (dark) DarkPalette else LightPalette
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme(palette), typography = typography, shapes = shapes) {
            // Text and icons outside a Surface default to the theme's ink, not black.
            CompositionLocalProvider(LocalContentColor provides palette.ink, content = content)
        }
    }
}
