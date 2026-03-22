package com.example.myapplication.ui.shared.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val InspectVisualSearchPrimary = Color(0xFF3B99FF)

fun inspectVisualSearchDarkColorScheme() = darkColorScheme(
    primary = InspectVisualSearchPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E3A55),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF98989E),
    onSecondary = Color.White,
    background = Color(0xFF050508),
    onBackground = Color.White,
    surface = Color(0xFF151820),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF252A33),
    onSurfaceVariant = Color(0xFFC4C4CC),
    outline = Color.White.copy(alpha = 0.28f),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF1A0000)
)

@Composable
fun InspectVisualSearchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = inspectVisualSearchDarkColorScheme(),
        typography = inspectVisualSearchTypography(),
        content = content
    )
}
