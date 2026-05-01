package com.example.myapplication.ui.inspect

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.AnimeLocalDataSource
import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.repository.GeminiApiKeyRepository
import com.example.myapplication.domain.inspect.InspectContentMode
import com.example.myapplication.domain.inspect.InspectGeminiRequiredException
import com.example.myapplication.domain.inspect.InspectGeminiRequirement
import com.example.myapplication.domain.inspect.InspectImageUseCase
import com.example.myapplication.domain.normalizeForSearch
import com.example.myapplication.domain.search.AddFromApiUseCase
import com.example.myapplication.network.ApiSearchResult
import com.example.myapplication.network.AppContentType
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.ui.home.ApiSearchUiModel
import com.example.myapplication.utils.getStrings
import io.ktor.http.ContentType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val KEY_LANG = stringPreferencesKey("lang")
private val KEY_CONTENT_TYPE = stringPreferencesKey("contentType")

sealed interface InspectUiState {
    data object Idle : InspectUiState
    data class Loading(val message: String) : InspectUiState
    data class Success(val results: ImmutableList<ApiSearchUiModel>) : InspectUiState
    data class Error(val message: String) : InspectUiState
}

enum class GeminiKeyStatus {
    InsertedFromClipboard,
    InvalidFormat,
    Checking,
    CheckFailed,
    Saved
}

enum class MoviesTvOnboardingStep {
    Instruction,
    KeyInput,
    CheckError
}

data class GeminiKeyUiState(
    val input: String = "",
    val hasValidSavedKey: Boolean = false,
    val onboardingStep: MoviesTvOnboardingStep = MoviesTvOnboardingStep.Instruction,
    val status: GeminiKeyStatus? = null,
    val statusDetail: String? = null
)

class InspectViewModel(
    private val inspectImageUseCase: InspectImageUseCase,
    private val localDataSource: AnimeLocalDataSource,
    private val addFromApiUseCase: AddFromApiUseCase,
    settingsDataStore: DataStore<Preferences>,
    private val geminiApiKeyRepository: GeminiApiKeyRepository
) : ViewModel() {

    val uiLanguage: StateFlow<AppLanguage> = settingsDataStore.data
        .map { prefs ->
            runCatching { AppLanguage.valueOf(prefs[KEY_LANG] ?: "EN") }
                .getOrElse { AppLanguage.EN }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLanguage.EN)

    val contentMode = MutableStateFlow(InspectContentMode.Anime)
    val geminiApiKey: StateFlow<String> = geminiApiKeyRepository.apiKeyFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    private val _geminiKeyInput = MutableStateFlow("")
    private val _onboardingStep = MutableStateFlow(MoviesTvOnboardingStep.Instruction)
    private val _geminiKeyStatus = MutableStateFlow<GeminiKeyStatus?>(null)
    private val _geminiKeyStatusDetail = MutableStateFlow<String?>(null)
    private var lastHandledClipboardValue: String? = null

    val geminiKeyUiState: StateFlow<GeminiKeyUiState> = combine(
        geminiApiKey,
        _geminiKeyInput,
        _onboardingStep,
        _geminiKeyStatus,
        _geminiKeyStatusDetail
    ) { savedKey, input, step, status, detail ->
        GeminiKeyUiState(
            input = input,
            hasValidSavedKey = geminiApiKeyRepository.isValidKey(savedKey),
            onboardingStep = step,
            status = status,
            statusDetail = detail
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GeminiKeyUiState())

    private val _rawResults = MutableStateFlow<List<ApiSearchResult>>(emptyList())
    private val _loadingMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val _addingFromApiId = MutableStateFlow<String?>(null)
    val addingFromApiId: StateFlow<String?> = _addingFromApiId

    val selectedImageUri = MutableStateFlow<Uri?>(null)

    val uiState: StateFlow<InspectUiState> = combine(
        _rawResults,
        _loadingMessage,
        _errorMessage,
        localDataSource.observeAllAnime()
    ) { raw, loading, err, localList ->
        when {
            loading != null -> InspectUiState.Loading(loading)
            err != null -> InspectUiState.Error(err)
            raw.isEmpty() -> InspectUiState.Idle
            else -> InspectUiState.Success(
                raw.map { r ->
                    ApiSearchUiModel(
                        result = r,
                        isAdded = isAddedInMemory(r, localList)
                    )
                }.toImmutableList()
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InspectUiState.Idle)

    init {
        viewModelScope.launch {
            val prefs = settingsDataStore.data.first()
            val type = runCatching { AppContentType.valueOf(prefs[KEY_CONTENT_TYPE] ?: "ANIME") }
                .getOrElse { AppContentType.ANIME }
            contentMode.value = when (type) {
                AppContentType.ANIME -> InspectContentMode.Anime
                AppContentType.MOVIE, AppContentType.SERIES -> InspectContentMode.MoviesSeries
            }
        }
        viewModelScope.launch {
            geminiApiKey.collect { saved ->
                if (_geminiKeyInput.value.isBlank()) {
                    _geminiKeyInput.value = saved
                }
            }
        }
    }

    fun setContentMode(mode: InspectContentMode) {
        contentMode.value = mode
        _rawResults.value = emptyList()
        _loadingMessage.value = null
        _errorMessage.value = null
        if (mode == InspectContentMode.MoviesSeries && !geminiApiKeyRepository.isValidKey(geminiApiKey.value)) {
            _onboardingStep.value = MoviesTvOnboardingStep.Instruction
        }
    }

    fun onGeminiKeyInputChanged(value: String) {
        _geminiKeyInput.value = value
        _geminiKeyStatus.value = null
        _geminiKeyStatusDetail.value = null
    }

    fun openGeminiKeyInputStep() {
        _onboardingStep.value = MoviesTvOnboardingStep.KeyInput
        _geminiKeyStatus.value = null
        _geminiKeyStatusDetail.value = null
    }

    fun returnToGeminiInstruction() {
        _onboardingStep.value = MoviesTvOnboardingStep.Instruction
        _geminiKeyStatus.value = null
        _geminiKeyStatusDetail.value = null
    }

    fun checkAndSaveGeminiKey() {
        val candidate = _geminiKeyInput.value.trim()
        if (!geminiApiKeyRepository.isValidKey(candidate)) {
            _geminiKeyStatus.value = GeminiKeyStatus.InvalidFormat
            _geminiKeyStatusDetail.value = null
            return
        }
        viewModelScope.launch {
            _geminiKeyStatus.value = GeminiKeyStatus.Checking
            _geminiKeyStatusDetail.value = null
            inspectImageUseCase.validateGeminiApiKey(candidate).fold(
                onSuccess = {
                    geminiApiKeyRepository.saveApiKey(candidate).fold(
                        onSuccess = {
                            _geminiKeyInput.value = candidate
                            _geminiKeyStatus.value = GeminiKeyStatus.Saved
                            _geminiKeyStatusDetail.value = null
                        },
                        onFailure = { saveErr ->
                            _onboardingStep.value = MoviesTvOnboardingStep.CheckError
                            _geminiKeyStatus.value = GeminiKeyStatus.CheckFailed
                            _geminiKeyStatusDetail.value = saveErr.message
                        }
                    )
                },
                onFailure = { err ->
                    _onboardingStep.value = MoviesTvOnboardingStep.CheckError
                    _geminiKeyStatus.value = GeminiKeyStatus.CheckFailed
                    _geminiKeyStatusDetail.value = err.message
                }
            )
        }
    }

    fun tryImportGeminiKeyFromClipboard(
        isWindowFocused: Boolean,
        clipboardText: String?
    ) {
        if (!isWindowFocused) return
        val candidate = clipboardText?.trim().orEmpty()
        if (candidate.isEmpty()) return
        if (candidate == lastHandledClipboardValue) return
        lastHandledClipboardValue = candidate

        if (!geminiApiKeyRepository.isValidKey(candidate)) {
            return
        }
        _geminiKeyInput.value = candidate
        _geminiKeyStatus.value = GeminiKeyStatus.InsertedFromClipboard
        _geminiKeyStatusDetail.value = null
    }

    fun analyzeImage(context: Context, uri: Uri) {
        selectedImageUri.value = uri
        viewModelScope.launch {
            val lang = uiLanguage.value
            val str = getStrings(lang)
            _errorMessage.value = null
            _rawResults.value = emptyList()
            _loadingMessage.value = str.inspectLoadingAnalyzing
            val outcome = runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: error(str.inspectReadImageFailed)
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val traceCt = mimeToKtorContentType(mime)
                inspectImageUseCase(
                    imageBytes = bytes,
                    mimeTypeForTrace = traceCt,
                    mimeTypeForGemini = mime,
                    contentMode = contentMode.value,
                    appLanguage = lang,
                    geminiApiKey = geminiApiKey.value
                ).getOrThrow()
            }
            _loadingMessage.value = null
            outcome.fold(
                onSuccess = { list ->
                    if (list.isEmpty()) {
                        _errorMessage.value = str.inspectNoResults
                    } else {
                        _rawResults.value = list
                    }
                },
                onFailure = { e ->
                    _errorMessage.value = when (e) {
                        is InspectGeminiRequiredException -> when (e.requirement) {
                            InspectGeminiRequirement.RU_ANIME_PATH ->
                                str.inspectGeminiRequiredRuAnime
                            InspectGeminiRequirement.MOVIES_TV ->
                                str.inspectGeminiRequiredMovies
                        }
                        else -> e.message
                            ?: str.inspectErrorGeneric
                    }
                }
            )
        }
    }

    fun addFromApi(result: ApiSearchResult) {
        val key = "${result.source}_${result.externalId ?: result.title}"
        viewModelScope.launch {
            _addingFromApiId.value = key
            addFromApiUseCase(result).onFailure { it.printStackTrace() }
            _addingFromApiId.value = null
        }
    }

    fun clearResults() {
        _rawResults.value = emptyList()
        _errorMessage.value = null
    }

    fun clearPreviewAndResults() {
        selectedImageUri.value = null
        clearResults()
    }

    private fun isAddedInMemory(result: ApiSearchResult, localList: List<Anime>): Boolean {
        val q = result.title.normalizeForSearch()
        if (q.isEmpty()) return false
        return localList.any { anime ->
            val t = anime.title.normalizeForSearch()
            t.isNotEmpty() && (t.contains(q) || q.contains(t))
        }
    }
}

private fun mimeToKtorContentType(mime: String): ContentType = when {
    mime.contains("png", ignoreCase = true) -> ContentType.Image.PNG
    mime.contains("webp", ignoreCase = true) -> ContentType("image", "webp")
    mime.contains("gif", ignoreCase = true) -> ContentType.parse("image/gif")
    else -> ContentType.Image.JPEG
}
