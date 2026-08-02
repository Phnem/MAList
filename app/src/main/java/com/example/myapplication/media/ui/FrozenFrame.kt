package com.example.myapplication.media.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Ширина снимка в пикселях. Кадр всё равно растягивается на весь экран — и это же его и
 * размывает: билинейная фильтрация при увеличении в двадцать с лишним раз даёт мягкую картинку
 * даром. Отдельный блюр не нужен, а `Modifier.blur` тут и не сработал бы: он требует API 31, а
 * пикселей `SurfaceView` Compose всё равно не видит.
 */
private const val SNAPSHOT_WIDTH = 128

/**
 * Сколько держать снимок после конца ожидания. Должно быть больше, чем длится уход оверлея,
 * иначе кадр исчезнет раньше катаны.
 */
private const val RELEASE_DELAY_MS = 450L

/**
 * Замороженный кадр видео на время ожидания.
 *
 * Видео живёт в [SurfaceView], и Compose его пикселей не видит: ни `Modifier.blur`, ни стекло
 * `drawBackdrop` поверх плеера не покажут ничего, кроме пустоты. Поэтому кадр снимается
 * [PixelCopy] — тем же способом, каким доки плеера подхватывают сцену.
 *
 * Снимок делается **один раз** на вход в ожидание: во время переключения серии кадр обязан
 * замереть, а не жить своей жизнью.
 *
 * @param active идёт ли ожидание.
 * @param enabled можно ли вообще снимать. `false` для DRM (secure surface пикселей не отдаёт) и
 *   для PiP. При `false` возвращается `null`, и вызывающая сторона показывает одно затемнение.
 * @return кадр или `null`, если снять не удалось. Неудача — штатный исход, а не ошибка.
 */
@Composable
fun rememberFrozenFrame(
    active: Boolean,
    enabled: Boolean,
    surfaceProvider: () -> SurfaceView?,
): ImageBitmap? {
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    val surface by rememberUpdatedState(surfaceProvider)

    LaunchedEffect(active, enabled) {
        if (active) {
            if (enabled) frame = surface()?.let { capture(it) }
        } else {
            delay(RELEASE_DELAY_MS)
            frame = null
        }
    }
    return frame
}

/** Снимает уменьшенную копию кадра. Пропорции берутся у сюрфейса, иначе картинку сплющит. */
private suspend fun capture(view: SurfaceView): ImageBitmap? {
    if (!view.holder.surface.isValid || view.width <= 0 || view.height <= 0) return null
    val height = (SNAPSHOT_WIDTH.toLong() * view.height / view.width).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(SNAPSHOT_WIDTH, height, Bitmap.Config.ARGB_8888)
    val copied = suspendCancellableCoroutine { continuation ->
        try {
            PixelCopy.request(
                view,
                bitmap,
                { result -> continuation.resume(result == PixelCopy.SUCCESS) },
                Handler(Looper.getMainLooper()),
            )
        } catch (e: IllegalArgumentException) {
            // Сюрфейс исчез между проверкой и запросом — снимка просто не будет.
            continuation.resume(false)
        }
    }
    return if (copied) bitmap.asImageBitmap() else null
}
