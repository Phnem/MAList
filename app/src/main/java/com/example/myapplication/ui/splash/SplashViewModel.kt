package com.example.myapplication.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.DropboxSyncManager
import com.example.myapplication.data.local.MigrationManager
import com.example.myapplication.data.repository.AppUpdateRepository
import com.example.myapplication.data.repository.LegacyMigrationRepository
import com.example.myapplication.domain.PermissionChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SplashState {
    data object Loading : SplashState
    data object Migrating : SplashState
    data class Completed(val nextRoute: String) : SplashState
}

class SplashViewModel(
    private val legacyMigrationRepository: LegacyMigrationRepository,
    private val migrationManager: MigrationManager,
    private val dropboxSyncManager: DropboxSyncManager,
    private val permissionChecker: PermissionChecker,
    private val appUpdateRepository: AppUpdateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SplashState>(SplashState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        startAppInitialization()
    }

    private fun startAppInitialization() {
        viewModelScope.launch {
            // 1. Ждем разрешения к файлам
            while (!permissionChecker.isExternalStorageManager()) {
                delay(500)
            }

            // 2. Проверяем старую папку MyAnimeList
            if (legacyMigrationRepository.needsMigration()) {
                _uiState.update { SplashState.Migrating }
                legacyMigrationRepository.migrateLegacyDataIfNeeded()
            }

            // 3. MigrationManager видит файлы в Vetro и заливает их в SQLDelight (list, updates, ignored)
            migrationManager.runMigration()

            viewModelScope.launch(Dispatchers.IO) {
                runCatching { appUpdateRepository.refreshAppUpdate(force = false) }
            }

            // 4. Роутинг: авторизован → Home, иначе → Welcome
            val route = if (dropboxSyncManager.hasToken()) "home" else "welcome"
            _uiState.update { SplashState.Completed(route) }
        }
    }
}
