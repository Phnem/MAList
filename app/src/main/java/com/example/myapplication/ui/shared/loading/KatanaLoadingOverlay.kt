package com.example.myapplication.ui.shared.loading

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.shared.theme.MotionTokens

/** Доля меньшей стороны, которую занимает риг катаны. */
private const val LOADER_SIZE_FRACTION = 0.66f

/** Дальше растягивать анимацию незачем: на планшете она стала бы плакатом. */
private val LOADER_MAX_SIZE = 320.dp

private const val SCRIM_ALPHA = 0.82f

/**
 * Полноэкранное ожидание: затемнение и катана, очерчивающая круг.
 *
 * Оверлей **не размывает фон сам**. У обычного Compose-контента это `Modifier.blur` на самом
 * контенте, у видео — замороженный снимок кадра (`SurfaceView` Compose не видит). Механизмы
 * разные, поэтому фон готовит вызывающая сторона, а оверлей одинаков для обоих.
 *
 * Показывается сразу, без задержки: решение пользователя — быстрые загрузки лучше моргнут, чем
 * заставят гадать, случилось ли что-нибудь.
 *
 * @param visible идёт ли ожидание. Держите оверлей в композиции постоянно и переключайте флаг —
 *   тогда появление и исчезновение будут плавными.
 */
@Composable
fun KatanaLoadingOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(MotionTokens.scrimFade()),
        exit = fadeOut(MotionTokens.dialogExit()),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                .consumeGestures(),
            contentAlignment = Alignment.Center,
        ) {
            val side = (minOf(maxWidth, maxHeight) * LOADER_SIZE_FRACTION)
                .coerceAtMost(LOADER_MAX_SIZE)
            KatanaLoader(modifier = Modifier.size(side))
        }
    }
}

/**
 * Гасит все жесты: пока экран занят ожиданием, нижний слой не должен ловить нажатия.
 * Именно все события, а не только тапы, — иначе сквозь оверлей проходили бы скролл и щипок.
 */
private fun Modifier.consumeGestures(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
        }
    }
}
