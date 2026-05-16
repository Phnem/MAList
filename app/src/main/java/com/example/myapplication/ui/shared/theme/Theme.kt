package com.example.myapplication.ui.shared.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.phnem.vetro.R

val SnProFamily = FontFamily(
    Font(R.font.snpro_bold, FontWeight.Bold),
    Font(R.font.snpro_mediumitalic, FontWeight.Normal),
    Font(R.font.snpro_mediumitalic, FontWeight.Medium),
    Font(R.font.snpro_lightitalic, FontWeight.Light)
)

@Composable
fun OneUiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        darkColorScheme(
            background = DarkBackground,
            surface = DarkSurface,
            surfaceVariant = DarkSurfaceVariant,
            primary = AccentMauveDark,
            onPrimary = AccentOnMauveDark,
            onBackground = DarkTextPrimary,
            onSurface = DarkTextPrimary,
            secondary = DarkTextSecondary,
            outline = DarkBorder,
            error = BrandRed,
            surfaceContainer = DarkSurfaceVariant
        )
    } else {
        lightColorScheme(
            background = LightBackground,
            surface = LightSurface,
            surfaceVariant = LightSurfaceVariant,
            primary = BrandRed,
            onPrimary = Color.White,
            onBackground = LightTextPrimary,
            onSurface = LightTextPrimary,
            onSurfaceVariant = LightTextSecondary,
            secondary = LightTextSecondary,
            secondaryContainer = BrandRed.copy(alpha = 0.12f),
            onSecondaryContainer = BrandRed,
            outline = LightBorder,
            error = BrandRed,
            surfaceContainer = LightSurface,
            surfaceContainerLow = LightSurface,
            surfaceContainerHigh = LightSurfaceVariant,
            surfaceContainerHighest = LightSurfaceVariant
        )
    }

    val rippleConfiguration = if (darkTheme) {
        RippleConfiguration(color = Color.White.copy(alpha = 0.14f))
    } else {
        RippleConfiguration(color = colors.primary.copy(alpha = 0.10f))
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            displayLarge = MaterialTheme.typography.displayLarge.copy(fontFamily = SnProFamily),
            displayMedium = MaterialTheme.typography.displayMedium.copy(fontFamily = SnProFamily),
            displaySmall = MaterialTheme.typography.displaySmall.copy(fontFamily = SnProFamily),
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontFamily = SnProFamily),
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontFamily = SnProFamily),
            headlineSmall = MaterialTheme.typography.headlineSmall.copy(fontFamily = SnProFamily),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontFamily = SnProFamily),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontFamily = SnProFamily),
            titleSmall = MaterialTheme.typography.titleSmall.copy(fontFamily = SnProFamily),
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = SnProFamily),
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontFamily = SnProFamily),
            bodySmall = MaterialTheme.typography.bodySmall.copy(fontFamily = SnProFamily),
            labelLarge = MaterialTheme.typography.labelLarge.copy(fontFamily = SnProFamily),
            labelMedium = MaterialTheme.typography.labelMedium.copy(fontFamily = SnProFamily),
            labelSmall = MaterialTheme.typography.labelSmall.copy(fontFamily = SnProFamily)
        )
    ) {
        CompositionLocalProvider(LocalRippleConfiguration provides rippleConfiguration) {
            content()
        }
    }
}
