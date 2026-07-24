package com.example.myapplication.localplayer.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Динамический «Ambilight» для стеклянных доков: раз в ~секунду достаём кадр текущего видео
 * ([MediaMetadataRetriever], масштабированный), тянем доминирующий цвет нижней части кадра через
 * [Palette] и плавно анимируем — им подсвечивается фон дока. Плёнки видео в Compose-слое нет
 * (SurfaceView), поэтому имитируем свечение цветом, а не блюром пикселей.
 */
@Composable
fun rememberAmbientDockColor(
    mediaUri: String?,
    positionProvider: () -> Long,
    active: Boolean,
): Color {
    val context = LocalContext.current
    var target by remember { mutableStateOf(DEFAULT_AMBIENT) }

    androidx.compose.runtime.LaunchedEffect(mediaUri, active) {
        if (!active || mediaUri.isNullOrEmpty()) return@LaunchedEffect
        val sampler = AmbientPaletteSampler(context, Uri.parse(mediaUri))
        try {
            while (isActive) {
                sampler.sampleAt(positionProvider())?.let { target = it }
                delay(1100)
            }
        } finally {
            sampler.release()
        }
    }

    val animated by animateColorAsState(target, tween(900), label = "ambient")
    return animated
}

private val DEFAULT_AMBIENT = Color(0xFF141414)

private class AmbientPaletteSampler(context: Context, uri: Uri) {
    private val mmr = MediaMetadataRetriever().apply {
        runCatching { setDataSource(context, uri) }
    }

    suspend fun sampleAt(positionMs: Long): Color? = withContext(Dispatchers.IO) {
        val bmp = frameAt(positionMs) ?: return@withContext null
        try {
            val h = bmp.height
            val w = bmp.width
            if (w <= 0 || h <= 0) return@withContext null
            val palette = Palette.from(bmp)
                .setRegion(0, (h * 0.62f).toInt(), w, h) // нижняя часть кадра
                .clearFilters()
                .maximumColorCount(12)
                .generate()
            val argb = palette.getVibrantColor(0)
                .takeIf { it != 0 }
                ?: palette.getDominantColor(0).takeIf { it != 0 }
                ?: return@withContext null
            Color(argb)
        } finally {
            bmp.recycle()
        }
    }

    private fun frameAt(positionMs: Long): Bitmap? = runCatching {
        val us = positionMs * 1000
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            mmr.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 192, 108)
        } else {
            mmr.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }
    }.getOrNull()

    fun release() {
        runCatching { mmr.release() }
    }
}

/** Бесшовная матовая noise-текстура (генерим один раз в память), тайлится как ShaderBrush. */
@Composable
fun rememberNoiseBrush(): ShaderBrush = remember {
    val size = 96
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val rnd = java.util.Random(1917L)
    val pixels = IntArray(size * size) {
        val v = rnd.nextInt(256)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    bmp.setPixels(pixels, 0, size, 0, 0, size, size)
    ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/**
 * Стеклянная пилюля-док по рецепту Ambilight: 4 слоя —
 * (1) База: динамический цвет [ambient] градиентом снизу вверх;
 * (2) Тонировка: полупрозрачный чёрный;
 * (3) Матовость: [noise] в BlendMode.Overlay с низкой прозрачностью (ломает гладкость);
 * (4) Блик грани: 1dp рамка-градиент от полупрозрачного белого к прозрачному.
 */
fun Modifier.ambientGlassPill(
    shape: Shape,
    ambient: Color,
    noise: ShaderBrush,
): Modifier = this
    .clip(shape)
    .drawBehind {
        // (1) База Ambilight — заливка + свечение к нижней грани.
        drawRect(ambient.copy(alpha = 0.32f))
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                1f to ambient.copy(alpha = 0.38f),
            ),
        )
        // (2) Тонировка под тёмную тему Vetro.
        drawRect(Color.Black.copy(alpha = 0.42f))
        // (3) Матовый шум — «шершавое» стекло.
        drawRect(brush = noise, alpha = 0.12f, blendMode = BlendMode.Overlay)
    }
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.45f),
            0.5f to Color.White.copy(alpha = 0.12f),
            1f to Color.White.copy(alpha = 0.0f),
        ),
        shape = shape,
    )
