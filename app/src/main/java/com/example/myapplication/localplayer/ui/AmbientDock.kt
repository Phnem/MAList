package com.example.myapplication.localplayer.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Tracks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Тон подложки и цвет содержимого для верхнего и нижнего доков — раздельно, потому что верх и низ
 * кадра почти всегда разной яркости (небо/титры сверху, тёмный передний план снизу).
 */
@Immutable
data class PlayerAmbient(
    val topTint: Color = NEUTRAL_TINT,
    val bottomTint: Color = NEUTRAL_TINT,
    val topContent: Color = Color.White,
    val bottomContent: Color = Color.White,
) {
    companion object {
        /** Статичный тон: защищённый (DRM) поток пикселей не отдаёт — см. [rememberPlayerAmbient]. */
        val Neutral = PlayerAmbient()
    }
}

private val NEUTRAL_TINT = Color(0xFF101010)
private val DARK_GLASS = Color(0xFF0E0E0E)
private val LIGHT_GLASS = Color(0xFFEFEFEF)
private val DARK_CONTENT = Color(0xFF101010)

/**
 * Доки подхватывают тон сцены под собой — без live-blur и без имитации объёма.
 *
 * Раз в ~секунду снимаем уже отрисованный кадр прямо с [SurfaceView] плеера через [PixelCopy]
 * (сразу уменьшая до 192×108) и считаем СРЕДНИЙ цвет ровно тех полос кадра, где лежат доки.
 * Среднее по региону — не [androidx.palette] с его k-means квантованием: для ~192×20 пикселей это
 * лишние такты без визуальной разницы.
 *
 * Из среднего берём перцептивную яркость (0.299R + 0.587G + 0.114B) и решаем, светлая под доком
 * сцена или тёмная → тон подложки и цвет иконок. Порог с гистерезисом (см. [contentIsLight]), иначе
 * на границе картинка мигала бы туда-сюда; переход цвета дополнительно сглажен анимацией.
 *
 * PixelCopy читает буфер SurfaceView, а не композицию экрана, поэтому источник кадра (локальный
 * файл или сетевой HLS/DASH) роли не играет — важно только, защищённый ли это поток. Решение
 * принимается ОДИН раз на входе через [adaptive] (см. [Tracks.isDrmProtected]): на protected
 * surface secure video path гарантированно не отдаёт пиксели, и дёргать PixelCopy каждую секунду,
 * чтобы каждый раз получить чёрный кадр, бессмысленно.
 *
 * Сэмплим только пока [active] — то есть пока контролы на экране.
 */
@Composable
fun rememberPlayerAmbient(
    surfaceProvider: () -> SurfaceView?,
    adaptive: Boolean,
    active: Boolean,
): PlayerAmbient {
    var top by remember { mutableStateOf(NEUTRAL_TINT) }
    var bottom by remember { mutableStateOf(NEUTRAL_TINT) }
    var topLight by remember { mutableStateOf(true) }
    var bottomLight by remember { mutableStateOf(true) }

    // Гейт по видимости — не пересоздание сэмплера: у него свой HandlerThread и переиспользуемый
    // bitmap, поднимать их на каждый показ контролов незачем. Пока панель скрыта — просто не читаем.
    val gate by rememberUpdatedState(active)
    val surface by rememberUpdatedState(surfaceProvider)
    LaunchedEffect(adaptive) {
        if (!adaptive) return@LaunchedEffect
        val sampler = SurfaceAmbientSampler()
        try {
            var misses = 0
            while (isActive) {
                if (!gate) {
                    delay(IDLE_POLL_MS)
                    continue
                }
                val sample = surface()?.let { sampler.sample(it) }
                if (sample == null) {
                    // Штатные промахи: сюрфейс ещё не отрисован после старта/поворота. Это НЕ
                    // детектор DRM (тот отработал на входе) — просто предохранитель, чтобы при
                    // systematically мёртвом сюрфейсе не крутить цикл до конца сессии.
                    if (++misses >= MAX_CONSECUTIVE_MISSES) {
                        Log.i(TAG, "Ambient sampling off: $misses misses in a row")
                        return@LaunchedEffect
                    }
                } else {
                    misses = 0
                    top = sample.top
                    bottom = sample.bottom
                    topLight = contentIsLight(sample.top.perceptualLuminance(), topLight)
                    bottomLight = contentIsLight(sample.bottom.perceptualLuminance(), bottomLight)
                }
                delay(SAMPLE_INTERVAL_MS)
            }
        } finally {
            sampler.release()
        }
    }

    // Небольшая подмешка среднего цвета в базовый тон — док «подхватывает» сцену.
    val topTint by animateColorAsState(
        lerp(if (topLight) DARK_GLASS else LIGHT_GLASS, top, SCENE_MIX),
        tween(TINT_ANIM_MS),
        label = "ambientTopTint",
    )
    val bottomTint by animateColorAsState(
        lerp(if (bottomLight) DARK_GLASS else LIGHT_GLASS, bottom, SCENE_MIX),
        tween(TINT_ANIM_MS),
        label = "ambientBottomTint",
    )
    val topContent by animateColorAsState(
        if (topLight) Color.White else DARK_CONTENT,
        tween(CONTENT_ANIM_MS),
        label = "ambientTopContent",
    )
    val bottomContent by animateColorAsState(
        if (bottomLight) Color.White else DARK_CONTENT,
        tween(CONTENT_ANIM_MS),
        label = "ambientBottomContent",
    )

    return if (adaptive) {
        PlayerAmbient(topTint, bottomTint, topContent, bottomContent)
    } else {
        PlayerAmbient.Neutral
    }
}

/**
 * Есть ли в дорожках защищённый контент. Защищённый поток рисуется в protected surface, откуда
 * secure video path по контракту не отдаёт пиксели никакому [PixelCopy] — это не глюк прошивки,
 * а гарантия DRM. Поэтому адаптив выключается один раз здесь, а не ловится промахами в рантайме.
 */
fun Tracks.isDrmProtected(): Boolean = groups.any { group ->
    (0 until group.length).any { group.getTrackFormat(it).drmInitData != null }
}

private const val TAG = "PlayerAmbient"
private const val SAMPLE_INTERVAL_MS = 1000L
private const val IDLE_POLL_MS = 250L
private const val MAX_CONSECUTIVE_MISSES = 12
private const val TINT_ANIM_MS = 600
private const val CONTENT_ANIM_MS = 350
private const val SCENE_MIX = 0.13f

/** Ниже — светлые иконки, выше — тёмные, между — оставляем как было (антимигание). */
private const val LIGHT_CONTENT_BELOW = 0.40f
private const val DARK_CONTENT_ABOVE = 0.55f

private fun contentIsLight(luminance: Float, current: Boolean): Boolean = when {
    luminance < LIGHT_CONTENT_BELOW -> true
    luminance > DARK_CONTENT_ABOVE -> false
    else -> current
}

private fun Color.perceptualLuminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

private class AmbientSample(val top: Color, val bottom: Color)

/**
 * Снимает кадр с [SurfaceView] плеера. Bitmap и поток колбэка создаются один раз на сессию:
 * PixelCopy сам масштабирует сюрфейс в целевой bitmap, так что копия сразу приходит уменьшенной.
 */
private class SurfaceAmbientSampler {
    private val thread = HandlerThread("player-ambient").apply { start() }
    private val handler = Handler(thread.looper)
    private val bitmap = Bitmap.createBitmap(SAMPLE_W, SAMPLE_H, Bitmap.Config.ARGB_8888)
    private val pixels = IntArray(SAMPLE_W * SAMPLE_H)

    suspend fun sample(view: SurfaceView): AmbientSample? {
        if (!view.holder.surface.isValid || view.width <= 0 || view.height <= 0) return null
        val copied = suspendCancellableCoroutine { cont ->
            try {
                PixelCopy.request(view, bitmap, { result ->
                    cont.resume(result == PixelCopy.SUCCESS)
                }, handler)
            } catch (e: IllegalArgumentException) {
                // Сюрфейс исчез между проверкой и запросом.
                cont.resume(false)
            }
        }
        if (!copied) return null
        return withContext(Dispatchers.Default) {
            bitmap.getPixels(pixels, 0, SAMPLE_W, 0, 0, SAMPLE_W, SAMPLE_H)
            AmbientSample(
                top = averageBand(0f, TOP_DOCK_BAND),
                bottom = averageBand(BOTTOM_DOCK_BAND, 1f),
            )
        }
    }

    /** Среднее RGB по горизонтальной полосе кадра [fromY]..[toY] (доли высоты). */
    private fun averageBand(fromY: Float, toY: Float): Color {
        val y0 = (SAMPLE_H * fromY).toInt().coerceIn(0, SAMPLE_H - 1)
        val y1 = (SAMPLE_H * toY).toInt().coerceIn(y0 + 1, SAMPLE_H)
        var r = 0L
        var g = 0L
        var b = 0L
        for (i in y0 * SAMPLE_W until y1 * SAMPLE_W) {
            val p = pixels[i]
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
        }
        val n = ((y1 - y0) * SAMPLE_W).coerceAtLeast(1)
        return Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    fun release() {
        // Bitmap НЕ recycle: отменённый suspend не отменяет сам PixelCopy, и запрос в полёте
        // допишет кадр уже после релиза — по recycled-битмапу это падение в нативе. 83 КБ соберёт GC.
        thread.quitSafely()
    }

    private companion object {
        const val SAMPLE_W = 192
        const val SAMPLE_H = 108
        /** Доли кадра, которые реально закрыты доками. */
        const val TOP_DOCK_BAND = 0.18f
        const val BOTTOM_DOCK_BAND = 0.80f
    }
}

/**
 * Плоская подложка дока. Никакого объёма: ни бликов, ни градиентов, ни фаски по краю — только
 * ровная заливка тоном [tint] из [rememberPlayerAmbient] и хайрлайн, отделяющий док от кадра.
 *
 * Заливка намеренно слабая — док должен читаться как лёгкая вуаль над видео, а не как панель.
 * Светлый тон кроем чуть плотнее: под ним лежит затемняющий градиент контролов, и на совсем
 * прозрачном светлом фоне тёмные иконки теряли бы контраст.
 */
fun Modifier.ambientDockSurface(
    shape: Shape,
    tint: Color,
): Modifier = this
    .clip(shape)
    .drawBehind {
        val alpha = if (tint.perceptualLuminance() > 0.5f) LIGHT_FILL_ALPHA else DARK_FILL_ALPHA
        drawRect(tint.copy(alpha = alpha))
    }
    .border(width = 1.dp, color = Color.White.copy(alpha = 0.08f), shape = shape)

private const val DARK_FILL_ALPHA = 0.30f
private const val LIGHT_FILL_ALPHA = 0.40f
