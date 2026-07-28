package com.example.myapplication.manga.domain

import android.graphics.BitmapFactory
import android.util.Log
import com.example.myapplication.manga.data.MangaReaderMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/**
 * Догадка о режиме чтения по пропорциям страницы (порт логики Kotatsu
 * `DetectReaderModeUseCase`): вебтун — это одна длинная вертикальная полоса, обычная манга —
 * страница около A4, и отличить их можно по одному соотношению сторон.
 *
 * Возвращает **подсказку**, а не команду: `null` значит «не смогли определить, настройку не
 * трогаем». Решение, применять ли результат, принимает вызывающий — явный выбор пользователя
 * всегда важнее автодетекта.
 *
 * Стоимость намеренно копеечная: качается только префикс файла и декодируются только габариты
 * (`inJustDecodeBounds`), пиксели в память не поднимаются.
 */
class DetectReaderMode(
    private val client: OkHttpClient,
) {

    /** Пробуем страницу с [PROBE_POSITION] главы: начало часто занимает обложка или врезка. */
    suspend operator fun invoke(pages: List<MangaPage>): MangaReaderMode? {
        if (pages.isEmpty()) return null
        val index = (pages.size * PROBE_POSITION).toInt().coerceIn(0, pages.lastIndex)
        return invoke(pages[index])
    }

    suspend operator fun invoke(page: MangaPage): MangaReaderMode? = withContext(Dispatchers.IO) {
        val size = readBounds(page) ?: return@withContext null
        val (width, height) = size
        if (width <= 0 || height <= 0) return@withContext null
        if (height > width * MIN_WEBTOON_RATIO) MangaReaderMode.Webtoon else MangaReaderMode.Paged
    }

    /** Габариты картинки или null на любой сетевой/форматной беде: детект не имеет права падать. */
    private suspend fun readBounds(page: MangaPage): Pair<Int, Int>? = runCatching {
        if (page.isLocal) {
            // Скачанная глава: путь берём срезом префикса, а не через URI — в имени папки
            // лежит ключ источника, который не обязан быть валидно закодированным.
            File(page.url.removePrefix("file://")).inputStream().use { readBounds(it) }
        } else {
            val request = Request.Builder()
                .url(page.url)
                // Заголовки источника обязательны: Remanga и подобные отдают картинки только
                // со своим Referer, без него придёт 403 и детект всегда молчал бы.
                .apply { page.headers.forEach { (name, value) -> header(name, value) } }
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) null else readBounds(body.byteStream())
            }
        }
    }.getOrElse { error ->
        if (error is CancellationException) throw error
        Log.w(TAG, "bounds probe failed for ${page.url}", error)
        null
    }

    /**
     * Читаем поток порциями и после каждой пробуем вытащить заголовок: у всех ходовых форматов
     * размеры лежат в первых килобайтах, так что до [MAX_PROBE_BYTES] дело обычно не доходит —
     * соединение закрывается, не докачав картинку.
     */
    private suspend fun readBounds(stream: InputStream): Pair<Int, Int>? {
        val buffer = ByteArray(CHUNK_BYTES)
        val prefix = ByteArrayOutputStream(CHUNK_BYTES)
        var total = 0
        while (total < MAX_PROBE_BYTES) {
            coroutineContext.ensureActive()
            val read = stream.read(buffer, 0, minOf(buffer.size, MAX_PROBE_BYTES - total))
            if (read <= 0) break
            total += read
            prefix.write(buffer, 0, read)
            decodeBounds(prefix.toByteArray())?.let { return it }
        }
        return null
    }

    private fun decodeBounds(bytes: ByteArray): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // На обрезанном префиксе decode вернёт null, но габариты из заголовка уже проставит.
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val width = options.outWidth
        val height = options.outHeight
        return if (width > 0 && height > 0) width to height else null
    }

    private companion object {
        const val TAG = "DetectReaderMode"

        /** Порог Kotatsu: полоса выше своей ширины в 1.8 раза — уже не страница, а вебтун. */
        const val MIN_WEBTOON_RATIO = 1.8

        /** Доля главы, с которой берём образец. */
        const val PROBE_POSITION = 0.3

        const val CHUNK_BYTES = 16 * 1024

        /** Потолок «дочитывания»: если за 128 КБ заголовок не нашёлся, файл нам не по зубам. */
        const val MAX_PROBE_BYTES = 128 * 1024
    }
}
