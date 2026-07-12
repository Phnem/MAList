package com.example.myapplication.ui.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.TransformOrigin
import com.example.myapplication.ui.shared.theme.MotionTokens

/**
 * Моушн модальных листов настроек (гайдбук §7 «bottom menu из элемента» / §10 scrim).
 * Вход — [MotionTokens.sheetPresent] (380/0.86, отзывчивое появление); выход — короткий tween,
 * чтобы закрытие ощущалось быстрее открытия. Scrim — строго `tween(250)` синхронно с контентом (§3.2).
 */
object SettingsOverlayMotion {
    val scrimFadeIn = fadeIn(tween(MotionTokens.ScrimFadeMillis, easing = FastOutSlowInEasing))
    val scrimFadeOut = fadeOut(tween(MotionTokens.ScrimFadeMillis, easing = FastOutSlowInEasing))

    fun panelFadeInScaleIn(): EnterTransition =
        fadeIn(tween(200, easing = FastOutSlowInEasing)) +
            scaleIn(
                initialScale = 0.94f,
                transformOrigin = TransformOrigin.Center,
                animationSpec = MotionTokens.sheetPresent(),
            )

    fun panelFadeOutScaleOut(): ExitTransition =
        fadeOut(tween(150, easing = FastOutSlowInEasing)) +
            scaleOut(
                targetScale = 0.94f,
                transformOrigin = TransformOrigin.Center,
                animationSpec = MotionTokens.dialogExit(),
            )
}
