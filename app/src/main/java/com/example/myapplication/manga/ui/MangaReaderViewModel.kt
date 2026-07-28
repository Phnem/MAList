package com.example.myapplication.manga.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.manga.data.MangaReaderMode
import com.example.myapplication.manga.data.MangaReadingStore
import com.example.myapplication.manga.domain.MangaChapter
import com.example.myapplication.manga.domain.MangaPage
import com.example.myapplication.manga.download.MangaPageResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MangaReaderUiState {
    data object Loading : MangaReaderUiState
    data class Ready(
        val chapter: MangaChapter,
        val pages: List<MangaPage>,
        /** Страница, с которой открываем: сохранённый прогресс, иначе первая. */
        val startPage: Int,
        val hasPrevious: Boolean,
        val hasNext: Boolean,
    ) : MangaReaderUiState

    data class Error(val message: String) : MangaReaderUiState
}

/**
 * Ридер одной главы с переходом на соседние.
 *
 * Список глав берётся из [MangaChapterHandoff] — ридер принципиально не ходит в источник за
 * оглавлением: он открывается из Details, где список уже загружен.
 */
class MangaReaderViewModel(
    private val animeId: String,
    initialChapterKey: String,
    private val pageResolver: MangaPageResolver,
    private val readingStore: MangaReadingStore,
) : ViewModel() {

    private val chapters: List<MangaChapter> = MangaChapterHandoff.chapters(animeId)

    private val _state = MutableStateFlow<MangaReaderUiState>(MangaReaderUiState.Loading)
    val state: StateFlow<MangaReaderUiState> = _state.asStateFlow()

    private var currentIndex: Int = chapters.indexOfFirst { it.key == initialChapterKey }
    private var loadJob: Job? = null
    private var saveJob: Job? = null

    val readerMode: StateFlow<MangaReaderMode> = readingStore.readerModeFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, MangaReaderMode.Paged)

    init {
        load()
    }

    fun load() {
        val chapter = chapters.getOrNull(currentIndex)
        if (chapter == null) {
            _state.value = MangaReaderUiState.Error(ERROR_NO_CHAPTER)
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = MangaReaderUiState.Loading
            val pages = pageResolver.pages(chapter)
            if (pages.isEmpty()) {
                _state.value = MangaReaderUiState.Error(ERROR_NO_PAGES)
                return@launch
            }
            val saved = readingStore.chapterProgress(animeId, chapter.key)
            // Дочитанную главу открываем сначала: продолжать с последней страницы бессмысленно.
            val startPage = saved
                ?.takeIf { !it.read }
                ?.pageIndex
                ?.coerceIn(0, pages.lastIndex)
                ?: 0
            _state.value = MangaReaderUiState.Ready(
                chapter = chapter,
                pages = pages,
                startPage = startPage,
                hasPrevious = currentIndex > 0,
                hasNext = currentIndex < chapters.lastIndex,
            )
        }
    }

    fun onPageChanged(pageIndex: Int) {
        val ready = _state.value as? MangaReaderUiState.Ready ?: return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            readingStore.saveProgress(
                animeId = animeId,
                chapterKey = ready.chapter.key,
                pageIndex = pageIndex,
                pageCount = ready.pages.size,
            )
        }
    }

    fun openNext() = moveBy(1)

    fun openPrevious() = moveBy(-1)

    fun setReaderMode(mode: MangaReaderMode) {
        viewModelScope.launch { readingStore.setReaderMode(mode) }
    }

    private fun moveBy(delta: Int) {
        val target = currentIndex + delta
        if (target !in chapters.indices) return
        currentIndex = target
        load()
    }

    private companion object {
        const val ERROR_NO_CHAPTER = "chapter_missing"
        const val ERROR_NO_PAGES = "pages_empty"
    }
}
