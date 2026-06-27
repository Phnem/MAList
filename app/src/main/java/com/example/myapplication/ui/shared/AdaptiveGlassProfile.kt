package com.example.myapplication.ui.shared

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myapplication.isAppInDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/** Dev toggle: adaptive glass on scroll (default on). Off = always full-quality glass. */
val LocalAdaptiveGlassEnabled = compositionLocalOf { true }

/** True while the main collection list is scrolling (drag or fling). */
val LocalAdaptiveGlassScrollInProgress = compositionLocalOf { false }

enum class GlassPreset(
    val fullBlur: Dp,
    val reducedBlur: Dp,
    val lensRefraction: Dp,
    val lensDepth: Dp,
) {
    CompactNav(fullBlur = 16.dp, reducedBlur = 4.dp, lensRefraction = 10.dp, lensDepth = 44.dp),
    Card(fullBlur = 12.dp, reducedBlur = 4.dp, lensRefraction = 8.dp, lensDepth = 40.dp),
    IconButton(fullBlur = 28.dp, reducedBlur = 6.dp, lensRefraction = 16.dp, lensDepth = 48.dp),
}

private const val GLASS_REDUCE_DURATION_MS = 150
private const val GLASS_RESTORE_DURATION_MS = 340

@Composable
private fun rememberGlassFloatAnimation(
    targetValue: Float,
    restoringToFull: Boolean,
    label: String,
): Float {
    val animated by animateFloatAsState(
        targetValue = targetValue,
        animationSpec = if (restoringToFull) {
            tween(
                durationMillis = GLASS_RESTORE_DURATION_MS,
                easing = FastOutSlowInEasing,
            )
        } else {
            tween(durationMillis = GLASS_REDUCE_DURATION_MS)
        },
        label = label,
    )
    return animated
}

data class AdaptiveGlassEffects(
    val blur: Dp,
    val lensRefraction: Dp,
    val lensDepth: Dp,
    val scrimAlpha: Float,
)

@Composable
fun rememberAdaptiveGlassEffects(preset: GlassPreset): AdaptiveGlassEffects {
    val adaptiveEnabled = LocalAdaptiveGlassEnabled.current
    val scrollInProgress = LocalAdaptiveGlassScrollInProgress.current
    val isDark = isAppInDarkTheme()

    val useReduced = adaptiveEnabled && scrollInProgress
    val restoringToFull = adaptiveEnabled && !scrollInProgress

    val targetBlur = if (useReduced) preset.reducedBlur else preset.fullBlur
    val animatedBlur = rememberGlassFloatAnimation(
        targetValue = targetBlur.value,
        restoringToFull = restoringToFull,
        label = "glassBlur",
    )

    val targetLensRefraction = if (useReduced) 0f else preset.lensRefraction.value
    val animatedLensRefraction = rememberGlassFloatAnimation(
        targetValue = targetLensRefraction,
        restoringToFull = restoringToFull,
        label = "glassLensRefraction",
    )

    val targetLensDepth = if (useReduced) 0f else preset.lensDepth.value
    val animatedLensDepth = rememberGlassFloatAnimation(
        targetValue = targetLensDepth,
        restoringToFull = restoringToFull,
        label = "glassLensDepth",
    )

    val reducedScrim = if (isDark) 0.14f else 0.10f
    val targetScrim = if (useReduced) reducedScrim else 0f
    val animatedScrim = rememberGlassFloatAnimation(
        targetValue = targetScrim,
        restoringToFull = restoringToFull,
        label = "glassScrim",
    )

    return AdaptiveGlassEffects(
        blur = animatedBlur.dp,
        lensRefraction = animatedLensRefraction.dp,
        lensDepth = animatedLensDepth.dp,
        scrimAlpha = animatedScrim,
    )
}

@Composable
fun Modifier.adaptiveGlassBackdrop(
    backdrop: Backdrop,
    shape: Shape,
    effects: AdaptiveGlassEffects,
): Modifier {
    val density = LocalDensity.current
    val blurPx = with(density) { effects.blur.toPx() }
    val lensRefractionPx = with(density) { effects.lensRefraction.toPx() }
    val lensDepthPx = with(density) { effects.lensDepth.toPx() }
    val scrimColor = if (effects.scrimAlpha > 0f) {
        Color.Black.copy(alpha = effects.scrimAlpha)
    } else {
        Color.Unspecified
    }
    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(blurPx)
                if (lensRefractionPx > 0.5f && lensDepthPx > 0.5f) {
                    lens(lensRefractionPx, lensDepthPx)
                }
            },
        )
        .then(
            if (scrimColor != Color.Unspecified) {
                Modifier.background(scrimColor, shape)
            } else {
                Modifier
            },
        )
}
