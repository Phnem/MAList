package com.example.myapplication.manga.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.manga.data.MangaReaderMode
import com.example.myapplication.manga.data.MangaReadingStore
import com.example.myapplication.manga.data.PageDirection
import com.example.myapplication.manga.domain.DetectReaderMode
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
    private val detectReaderMode: DetectReaderMode,
) : ViewModel() {

    private val chapters: List<MangaChapter> = MangaChapterHandoff.chapters(animeId)

    private val _state = MutableStateFlow<MangaReaderUiState>(MangaReaderUiState.Loading)
    val state: StateFlow<MangaReaderUiState> = _state.asStateFlow()

    private var currentIndex: Int = chapters.indexOfFirst { it.key == initialChapterKey }
    private var loadJob: Job? = null
    private var saveJob: Job? = null

    /** Автодетект — разовое событие на сессию ридера, а не проверка при каждой смене главы. */
    private var detectAttempted = false

    val readerMode: StateFlow<MangaReaderMode> = readingStore.readerModeFlow(animeId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, MangaReaderMode.Paged)

    val pageDirection: StateFlow<PageDirection> = readingStore.directionFlow(animeId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, PageDirection.Rtl)

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
            autoDetectLayout(pages)
        }
    }

    /**
     * Подобрать режим по пропорциям страницы — но только тайтлу, которому его ещё не выбирали:
     * ручной выбор автодетект не перетирает.
     *
     * Отдельной корутиной, а не внутри [load]: глава уже показана, и переключение на вебтун
     * догоняет её через секунду-другую вместо того, чтобы задерживать открытие.
     */
    private fun autoDetectLayout(pages: List<MangaPage>) {
        if (detectAttempted) return
        detectAttempted = true
        viewModelScope.launch {
            if (readingStore.hasExplicitMode(animeId)) return@launch
            // Результат пишем даже когда он совпал с дефолтом: так выбор становится явным и
            // детект больше не гоняется при каждом открытии тайтла.
            val detected = detectReaderMode(pages) ?: return@launch
            if (!readingStore.hasExplicitMode(animeId)) {
                readingStore.setReaderMode(animeId, detected)
            }
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

    /**
     * Режим и направление меняются одной кнопкой-циклом, поэтому пишем их вместе.
     * Направление — первым: при переходе «вебтун → классика» пейджера ещё нет, и он соберётся
     * ровно один раз, уже с нужным направлением.
     */
    fun setLayout(mode: MangaReaderMode, direction: PageDirection) {
        viewModelScope.launch {
            if (direction != pageDirection.value) readingStore.setDirection(animeId, direction)
            if (mode != readerMode.value) readingStore.setReaderMode(animeId, mode)
        }
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
