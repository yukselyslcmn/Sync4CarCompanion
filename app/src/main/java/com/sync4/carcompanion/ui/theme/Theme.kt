package com.sync4.carcompanion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Ford SYNC 4 koyu kokpit paletinden esinlenen renk şeması
private val Sync4DarkColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF062038),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF60A5FA),
    onSecondary = Color(0xFF08224A),
    secondaryContainer = Color(0xFF1E3A5F),
    onSecondaryContainer = Color(0xFFDBEAFE),
    tertiary = Color(0xFF34D399),
    background = Color(0xFF08111F),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0F1B2D),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF192942),
    onSurfaceVariant = Color(0xFF94A3B8),
    error = Color(0xFFF87171)
)

private val Sync4LightColors = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF082F49),
    secondary = Color(0xFF1D4ED8),
    onSecondary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569)
)

@Composable
fun Sync4CarCompanionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) Sync4DarkColors else Sync4LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
