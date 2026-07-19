package com.example.myapplication.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.repository.AnimeRepository
import com.example.myapplication.data.repository.ImageStorageRepository
import com.example.myapplication.network.AppLanguage
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DetailsViewModel(
    private val animeId: String,
    private val repository: AnimeRepository,
    private val settingsDataStore: DataStore<Preferences>,
    private val imageStorage: ImageStorageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Idle)
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    // Инициализация синхронно: узел sharedBounds есть в дереве на 0-м кадре — Exit transition работает.
    private val _currentAnime = MutableStateFlow<Anime?>(repository.getAnimeById(animeId))
    val currentAnime: StateFlow<Anime?> = _currentAnime.asStateFlow()

    private val _currentLanguage = MutableStateFlow(AppLanguage.EN)
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = settingsDataStore.data.first()
            val langKey = stringPreferencesKey("lang")
            _currentLanguage.value = AppLanguage.valueOf(prefs[langKey] ?: "EN")
            _currentAnime.value?.let { loadDetails(it, _currentLanguage.value) }
        }
    }

    fun getImgPath(name: String?): String? {
        if (name == null) return null
        return imageStorage.getImageFilePath(name)
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            repository.toggleFavorite(animeId)?.let { _currentAnime.value = it }
        }
    }

    private fun loadDetails(anime: Anime, language: AppLanguage) {
        viewModelScope.launch {
            val canLookupEnDescription =
                anime.anilistId != null ||
                    anime.malId != null ||
                    anime.shikimoriId != null ||
                    !anime.titleEn.isNullOrBlank()

            if (
                language == AppLanguage.EN &&
                anime.mediaType != com.example.myapplication.data.models.MediaType.MANGA &&
                !canLookupEnDescription
            ) {
                _uiState.value = DetailsUiState.MissingEnglishTitle
                return@launch
            }

            _uiState.value = DetailsUiState.Loading
            val startTime = System.currentTimeMillis()

            val lookupTitle = when (language) {
                AppLanguage.RU -> anime.titleRu?.takeIf { it.isNotBlank() } ?: anime.title
                AppLanguage.EN -> anime.titleEn?.takeIf { it.isNotBlank() } ?: anime.title
            }

            repository.fetchDetails(
                title = lookupTitle,
                language = language,
                isManga = anime.mediaType == com.example.myapplication.data.models.MediaType.MANGA,
                malId = anime.malId,
                anilistId = anime.anilistId,
                titleEn = anime.titleEn,
                shikimoriId = anime.shikimoriId,
            )
                .fold(
                    onSuccess = { details ->
                        val elapsed = System.currentTimeMillis() - startTime
                        if (elapsed < 500) delay(500 - elapsed)
                        _uiState.value = if (details != null) {
                            DetailsUiState.Success(details)
                        } else {
                            DetailsUiState.Error
                        }
                    },
                    onFailure = {
                        _uiState.value = DetailsUiState.Error
                    }
                )
        }
    }
}
