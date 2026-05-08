package com.example.myapplication.ui.shared.theme

import androidx.compose.ui.graphics.Color

val BrandBlue = Color(0xFF007AFF)
val BrandBlueSoft = Color(0xFF5AC8FA)
val BrandRed = Color(0xFFC62828)

/** Акценты плиток настроек */
val SettingsAccentLangDarkBlue = Color(0xFF1565C0)
val SettingsAccentThemeDarkGreen = Color(0xFF21B1A0)
val SettingsAccentCloudLightBlue = Color(0xFF81D4FA)
val SettingsAccentContentOrange = Color(0xFFFF9500)
val SettingsAccentContactLightGreen = Color(0xFF81C784)
/** Акцент плитки доната (кофейный / тёплый) */
val SettingsAccentDonationCoffee = Color(0xFF8D6E63)

// M3 Accent — серо-фиолетовый (mauve), как у FilledTonalButton / secondaryContainer
val AccentMauveDark = Color(0xFF4A4458)
val AccentOnMauveDark = Color(0xFFE8DEF8)
val AccentMauveLight = Color(0xFFE8DEF8)
val AccentOnMauveLight = Color(0xFF1D192B)

// Updated Rating Colors
val RateColor1 = Color(0xFFFF3B30) // Red
val RateColor2 = Color(0xFFFF6D00) // Red-Orange
val RateColor3 = Color(0xFFFF9800) // Orange
val RateColor4 = Color(0xFFFFD600) // Yellow
val RateColor5 = Color(0xFF34C759) // Green

val RateColorEmpty = Color(0xFFE0E0E0)
val EpisodesColor = Color(0xFF2196F3)
val TimeColor = Color(0xFF9C27B0)
val RatingColor = Color(0xFFFFC400)
val RankColor = Color(0xFF43A047)

val DarkBackground = Color(0xFF0D1117)
val DarkSurface = Color(0xFF2D333B)
val DarkSurfaceVariant = Color(0xFF30363D)
val DarkTextPrimary = Color(0xFFF2F2F7)
val DarkTextSecondary = Color(0xFF9898A0)
val DarkBorder = Color(0xFFFFFFFF).copy(alpha = 0.08f)
val LightBackground = Color(0xFFF4EBDD)
val LightSurface = Color(0xFFFFF9F0)
val LightSurfaceVariant = Color(0xFFEFE4D3)
val LightTextPrimary = Color(0xFF1C1C1E)
val LightTextSecondary = Color(0xFF636366)
val LightBorder = Color(0xFFE6D8C5)

fun getRatingColor(rating: Int): Color {
    return when (rating) {
        1 -> RateColor1
        2 -> RateColor2
        3 -> RateColor3
        4 -> RateColor4
        5 -> RateColor5
        else -> Color.Gray
    }
}
