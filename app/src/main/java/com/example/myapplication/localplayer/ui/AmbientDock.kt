package com.example.myapplication.localplayer.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
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
 *
 * Кроме тона несёт [DockBackdrop] — размытую текстуру той же полосы кадра. `null` там, где кадра
 * нет (DRM) или он ещё не снят: док тогда рисуется ровно как раньше, плоской заливкой.
 */
@Immutable
data class PlayerAmbient(
    val topTint: Color = NEUTRAL_TINT,
    val bottomTint: Color = NEUTRAL_TINT,
    val topContent: Color = Color.White,
    val bottomContent: Color = Color.White,
    val topBackdrop: DockBackdrop? = null,
    val bottomBackdrop: DockBackdrop? = null,
) {
    companion object {
        /** Статичный тон: защищённый (DRM) поток пикселей не отдаёт — см. [rememberPlayerAmbient]. */
        val Neutral = PlayerAmbient()
    }
}

/**
 * Размытая полоса кадра под доком — фон вместо плоской заливки.
 *
 * Хранит ДВЕ картинки, а не одну: кадр снимается раз в секунду, и подмена текстуры встык на склейке
 * плана читалась бы как мигание. [fade] ведёт кроссфейд со старой на новую в том же темпе, что
 * `animateColorAsState` ведёт тон, — иначе тон и текстура разъезжались бы во времени.
 *
 * [fade] намеренно [State], а не `Float`: он меняется каждый кадр анимации, и чтение его ВНУТРИ
 * `drawBehind` инвалидирует только отрисовку. Будь это обычное поле, док рекомпозился бы 60 раз в
 * секунду после каждого снимка.
 */
@Immutable
class DockBackdrop(
    val previous: ImageBitmap?,
    val current: ImageBitmap?,
    private val blend: State<Float>,
) {
    val fade: Float get() = blend.value
}

private val NEUTRAL_TINT = Color(0xFF101010)
private val DARK_GLASS = Color(0xFF0E0E0E)
private val LIGHT_GLASS = Color(0xFFEFEFEF)
private val DARK_CONTENT = Color(0xFF101010)

/**
 * Доки подхватывают сцену под собой — размытым снимком кадра, но без live-blur и без имитации
 * объёма.
 *
 * Раз в ~секунду снимаем уже отрисованный кадр прямо с [SurfaceView] плеера через [PixelCopy]
 * (сразу уменьшая до 192×108) и берём из него ДВА результата по каждой полосе, где лежат доки:
 * средний цвет (тон и решение «светлая сцена или тёмная») и размытую копию самой полосы — фон
 * дока. Второе достаётся почти даром: байты уже в памяти на момент подсчёта среднего, и раньше
 * картинка просто выбрасывалась ради одного числа.
 *
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

    // Текстуры доков + счётчик снимков: по нему заводится кроссфейд и пересобирается DockBackdrop.
    var topPrevTexture by remember { mutableStateOf<ImageBitmap?>(null) }
    var topTexture by remember { mutableStateOf<ImageBitmap?>(null) }
    var bottomPrevTexture by remember { mutableStateOf<ImageBitmap?>(null) }
    var bottomTexture by remember { mutableStateOf<ImageBitmap?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    val fade = remember { Animatable(1f) }

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
                    topPrevTexture = topTexture
                    bottomPrevTexture = bottomTexture
                    topTexture = sample.topBand
                    bottomTexture = sample.bottomBand
                    generation++
                }
                delay(SAMPLE_INTERVAL_MS)
            }
        } finally {
            sampler.release()
        }
    }

    LaunchedEffect(generation) {
        if (generation == 0) return@LaunchedEffect
        fade.snapTo(0f)
        fade.animateTo(1f, tween(TINT_ANIM_MS))
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

    // Пересобирается только на новом снимке: DockBackdrop сравнивается по ссылке, и стабильная
    // ссылка держит модификатор дока от лишних пересозданий между кадрами кроссфейда.
    val topBackdrop = remember(generation) {
        topTexture?.let { DockBackdrop(topPrevTexture, it, fade.asState()) }
    }
    val bottomBackdrop = remember(generation) {
        bottomTexture?.let { DockBackdrop(bottomPrevTexture, it, fade.asState()) }
    }

    return if (adaptive) {
        PlayerAmbient(topTint, bottomTint, topContent, bottomContent, topBackdrop, bottomBackdrop)
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

private class AmbientSample(
    val top: Color,
    val bottom: Color,
    val topBand: ImageBitmap,
    val bottomBand: ImageBitmap,
)

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
                topBand = blurredBand(0f, TOP_DOCK_BAND),
                bottomBand = blurredBand(BOTTOM_DOCK_BAND, 1f),
            )
        }
    }

    /**
     * Размытая копия полосы кадра [fromY]..[toY]. Блюрится ОДИН статичный снимок 192×~20 раз в
     * секунду, а не сцена по кадру, поэтому обычная дороговизна блюра тут ни при чём: два прохода
     * box-blur по ~4 тысячам пикселей — доли миллисекунды.
     *
     * `RenderEffect.createBlurEffect` не годится: он требует API 31 (у проекта minSdk 26) и живёт
     * на RenderNode, а не на Bitmap — пришлось бы поднимать HardwareRenderer ради картинки, которую
     * дешевле размыть арифметикой.
     *
     * Битмап каждый раз НОВЫЙ, переиспользовать один нельзя: он уходит в композицию как текстура и
     * читается при отрисовке, а следующий снимок затирал бы его прямо под рукой у рендера.
     */
    private fun blurredBand(fromY: Float, toY: Float): ImageBitmap {
        val y0 = (SAMPLE_H * fromY).toInt().coerceIn(0, SAMPLE_H - 1)
        val y1 = (SAMPLE_H * toY).toInt().coerceIn(y0 + 1, SAMPLE_H)
        val h = y1 - y0
        val band = IntArray(SAMPLE_W * h)
        System.arraycopy(pixels, y0 * SAMPLE_W, band, 0, band.size)
        boxBlur(band, SAMPLE_W, h, BLUR_RADIUS, BLUR_PASSES)
        return Bitmap.createBitmap(SAMPLE_W, h, Bitmap.Config.ARGB_8888)
            .apply { setPixels(band, 0, SAMPLE_W, 0, 0, SAMPLE_W, h) }
            .asImageBitmap()
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
        /** Радиус в пикселях СНИМКА: полоса всего 192 точки шириной, больше и не нужно. */
        const val BLUR_RADIUS = 4
        /** Два прохода box-blur — уже неотличимо от гауссова на таком радиусе. */
        const val BLUR_PASSES = 2
    }
}

/**
 * Разделимый box-blur по месту: горизонтальный проход во временный буфер, вертикальный — обратно.
 * Альфа не трогаем вовсе — снимок PixelCopy всегда непрозрачный.
 */
private fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int) {
    if (radius <= 0 || width <= 1 || height <= 1) return
    val tmp = IntArray(pixels.size)
    repeat(passes) {
        blurAxis(pixels, tmp, width, height, radius, horizontal = true)
        blurAxis(tmp, pixels, width, height, radius, horizontal = false)
    }
}

private fun blurAxis(
    src: IntArray,
    dst: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontal: Boolean,
) {
    val lineCount = if (horizontal) height else width
    val lineLength = if (horizontal) width else height
    val step = if (horizontal) 1 else width
    val effectiveRadius = radius.coerceAtMost(lineLength - 1)

    for (line in 0 until lineCount) {
        val base = if (horizontal) line * width else line
        var r = 0
        var g = 0
        var b = 0
        var count = 0
        // Стартовое окно [0..radius]; дальше едем скользящим, добавляя правый край и снимая левый.
        for (i in 0..effectiveRadius) {
            val p = src[base + i * step]
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
            count++
        }
        for (i in 0 until lineLength) {
            dst[base + i * step] = (0xFF shl 24) or
                ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
            val outgoing = i - effectiveRadius
            val incoming = i + effectiveRadius + 1
            if (outgoing >= 0) {
                val p = src[base + outgoing * step]
                r -= (p shr 16) and 0xFF
                g -= (p shr 8) and 0xFF
                b -= p and 0xFF
                count--
            }
            if (incoming < lineLength) {
                val p = src[base + incoming * step]
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
                count++
            }
        }
    }
}

/**
 * Подложка дока: размытая полоса кадра под ним ([backdrop]) плюс тонирующая заливка сверху.
 * Никакого объёма — ни бликов, ни градиентов, ни фаски: разница с прежней версией именно в
 * текстуре и резкости фона, а не в имитации толщины стекла.
 *
 * Порядок слоёв важен: сначала текстура, поверх — [tint] прежней слабой альфой. Она и осталась
 * регулятором того, насколько кадр просвечивает; убирать её нельзя, иначе видео и текст дока
 * начнут спорить за контраст.
 *
 * Текстура рисуется растянутой на весь док с [FilterQuality.Low]: билинейный апскейл 192×~20 на
 * площадь дока сам по себе доразмывает картинку — здесь это плюс, а не компромисс. Она же и
 * полупрозрачная ([BACKDROP_ALPHA]) — снимок отстаёт на секунду, и живое видео, просвечивающее
 * из-под него, маскирует эту задержку.
 *
 * [backdrop] `== null` (DRM-поток, первые кадры) — рисуется ровно прежняя плоская заливка.
 */
fun Modifier.ambientDockSurface(
    shape: Shape,
    tint: Color,
    backdrop: DockBackdrop? = null,
): Modifier = this
    .clip(shape)
    .drawBehind {
        if (backdrop != null) {
            val blend = backdrop.fade
            backdrop.previous?.let { drawBackdrop(it, (1f - blend) * BACKDROP_ALPHA) }
            backdrop.current?.let { drawBackdrop(it, blend * BACKDROP_ALPHA) }
        }
        val alpha = if (tint.perceptualLuminance() > 0.5f) LIGHT_FILL_ALPHA else DARK_FILL_ALPHA
        drawRect(tint.copy(alpha = alpha))
        // Зерно — только поверх текстуры: на плоской заливке оно читалось бы как грязь на экране.
        if (backdrop != null) drawRect(brush = NoiseBrush, alpha = NOISE_ALPHA)
    }
    .border(
        width = 1.dp,
        // Хайрлайн ярче там, где под доком есть текстура: на её фоне прежние 0.08 растворялись,
        // а кромка — единственная разрешённая ручка объёма (блики и фаска запрещены).
        color = Color.White.copy(alpha = if (backdrop != null) RIM_ALPHA else FLAT_RIM_ALPHA),
        shape = shape,
    )

private fun DrawScope.drawBackdrop(image: ImageBitmap, alpha: Float) {
    if (alpha <= 0.001f) return
    drawImage(
        image = image,
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.Low,
    )
}

private const val DARK_FILL_ALPHA = 0.30f
private const val LIGHT_FILL_ALPHA = 0.40f
private const val BACKDROP_ALPHA = 0.5f
private const val NOISE_ALPHA = 0.05f
private const val RIM_ALPHA = 0.14f
private const val FLAT_RIM_ALPHA = 0.08f

/**
 * Тайл зерна 64×64, один на процесс: он статичный и к [PixelCopy] отношения не имеет — это фактура
 * самого стекла, а не сцены под ним. Серый вокруг средней точки, поэтому на низкой альфе даёт
 * крупинки в обе стороны, а не общее осветление.
 *
 * Умышленно `SrcOver`, а не `Overlay`/`SoftLight`: расширенные blend-режимы на аппаратном холсте
 * появляются только с API 29, а minSdk проекта — 26.
 */
private val NoiseBrush: Brush by lazy {
    val size = 64
    val random = java.util.Random(0x5EED)
    val pixels = IntArray(size * size) {
        val v = 96 + random.nextInt(96)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    val tile = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        .apply { setPixels(pixels, 0, size, 0, 0, size, size) }
        .asImageBitmap()
    ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
}
