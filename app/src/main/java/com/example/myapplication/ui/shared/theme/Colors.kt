package com.example.myapplication.ui.shared.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

val BrandBlue = Color(0xFF007AFF)
val BrandBlueSoft = Color(0xFF5AC8FA)
val BrandRed = Color(0xFFC62828)

/** Акценты плиток настроек */
/** Язык — яркий голубой акцент для слайдера */
val SettingsAccentLangDarkBlue = Color(0xFF29B6F6)
/** Тема — зелёный акцент слайдера */
val SettingsAccentThemeDarkGreen = Color(0xFF34C85A)
val SettingsAccentCloudLightBlue = Color(0xFF81D4FA)
/** Контент — приглушённый оранжевый (не неон как #FF9500) */
val SettingsAccentContentOrange = Color(0xFFE65100)
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
/** Нейтральная «чистая» светлая палитра (без тёплого крема). */
val LightBackground = Color(0xFFF7F7F8)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF0F0F3)
val LightTextPrimary = Color(0xFF1C1C1E)
val LightTextSecondary = Color(0xFF636366)
val LightBorder = Color(0xFFE0E0E6)

/** Пастельная подложка иконки + tint с достаточным контрастом на светлой теме. */
data class IconWellColors(
    val background: Color,
    val tint: Color,
)

private fun Color.relativeLuminance(): Float {
    fun linearize(c: Float): Float =
        if (c <= 0.04045f) c / 12.92f
        else StrictMath.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    val r = linearize(red)
    val g = linearize(green)
    val b = linearize(blue)
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

fun iconWellColorsLight(accent: Color): IconWellColors {
    val background = lerp(Color.White, accent, 0.18f)
    val lum = accent.relativeLuminance()
    val tint = when {
        lum > 0.62f -> lerp(accent, Color(0xFF1A1A1C), 0.62f)
        lum > 0.38f -> lerp(accent, Color(0xFF1A1A1C), 0.42f)
        else -> lerp(accent, Color(0xFF1A1A1C), 0.22f)
    }
    return IconWellColors(background = background, tint = tint)
}

fun settingsIconWellColors(isDark: Boolean, accent: Color): IconWellColors =
    if (isDark) {
        IconWellColors(background = OverlayThemeTokens.TileIconBgDark, tint = accent)
    } else {
        iconWellColorsLight(accent)
    }

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
