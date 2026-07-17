package com.magicbill.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/** True when the app-level theme is dark. Components (badges, charts) use this
 *  for semantic colors that sit outside the Material color scheme. */
val LocalMBDarkTheme = staticCompositionLocalOf { true }

private val DarkColors = darkColorScheme(
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

private val LightColors = lightColorScheme(
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

/**
 * Magic Bill theme. Dark is the app default (restaurant/POS environments);
 * a persisted light-mode preference arrives with the settings screen — until
 * then callers pass [darkTheme] explicitly if they need to override.
 */
@Composable
fun MagicBillTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMBDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = MBTypography,
            shapes = MBShapes,
            content = content,
        )
    }
}
