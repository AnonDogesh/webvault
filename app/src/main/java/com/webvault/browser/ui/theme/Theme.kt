package com.webvault.browser.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = PrimaryBlue,
    background = AppBackground,
    surface = AppSurface,
    onPrimary = AppSurface,
    onBackground = SecondaryText,
    onSurface = SecondaryText
)

private val DarkColors = darkColorScheme(
    primary = PrimaryBlue,
    background = AppBackground,
    surface = AppSurface,
    onPrimary = AppSurface,
    onBackground = SecondaryText,
    onSurface = SecondaryText
)

@Composable
fun WebvaultTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
