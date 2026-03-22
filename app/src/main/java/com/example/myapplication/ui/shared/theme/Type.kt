package com.example.myapplication.ui.shared.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Типографика экрана Visual Search: единый шрифт Sn Pro (как у «Visual Search» / «VETRO»).
 */
fun inspectVisualSearchTypography(): Typography {
    val default = Typography()
    return default.copy(
        headlineLarge = default.headlineLarge.copy(
            fontFamily = SnProFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 40.sp
        ),
        headlineMedium = default.headlineMedium.copy(
            fontFamily = SnProFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            lineHeight = 22.sp
        ),
        titleMedium = default.titleMedium.copy(
            fontFamily = SnProFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        ),
        titleSmall = default.titleSmall.copy(
            fontFamily = SnProFamily,
            fontWeight = FontWeight.SemiBold
        ),
        bodyLarge = default.bodyLarge.copy(
            fontFamily = SnProFamily
        ),
        labelSmall = default.labelSmall.copy(
            fontFamily = SnProFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            letterSpacing = 1.8.sp
        )
    )
}
