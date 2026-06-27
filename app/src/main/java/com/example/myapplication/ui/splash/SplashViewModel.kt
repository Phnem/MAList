package com.example.myapplication.ui.splash

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.sync.supabase.AuthRepository
import com.example.myapplication.data.local.ImageCompressionMigrator
import com.example.myapplication.data.local.LegacyCollectionSafMigrator
import com.example.myapplication.data.local.LegacyStorageMigrator
import com.example.myapplication.data.local.MigrationManager
import com.example.myapplication.data.repository.AppUpdateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SplashState {
    data object Loading : SplashState
    data object MigratingStorage : SplashState
    data object MigratingJson : SplashState
    data object AwaitingLegacyFolder : SplashState
    data object ImportingLegacyFolder : SplashState
    data object RestoringFromCloud : SplashState
    data class Completed(val nextRoute: String) : SplashState
}

class SplashViewModel(
    private val legacyStorageMigrator: LegacyStorageMigrator,
    private val legacyCollectionSafMigrator: LegacyCollectionSafMigrator,
    private val migrationManager: MigrationManager,
    private val authRepository: AuthRepository,
    private val appUpdateRepository: AppUpdateRepository,
    private val imageCompressionMigrator: ImageCompressionMigrator,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SplashState>(SplashState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        startAppInitialization()
    }

    fun onLegacyFolderSelected(uri: Uri?) {
        if (uri == null) {
            if (_uiState.value is SplashState.AwaitingLegacyFolder) {
                _uiState.update { SplashState.AwaitingLegacyFolder }
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { SplashState.ImportingLegacyFolder }
            withContext(Dispatchers.IO) {
                legacyCollectionSafMigrator.saveTreeUriAndCopy(uri)
                imageCompressionMigrator.compressExistingImages()
            }
            finishStartup(legacyFolderPromptDismissed = true)
        }
    }

    fun skipLegacyFolderMigration() {
        viewModelScope.launch {
            finishStartup(forceWithoutImages = true)
        }
    }

    private fun startAppInitialization() {
        viewModelScope.launch {
            if (legacyStorageMigrator.isPendingMigration()) {
                _uiState.update { SplashState.MigratingStorage }
            }
            withContext(Dispatchers.IO) {
                legacyStorageMigrator.migrateIfNeeded()
            }

            if (migrationManager.needsJsonMigration()) {
                _uiState.update { SplashState.MigratingJson }
                migrationManager.runMigration()
            }

            withContext(Dispatchers.IO) {
                legacyCollectionSafMigrator.migrateAllAvailableSources()
            }

            if (legacyCollectionSafMigrator.needsLegacyFolderAccess()) {
                _uiState.update { SplashState.AwaitingLegacyFolder }
                return@launch
            }

            withContext(Dispatchers.IO) {
                imageCompressionMigrator.compressExistingImages()
            }

            finishStartup()
        }
    }

    private suspend fun finishStartup(
        forceWithoutImages: Boolean = false,
        legacyFolderPromptDismissed: Boolean = false,
    ) {
        if (
            !forceWithoutImages &&
            !legacyFolderPromptDismissed &&
            legacyCollectionSafMigrator.needsLegacyFolderAccess()
        ) {
            _uiState.update { SplashState.AwaitingLegacyFolder }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching { appUpdateRepository.refreshAppUpdate(force = false) }
        }

        val route = if (authRepository.hasToken() || authRepository.isGuest) "home" else "welcome"
        _uiState.update { SplashState.Completed(route) }
    }
}
