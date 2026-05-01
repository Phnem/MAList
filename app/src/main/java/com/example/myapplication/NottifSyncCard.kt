package com.example.myapplication

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Круглая кнопка обновления с бесконечным вращением иконки во время checkUpdates. */
@Composable
internal fun NottifRefreshButton(
    isDark: Boolean,
    accent: Color,
    isRotating: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 44.dp,
    iconSize: Dp = 22.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nottif-refresh")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinChk"
    )
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(buttonSize)
            .clip(CircleShape)
            .background(nottifOverlayCircleControlBg(isDark))
            .border(1.dp, accent.copy(alpha = if (isDark) 0.35f else 0.45f), CircleShape)
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = contentDescription,
            tint = accent,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer { if (isRotating) rotationZ = angle }
        )
    }
}
