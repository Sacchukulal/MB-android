package com.magicbill.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
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
import com.magicbill.app.ui.components.GlowBackground

/*
 * The 2.x app's Material theme, verbatim: the same colour schemes, Inter typography and shapes
 * that the owner approved on real installs — with the emerald/teal GlowBackground behind every
 * screen. The `Mb` accessor lets newer screens keep reading tokens by job.
 */

/** True when the app-level theme is dark. Components use this for semantic colours. */
val LocalMBDarkTheme = staticCompositionLocalOf { true }

private val DarkColorsM3 = darkColorScheme(
    primary = Emerald,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = EmeraldDeep,
    secondary = Teal,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = Navy,
    onBackground = DarkInk,
    surface = Navy,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkInkMuted,
    surfaceTint = Emerald,
    surfaceBright = DarkSurfaceHighest,
    surfaceDim = Navy,
    surfaceContainerLowest = Navy,
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainer = NavySurface,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHighest,
    inverseSurface = DarkInk,
    inverseOnSurface = Navy,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

private val LightColorsM3 = lightColorScheme(
    primary = EmeraldDeep,
    onPrimary = LightSurface,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    inversePrimary = Emerald,
    secondary = LightSecondary,
    onSecondary = LightSurface,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightSurface,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = LightSurfaceLow,
    onSurfaceVariant = LightInkMuted,
    surfaceTint = EmeraldDeep,
    surfaceBright = LightSurface,
    surfaceDim = LightSurfaceLow,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightBackground,
    surfaceContainer = LightSurfaceLow,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = LightOutlineVariant,
    inverseSurface = LightInk,
    inverseOnSurface = LightBackground,
    error = LightError,
    onError = LightSurface,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

@Immutable
class MbTheme(val colors: MbColors, val type: MbType)

val LocalMb = staticCompositionLocalOf<MbTheme> { MbTheme(DarkColors, MbType()) }

/** `Mb.colors.accent`, `Mb.type.page` — the way newer screens reach the theme. */
object Mb {
    val colors: MbColors @Composable get() = LocalMb.current.colors
    val type: MbType @Composable get() = LocalMb.current.type
}

/** The one text-size setting multiplies the whole Material scale too. */
private fun Typography.scaled(f: Float): Typography {
    if (f == 1f) return this
    fun s(t: androidx.compose.ui.text.TextStyle) = t.copy(fontSize = t.fontSize * f, lineHeight = t.lineHeight * f)
    return Typography(
        displayLarge = s(displayLarge), displayMedium = s(displayMedium), displaySmall = s(displaySmall),
        headlineLarge = s(headlineLarge), headlineMedium = s(headlineMedium), headlineSmall = s(headlineSmall),
        titleLarge = s(titleLarge), titleMedium = s(titleMedium), titleSmall = s(titleSmall),
        bodyLarge = s(bodyLarge), bodyMedium = s(bodyMedium), bodySmall = s(bodySmall),
        labelLarge = s(labelLarge), labelMedium = s(labelMedium), labelSmall = s(labelSmall),
    )
}

@Composable
fun MagicBillTheme(mode: String, textScale: Float = 1f, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeControllerModes.DARK -> true
        ThemeControllerModes.LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    val colors = if (dark) DarkColorsM3 else LightColorsM3
    val mb = MbTheme(if (dark) DarkColors else LightColors, MbType(textScale))
    // Status and navigation bar icons follow the APP's theme, not the OS setting — the 2.x
    // hard-won fix: bare edge-to-edge left white icons on a white system theme over navy.
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
    CompositionLocalProvider(LocalMBDarkTheme provides dark, LocalMb provides mb) {
        MaterialTheme(colorScheme = colors, typography = MBTypography.scaled(textScale), shapes = MBShapes) {
            // Screens draw on the open canvas, never a Surface — anchor the default text colour,
            // and put the signature emerald/teal glow behind everything.
            CompositionLocalProvider(LocalContentColor provides colors.onBackground, LocalTextStyle provides mb.type.body) {
                GlowBackground(Modifier.fillMaxSize()) { content() }
            }
        }
    }
}

object ThemeControllerModes {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"
}

@Composable
fun windowWidthDp(): Dp {
    val px = LocalWindowInfo.current.containerSize.width
    return with(LocalDensity.current) { px.toDp() }
}

@Composable
fun isWide(): Boolean = windowWidthDp() >= Breakpoint.wide
