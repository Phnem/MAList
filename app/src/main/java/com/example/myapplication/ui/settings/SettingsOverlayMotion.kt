package com.example.myapplication.ui.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.TransformOrigin

/** Motion tokens for settings sheets & global startup update overlay (plan §7). */
object SettingsOverlayMotion {
    /** Scrim + panel share similar timing for a coherent overlay. */
    const val FadeInMillis: Int = 300
    const val FadeOutMillis: Int = 280

    val scrimFadeIn = fadeIn(
        tween(FadeInMillis, easing = FastOutSlowInEasing),
    )

    val scrimFadeOut = fadeOut(
        tween(FadeOutMillis, easing = FastOutSlowInEasing),
    )

    fun panelFadeInScaleIn(): EnterTransition =
        fadeIn(
            tween(FadeInMillis, easing = FastOutSlowInEasing),
        ) + scaleIn(
            initialScale = 0.96f,
            transformOrigin = TransformOrigin.Center,
            animationSpec = spring(
                dampingRatio = 0.88f,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )

    fun panelFadeOutScaleOut(): ExitTransition =
        fadeOut(
            tween(FadeOutMillis, easing = FastOutSlowInEasing),
        ) + scaleOut(
            targetScale = 0.96f,
            transformOrigin = TransformOrigin.Center,
            animationSpec = spring(
                dampingRatio = 1f,
                stiffness = Spring.StiffnessMedium,
            ),
        )
}
