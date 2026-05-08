package com.example.myapplication

import androidx.annotation.RawRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlin.math.max

@Composable
internal fun NottifRefreshButton(
    isDark: Boolean,
    accent: Color,
    isRotating: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    @RawRes lottieRawRes: Int? = null,
    /**
     * При >1 слегка приближаем центр композиции, подрезая толстый прозрачный кадр из JSON.
     * Слишком большие значения на маленьком [buttonSize] дают только фрагмент дуги — держать ~1.5–2.5.
     */
    lottieVisualScale: Float = 1f
) {
    val buttonSurface = modifier
        .size(buttonSize)
        .clip(CircleShape)
        .background(nottifOverlayCircleControlBg(isDark))
        .border(1.dp, accent.copy(alpha = if (isDark) 0.35f else 0.45f), CircleShape)

    if (lottieRawRes != null) {
        val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(lottieRawRes))
        val lottieProgress by animateLottieCompositionAsState(
            composition = composition,
            iterations = LottieConstants.IterateForever
        )
        val tapSide = max(buttonSize.value, 48f).dp
        Box(
            modifier = modifier
                .size(tapSide)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val scale = maxOf(lottieVisualScale, 1f)
                LottieAnimation(
                    composition = composition,
                    progress = { lottieProgress },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin.Center
                        },
                    alignment = Alignment.Center,
                    contentScale = ContentScale.Fit
                )
            }
        }
    } else {
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
            modifier = buttonSurface
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
}
