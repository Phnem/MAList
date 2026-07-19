package com.example.myapplication.ui.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.shared.theme.AsgrikeFamily
import com.example.myapplication.ui.shared.theme.BrandOrange
import com.example.myapplication.ui.shared.theme.SnProFamily

private val Cream = Color(0xFFF5F0E8)
private val CollectionOrange = BrandOrange.copy(alpha = 0.82f)

/**
 * Lockup «Vetro / COLLECTION».
 * «Vetro» — шрифт Asgrike; подпись — широкий трекинг.
 */
@Composable
fun VetroWordmark(
    modifier: Modifier = Modifier,
    cream: Color = Cream,
    collectionColor: Color = CollectionOrange,
) {
    Column(
        modifier = modifier.fillMaxWidth(0.92f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Vetro",
            color = cream,
            fontSize = 96.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = AsgrikeFamily,
            letterSpacing = (-1.5).sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = "COLLECTION",
            color = collectionColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = SnProFamily,
            letterSpacing = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
