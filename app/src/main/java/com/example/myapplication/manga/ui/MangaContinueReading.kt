package com.example.myapplication.manga.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Действие кнопки play рядом с доком Details для манги — ровно то же, что для аниме делает
 * `EpisodeMenuViewModel.startWatching()`: автоматический клик по нужной главе.
 *
 * Глава выбирается по тому же правилу, что серия: первая непрочитанная, а если прочитано всё —
 * самая первая. «Там, где остановился» доводит уже сам ридер: он открывает главу на сохранённой
 * странице (и, в вебтуне, на сохранённой докрутке). Поэтому никакого особого возврата у такого
 * входа нет — жест назад работает как при обычном открытии главы из списка.
 *
 * Возвращает `null`, когда открывать нечего (тайтл ещё не привязан к источнику, оглавление не
 * загрузилось, все главы платные) — вызывающий в этом случае просто не рисует кнопку: мёртвый
 * play выглядит как поломка.
 *
 * ViewModel берётся по тому же ключу, что у [MangaChaptersPage], — это ровно тот же экземпляр с
 * уже загруженным оглавлением, второй загрузки не происходит.
 */
@Composable
fun rememberMangaContinueReading(
    animeId: String,
    animeTitle: String,
    animeTitleEn: String?,
    viewModel: MangaLibraryViewModel = koinViewModel(key = "manga_chapters_$animeId") {
        parametersOf(animeId, animeTitle, animeTitleEn.orEmpty())
    },
): (() -> Unit)? {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val chapters = (state as? MangaLibraryUiState.Chapters) ?: return null
    // Платные главы в ридер не отдаём — тем же правилом, что и при обычном открытии из списка.
    val readable = chapters.chapters.filterNot { it.paid }
    if (readable.isEmpty()) return null

    return {
        val target = readable.firstOrNull { chapters.progress[it.key]?.read != true }
            ?: readable.first()
        MangaReaderActivity.start(
            context = context,
            animeId = animeId,
            chapter = target,
            chapters = readable,
        )
    }
}
