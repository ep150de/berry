package com.alexanderlingle.berry.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BerryPurple,
    onPrimary = Color.White,
    primaryContainer = BerryPurpleLight,
    onPrimaryContainer = BerryInk,
    secondary = BerryPink,
    onSecondary = Color.White,
    secondaryContainer = BerryPinkLight,
    onSecondaryContainer = BerryPinkDark,
    tertiary = BerryTeal,
    onTertiary = Color.White,
    tertiaryContainer = BerryTealLight,
    onTertiaryContainer = Color(0xFF0E2E27),
    background = LightBackground,
    surface = LightBackground,
    surfaceVariant = LightSurfaceVariant,
)

private val DarkColors = darkColorScheme(
    primary = BerryPurpleOnDark,
    onPrimary = Color(0xFF381E5E),
    primaryContainer = BerryPurpleDark,
    onPrimaryContainer = BerryPurpleLight,
    secondary = BerryPinkOnDark,
    onSecondary = BerryPinkDark,
    secondaryContainer = Color(0xFF5C2E44),
    onSecondaryContainer = BerryPinkLight,
    tertiary = Color(0xFF8FD0BE),
    onTertiary = Color(0xFF0E2E27),
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
)

@Composable
fun BerryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = BerryTypography,
        content = content
    )
}
