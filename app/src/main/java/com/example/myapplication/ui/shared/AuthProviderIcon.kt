package com.example.myapplication.ui.shared

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.phnem.vetro.R

@Composable
fun AuthProviderIcon(
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    invertForTheme: Boolean = iconRes == R.drawable.ic_github,
    isDarkTheme: Boolean,
) {
    Image(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = modifier,
        colorFilter = if (invertForTheme) {
            ColorFilter.tint(if (isDarkTheme) Color.White else Color.Black)
        } else {
            null
        },
    )
}
