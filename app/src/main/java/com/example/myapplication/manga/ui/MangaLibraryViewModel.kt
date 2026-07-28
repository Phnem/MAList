package com.example.myapplication.manga.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.manga.data.ChapterReadingProgress
import com.example.myapplication.manga.data.DownloadProgress
import com.example.myapplication.manga.data.MangaBinding
import com.example.myapplication.manga.data.MangaBindingStore
import com.example.myapplication.manga.data.MangaChapterCacheStore
import com.example.myapplication.manga.data.MangaDownloadStore
import com.example.myapplication.manga.data.MangaReadingStore
import com.example.myapplication.manga.domain.MangaChapter
import com.example.myapplication.manga.domain.MangaItem
import com.example.myapplication.manga.download.MangaDownloadWorker
import com.example.myapplication.manga.source.MangaSourceEngine
import com.example.myapplication.manga.source.MangaSourceResults
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed interface MangaLibraryUiState {
    data object Loading : MangaLibraryUiState

    /** Тайтл ещё не привязан к источнику — показываем варианты и ждём подтверждения (§8 плана). */
    data class SourcePicker(
        val query: String,
        val results: List<MangaSourceResults>,
        val searching: Boolean,
    ) : MangaLibraryUiState

    data class Chapters(
        val binding: MangaBinding,
        val chapters: List<MangaChapter>,
        /** Языки, на которых у источника есть главы — для чипов фильтра. */
        val availableLanguages: List<String>,
        val progress: Map<String, ChapterReadingProgress>,
        /** Ключи скачанных глав и прогресс активных загрузок — обе карты по `chapter.key`. */
        val downloadedKeys: Set<String> = emptySet(),
        val downloading: Map<String, DownloadProgress> = emptyMap(),
        val refreshing: Boolean = false,
    ) : MangaLibraryUiState

    data class Error(val message: String) : MangaLibraryUiState
}

/**
 * Вкладка «Главы» в Details: привязка тайтла к источнику + список глав с прогрессом чтения.
 *
 * Автоматически привязку НЕ проставляем даже при единственном точном совпадении: сопоставление
 * идёт по названию и ошибается (ромадзи против кириллицы, сиквелы с похожими именами), а молча
 * подсунутая не та манга — худший из возможных исходов.
 */
class MangaLibraryViewModel(
    private val app: Application,
    private val animeId: String,
    private val animeTitle: String,
    /** Пустая строка = английского названия нет; null через Koin-параметры не гоняем. */
    private val animeTitleEn: String,
    private val engine: MangaSourceEngine,
    private val bindingStore: MangaBindingStore,
    private val readingStore: MangaReadingStore,
    private val cacheStore: MangaChapterCacheStore,
    private val downloadStore: MangaDownloadStore,
) : ViewModel() {

    private val _state = MutableStateFlow<MangaLibraryUiState>(MangaLibraryUiState.Loading)
    val state: StateFlow<MangaLibraryUiState> = _state.asStateFlow()

    private var chapters: List<MangaChapter> = emptyList()
    private var searchJob: Job? = null
    private var chaptersJob: Job? = null
    private var progressJob: Job? = null
    private var downloadsJob: Job? = null

    init {
        viewModelScope.launch {
            bindingStore.ensureLoaded()
            val binding = bindingStore.bindingFor(animeId)
            if (binding != null) loadChapters(binding) else search(defaultQuery())
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = MangaLibraryUiState.SourcePicker(query, emptyList(), searching = true)
            val results = engine.searchAll(query)
            _state.value = MangaLibraryUiState.SourcePicker(query, results, searching = false)
        }
    }

    /** Пользователь подтвердил соответствие — с этого момента поиск для тайтла больше не нужен. */
    fun bind(item: MangaItem) {
        viewModelScope.launch {
            val binding = MangaBinding(
                animeId = animeId,
                sourceId = item.sourceId.value,
                mangaKey = item.key,
                title = item.title,
                coverUrl = item.coverUrl,
            )
            bindingStore.put(binding)
            loadChapters(binding)
        }
    }

    /**
     * Отвязка источника уносит и его следы: оглавление в кэше и скачанные главы. Иначе диск
     * заполняют главы источника, к которому пользователь уже не вернётся.
     */
    fun unbind() {
        viewModelScope.launch {
            chaptersJob?.cancel()
            progressJob?.cancel()
            downloadsJob?.cancel()
            chapters = emptyList()
            val previous = bindingStore.bindingFor(animeId)
            bindingStore.remove(animeId)
            if (previous != null) {
                cacheStore.remove(previous.sourceId, previous.mangaKey)
                downloadStore.deleteAll(previous.sourceId, previous.mangaKey)
            }
            search(defaultQuery())
        }
    }

    fun selectLanguage(language: String?) {
        viewModelScope.launch {
            bindingStore.setPreferredLanguage(animeId, language)
            val binding = bindingStore.bindingFor(animeId) ?: return@launch
            emitChapters(binding, refreshing = false)
        }
    }

    fun refresh() {
        val binding = bindingStore.bindingFor(animeId) ?: return
        loadChapters(binding, forceRefresh = true)
    }

    fun setRead(chapter: MangaChapter, read: Boolean) {
        viewModelScope.launch {
            readingStore.setRead(animeId, chapter.key, read, chapter.pageCount)
        }
    }

    /** Скачать главу для офлайна; повторный вызов по уже скачанной — удаление. */
    fun toggleDownload(chapter: MangaChapter) {
        if (chapter.paid) return
        viewModelScope.launch {
            downloadStore.ensureLoaded()
            if (downloadStore.isDownloaded(chapter)) {
                downloadStore.delete(chapter)
            } else if (downloadStore.progressOf(chapter) != null) {
                MangaDownloadWorker.cancel(app, chapter)
                downloadStore.onFinished(chapter)
            } else {
                MangaDownloadWorker.enqueue(app, chapter)
            }
        }
    }

    /**
     * Stale-while-revalidate: кэш показывается сразу, даже протухший, и обновляется фоном.
     * Пустой ответ источника при живом кэше — не ошибка, а повод оставить то, что уже есть:
     * оглавление не должно исчезать из-за одной неудачной попытки.
     */
    private fun loadChapters(binding: MangaBinding, forceRefresh: Boolean = false) {
        chaptersJob?.cancel()
        chaptersJob = viewModelScope.launch {
            cacheStore.ensureLoaded()
            val cached = cacheStore.entry(binding.sourceId, binding.mangaKey)
            val fresh = cacheStore.isFresh(binding.sourceId, binding.mangaKey)
            if (cached != null) {
                chapters = cached.chapters
                emitChapters(binding, refreshing = !fresh || forceRefresh)
                observeProgress(binding)
                observeDownloads(binding)
                if (fresh && !forceRefresh) return@launch
            } else {
                _state.value = MangaLibraryUiState.Loading
            }

            val loaded = engine.chapters(binding.toItem())
            if (loaded.isEmpty()) {
                if (cached == null) {
                    _state.value = MangaLibraryUiState.Error(ERROR_NO_CHAPTERS)
                } else {
                    emitChapters(binding, refreshing = false)
                }
                return@launch
            }
            chapters = loaded
            cacheStore.put(binding.sourceId, binding.mangaKey, loaded)
            emitChapters(binding, refreshing = false)
            observeProgress(binding)
            observeDownloads(binding)
        }
    }

    private fun emitChapters(binding: MangaBinding, refreshing: Boolean) {
        val languages = chapters.mapNotNull { it.language }.distinct().sorted()
        val filtered = binding.preferredLanguage
            ?.let { lang -> chapters.filter { it.language == lang } }
            ?.takeIf { it.isNotEmpty() }
            ?: chapters
        // Прогресс чтения и загрузки живут в своих потоках — при пересборке списка их не теряем.
        val previous = _state.value as? MangaLibraryUiState.Chapters
        _state.value = MangaLibraryUiState.Chapters(
            binding = binding,
            chapters = filtered,
            availableLanguages = languages,
            progress = previous?.progress.orEmpty(),
            downloadedKeys = previous?.downloadedKeys.orEmpty(),
            downloading = previous?.downloading.orEmpty(),
            refreshing = refreshing,
        )
    }

    private fun observeProgress(binding: MangaBinding) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            readingStore.progressFlow(animeId).collect { progress ->
                val current = _state.value
                if (current is MangaLibraryUiState.Chapters) {
                    _state.value = current.copy(progress = progress)
                }
            }
        }
    }

    /**
     * Скачанное и качающееся — из стора загрузок, отфильтрованное по текущему источнику: ключи
     * глав уникальны только внутри него.
     */
    private fun observeDownloads(binding: MangaBinding) {
        downloadsJob?.cancel()
        downloadsJob = viewModelScope.launch {
            downloadStore.ensureLoaded()
            val prefix = "${binding.sourceId}::"
            combine(downloadStore.downloaded, downloadStore.active) { downloaded, active ->
                val keys = downloaded.values
                    .filter { it.sourceId == binding.sourceId }
                    .map { it.chapterKey }
                    .toSet()
                val progress = active
                    .filterKeys { it.startsWith(prefix) }
                    .mapKeys { (key, _) -> key.removePrefix(prefix) }
                keys to progress
            }.collect { (keys, progress) ->
                val current = _state.value
                if (current is MangaLibraryUiState.Chapters) {
                    _state.value = current.copy(downloadedKeys = keys, downloading = progress)
                }
            }
        }
    }

    /** Английское название ищется у источников заметно лучше русского. */
    private fun defaultQuery(): String = animeTitleEn.takeIf { it.isNotBlank() } ?: animeTitle

    private companion object {
        const val ERROR_NO_CHAPTERS = "chapters_empty"
    }
}
