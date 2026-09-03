package com.magicbill.app.ui.theme

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import com.magicbill.app.ui.kit.Glow

/*
 * The theme: ONE palette (Palette.kt) and ONE set of scales (Tokens.kt), and everything else
 * derived. The Material components get a colour scheme and a typography built FROM those, so
 * a switch, a text field and a screen's own text can never disagree. Screens and the kit read
 * `Mb.colors.*` and `Mb.type.*`; nothing reads a colour by number outside Palette.kt.
 */

@Immutable
class MbTheme(val colors: MbColors, val type: MbType)

val LocalMb = staticCompositionLocalOf<MbTheme> { MbTheme(DarkColors, MbType()) }

/** `Mb.colors.accent`, `Mb.type.page` — the way every screen reaches the theme. */
object Mb {
    val colors: MbColors @Composable get() = LocalMb.current.colors
    val type: MbType @Composable get() = LocalMb.current.type
}

/** The Material scheme, from the palette. Only Material's own components read this. */
private fun schemeOf(c: MbColors): ColorScheme = if (c.isDark) {
    darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.onAccentSoft,
        inversePrimary = c.accent, secondary = c.accent2, onSecondary = c.onAccent, secondaryContainer = c.infoSoft, onSecondaryContainer = c.info,
        tertiary = c.warn, onTertiary = c.bg, tertiaryContainer = c.warnSoft, onTertiaryContainer = c.warn,
        background = c.bg, onBackground = c.ink, surface = c.bg, onSurface = c.ink,
        surfaceVariant = c.raised, onSurfaceVariant = c.inkMuted, surfaceTint = c.accent,
        surfaceBright = c.raisedHigh, surfaceDim = c.bg, surfaceContainerLowest = c.bg, surfaceContainerLow = c.surface,
        surfaceContainer = c.surface, surfaceContainerHigh = c.raised, surfaceContainerHighest = c.raisedHigh,
        inverseSurface = c.ink, inverseOnSurface = c.bg,
        error = c.danger, onError = c.bg, errorContainer = c.dangerSoft, onErrorContainer = c.danger,
        outline = c.line, outlineVariant = c.lineSoft, scrim = c.bg,
    )
} else {
    lightColorScheme(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.onAccentSoft,
        inversePrimary = c.accent, secondary = c.accent2, onSecondary = c.onAccent, secondaryContainer = c.infoSoft, onSecondaryContainer = c.info,
        tertiary = c.warn, onTertiary = c.surface, tertiaryContainer = c.warnSoft, onTertiaryContainer = c.warn,
        background = c.bg, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.raised, onSurfaceVariant = c.inkMuted, surfaceTint = c.accent,
        surfaceBright = c.surface, surfaceDim = c.raised, surfaceContainerLowest = c.surface, surfaceContainerLow = c.bg,
        surfaceContainer = c.raised, surfaceContainerHigh = c.raisedHigh, surfaceContainerHighest = c.lineSoft,
        inverseSurface = c.ink, inverseOnSurface = c.bg,
        error = c.danger, onError = c.surface, errorContainer = c.dangerSoft, onErrorContainer = c.danger,
        outline = c.line, outlineVariant = c.lineSoft, scrim = c.ink,
    )
}

/** Material's typography, from the type scale — so a Material component's text is ours too. */
private fun typographyOf(t: MbType): Typography = Typography(
    displayLarge = t.hero, displayMedium = t.hero, displaySmall = t.brand,
    headlineLarge = t.page, headlineMedium = t.page, headlineSmall = t.page,
    titleLarge = t.stat, titleMedium = t.section, titleSmall = t.statSmall,
    bodyLarge = t.body, bodyMedium = t.cell, bodySmall = t.caption,
    labelLarge = t.button, labelMedium = t.label, labelSmall = t.navLabel,
)

@Composable
fun MagicBillTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    val mb = MbTheme(colors, MbType())
    // Status and navigation bar icons follow the APP's theme, not the OS setting: bare
    // edge-to-edge left white icons on a white system theme over navy.
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.LaunchedEffect(dark) {
            (view.context as? android.app.Activity)?.window?.let { w ->
                val c = androidx.core.view.WindowCompat.getInsetsController(w, view)
                c.isAppearanceLightStatusBars = !dark
                c.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalMb provides mb) {
        MaterialTheme(colorScheme = schemeOf(colors), typography = typographyOf(mb.type), shapes = MBShapes) {
            // Screens draw on the open canvas, never a Surface — anchor the default text colour,
            // and put the signature glow behind everything.
            // ONE overscroll for every list and page: the rubber band, never the stretch that
            // re-rasterises the type each frame.
            CompositionLocalProvider(
                LocalContentColor provides colors.ink,
                LocalTextStyle provides mb.type.body,
                LocalOverscrollFactory provides RubberBandOverscrollFactory,
            ) {
                Glow(Modifier.fillMaxSize()) { content() }
            }
        }
    }
}

@Composable
fun windowWidthDp(): Dp {
    val px = LocalWindowInfo.current.containerSize.width
    return with(LocalDensity.current) { px.toDp() }
}

@Composable
fun isWide(): Boolean = windowWidthDp() >= Breakpoint.wide
