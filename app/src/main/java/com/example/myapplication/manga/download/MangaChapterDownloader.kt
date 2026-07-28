package com.example.myapplication.manga.download

import android.util.Log
import com.example.myapplication.manga.data.DownloadedChapter
import com.example.myapplication.manga.data.MangaDownloadStore
import com.example.myapplication.manga.domain.MangaChapter
import com.example.myapplication.manga.domain.MangaPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Последовательное скачивание страниц главы.
 *
 * Последовательно, а не пачкой: источники и так живут на грани рейт-лимита, а выигрыш в скорости
 * на 20 картинках не стоит риска получить 429 посреди тома. Каждая страница пишется во временный
 * файл и переименовывается — прерванная загрузка не оставит «половину картинки», которую ридер
 * потом покажет как битую.
 */
class MangaChapterDownloader(
    private val client: OkHttpClient,
    private val store: MangaDownloadStore,
) {

    suspend fun download(chapter: MangaChapter, pages: List<MangaPage>): Boolean {
        if (pages.isEmpty()) return false
        store.ensureLoaded()
        val dir = store.chapterDir(chapter.sourceId.value, chapter.mangaKey, chapter.key)

        return withContext(Dispatchers.IO) {
            // Перекачиваем начисто: остатки прошлой неудачной попытки могут быть от другой версии главы.
            dir.deleteRecursively()
            if (!dir.mkdirs()) {
                Log.w(TAG, "Cannot create ${dir.absolutePath}")
                return@withContext false
            }

            var bytes = 0L
            val names = mutableListOf<String>()
            store.onProgress(chapter, downloaded = 0, total = pages.size)
            try {
                pages.forEachIndexed { index, page ->
                    coroutineContext.ensureActive()
                    val name = "%04d%s".format(index + 1, page.url.imageExtension())
                    val written = fetchPage(page, File(dir, name))
                        ?: run {
                            Log.w(TAG, "Page ${index + 1}/${pages.size} failed for ${chapter.key}")
                            return@withContext abort(chapter, dir)
                        }
                    bytes += written
                    names += name
                    store.onProgress(chapter, downloaded = index + 1, total = pages.size)
                }
            } catch (e: Exception) {
                // Отмена — тоже сюда: недокачанная глава не должна остаться в индексе.
                Log.w(TAG, "Download of ${chapter.key} interrupted", e)
                return@withContext abort(chapter, dir)
            }

            store.put(
                DownloadedChapter(
                    sourceId = chapter.sourceId.value,
                    mangaKey = chapter.mangaKey,
                    chapterKey = chapter.key,
                    pageFiles = names,
                    bytes = bytes,
                    downloadedAt = System.currentTimeMillis(),
                ),
            )
            store.onFinished(chapter)
            true
        }
    }

    private fun abort(chapter: MangaChapter, dir: File): Boolean {
        dir.deleteRecursively()
        store.onFinished(chapter)
        return false
    }

    /** Возвращает число записанных байт или null, если страница не скачалась. */
    private fun fetchPage(page: MangaPage, target: File): Long? {
        val request = Request.Builder()
            .url(page.url)
            .apply { page.headers.forEach { (name, value) -> header(name, value) } }
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val tmp = File(target.parentFile, "${target.name}.part")
                val written = tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
                if (written <= 0L || !tmp.renameTo(target)) {
                    tmp.delete()
                    return null
                }
                written
            }
        }.getOrNull()
    }

    /** Расширение берём из URL; неизвестное — `.img`, Coil определяет формат по содержимому. */
    private fun String.imageExtension(): String {
        val clean = substringBefore('?').substringBefore('#')
        val ext = clean.substringAfterLast('.', "")
        return if (ext.length in 2..4 && ext.all { it.isLetterOrDigit() }) ".${ext.lowercase()}" else ".img"
    }

    private companion object {
        const val TAG = "MangaDownloader"
    }
}
