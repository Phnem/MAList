package com.example.myapplication.manga.ui

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max

/**
 * Обрезка однотонных полей страницы (порт `EdgeDetector` из Kotatsu) как трансформация Coil 3.
 *
 * Сканы очень часто приходят с широкими белыми полями от разворота книги: на телефоне они съедают
 * до трети экрана, а полезная область при этом ужимается до нечитаемой. Детектор находит границы
 * содержимого и отдаёт кроп — картинка после этого раскрывается на всю ширину сама, потому что
 * масштабированием занимается `ContentScale`.
 *
 * Работает только над рамкой изображения: ищет первую строку/столбец, отличающийся от фона
 * (цвет берётся с углов). Внутрь страницы детектор не заглядывает — это не «умная» обрезка
 * содержимого, а именно снятие полей.
 *
 * ⚠️ Применять только с `allowConversionToBitmap(false)`: длинные вебтун-полосы приходят из
 * [RegionBitmapDecoder] составным `Image`, и Coil ради трансформации схлопнул бы их в один
 * гигантский битмап — ровно тот OOM, от которого тайловый декодер и спасает.
 */
object EdgeCropTransformation : Transformation() {

    override val cacheKey: String = "manga-edge-crop-v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = input.width
        val height = input.height
        if (width < MIN_SIDE || height < MIN_SIDE) return input

        val background = backgroundColor(input) ?: return input

        val maxCropX = (width * MAX_CROP_FRACTION).toInt()
        val maxCropY = (height * MAX_CROP_FRACTION).toInt()

        val row = IntArray(width)
        val column = IntArray(height)

        val top = scanRows(input, row, background, from = 0, limit = maxCropY, step = 1)
        val bottom = scanRows(input, row, background, from = height - 1, limit = maxCropY, step = -1)
        val left = scanColumns(input, column, background, from = 0, limit = maxCropX, step = 1)
        val right = scanColumns(input, column, background, from = width - 1, limit = maxCropX, step = -1)

        // Кроп меньше порога не стоит второго битмапа в памяти: поля в пару пикселей глазом
        // не видны, а картинка перестала бы делить пиксели с исходной.
        if (max(top, bottom) < MIN_CROP_PX && max(left, right) < MIN_CROP_PX) return input

        val cropWidth = width - left - right
        val cropHeight = height - top - bottom
        if (cropWidth < MIN_SIDE || cropHeight < MIN_SIDE) return input

        return Bitmap.createBitmap(input, left, top, cropWidth, cropHeight)
    }

    /**
     * Цвет полей — по четырём углам. Если углы не сходятся между собой, полей нет: страница либо
     * уже обрезана, либо это иллюстрация в разворот, и обрезать у неё нечего.
     */
    private fun backgroundColor(bitmap: Bitmap): Int? {
        val corners = intArrayOf(
            bitmap.getPixel(0, 0),
            bitmap.getPixel(bitmap.width - 1, 0),
            bitmap.getPixel(0, bitmap.height - 1),
            bitmap.getPixel(bitmap.width - 1, bitmap.height - 1),
        )
        val reference = corners[0]
        return if (corners.all { matches(it, reference) }) reference else null
    }

    /** Сколько строк подряд от края совпадает с фоном; [step] = 1 сверху, -1 снизу. */
    private suspend fun scanRows(
        bitmap: Bitmap,
        buffer: IntArray,
        background: Int,
        from: Int,
        limit: Int,
        step: Int,
    ): Int {
        var scanned = 0
        var y = from
        while (scanned < limit && y in 0 until bitmap.height) {
            coroutineContext.ensureActive()
            bitmap.getPixels(buffer, 0, bitmap.width, 0, y, bitmap.width, 1)
            if (!isUniform(buffer, bitmap.width, background)) break
            scanned++
            y += step
        }
        return scanned
    }

    private suspend fun scanColumns(
        bitmap: Bitmap,
        buffer: IntArray,
        background: Int,
        from: Int,
        limit: Int,
        step: Int,
    ): Int {
        var scanned = 0
        var x = from
        while (scanned < limit && x in 0 until bitmap.width) {
            coroutineContext.ensureActive()
            // stride = 1 при ширине выборки 1 — читаем столбец одним вызовом, а не попиксельно.
            bitmap.getPixels(buffer, 0, 1, x, 0, 1, bitmap.height)
            if (!isUniform(buffer, bitmap.height, background)) break
            scanned++
            x += step
        }
        return scanned
    }

    private fun isUniform(pixels: IntArray, count: Int, background: Int): Boolean {
        for (i in 0 until count) {
            if (!matches(pixels[i], background)) return false
        }
        return true
    }

    /**
     * Поканальное сравнение с допуском: JPEG-артефакты по краю скана дают разброс в несколько
     * единиц, и строгое равенство остановило бы обрезку на первой же строке.
     */
    private fun matches(color: Int, reference: Int): Boolean {
        // Прозрачное поле — тоже поле, независимо от того, какой под ним RGB.
        if ((color ushr 24) < ALPHA_THRESHOLD && (reference ushr 24) < ALPHA_THRESHOLD) return true
        return abs((color shr 16 and 0xFF) - (reference shr 16 and 0xFF)) <= TOLERANCE &&
            abs((color shr 8 and 0xFF) - (reference shr 8 and 0xFF)) <= TOLERANCE &&
            abs((color and 0xFF) - (reference and 0xFF)) <= TOLERANCE
    }

    /** Разброс канала, который ещё считаем «тем же цветом». */
    private const val TOLERANCE = 12

    private const val ALPHA_THRESHOLD = 16

    /**
     * Потолок обрезки с каждой стороны. Без него страница-заставка, залитая одним цветом с
     * подписью посередине, схлопнулась бы в эту подпись.
     */
    private const val MAX_CROP_FRACTION = 0.22f

    private const val MIN_CROP_PX = 4

    /** Мельче — это не страница, а иконка/склейка: обрезать нечего. */
    private const val MIN_SIDE = 32
}
