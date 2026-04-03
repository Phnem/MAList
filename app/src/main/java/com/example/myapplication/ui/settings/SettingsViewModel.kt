package com.example.myapplication.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import androidx.core.content.FileProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.network.AppContentType
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.data.models.AppTheme
import com.example.myapplication.data.models.AppUpdateStatus
import com.example.myapplication.data.models.SemanticVersion
import com.phnem.vetro.BuildConfig
import com.example.myapplication.data.local.SQLDelightDatabaseFactory
import com.example.myapplication.data.repository.AnimeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File

private val KEY_LANG = stringPreferencesKey("lang")
private val KEY_THEME = stringPreferencesKey("theme")
private val KEY_CONTENT_TYPE = stringPreferencesKey("contentType")
private val KEY_DEV_MIRROR_DB = booleanPreferencesKey("dev_mirror_db_to_documents")
private val KEY_DEV_HIDE_SHARE = booleanPreferencesKey("dev_hide_share_button")
private val KEY_DEV_FPS_OVERLAY = booleanPreferencesKey("dev_fps_overlay")
private const val LOG_TAG = "SettingsViewModel"

class SettingsViewModel(
    private val repository: AnimeRepository,
    private val settingsDataStore: DataStore<Preferences>,
    private val databaseFactory: SQLDelightDatabaseFactory
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.data.first().let { prefs ->
                _uiState.update {
                    it.copy(
                        language = AppLanguage.valueOf(prefs[KEY_LANG] ?: "EN"),
                        theme = runCatching { AppTheme.valueOf(prefs[KEY_THEME] ?: "SYSTEM") }.getOrElse { AppTheme.SYSTEM },
                        contentType = runCatching { AppContentType.valueOf(prefs[KEY_CONTENT_TYPE] ?: "ANIME") }.getOrElse { AppContentType.ANIME },
                        devMirrorDbToDocuments = prefs[KEY_DEV_MIRROR_DB] ?: false,
                        devHideShareButton = prefs[KEY_DEV_HIDE_SHARE] ?: false,
                        devFpsOverlay = prefs[KEY_DEV_FPS_OVERLAY] ?: false
                    )
                }
            }
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            _uiState.update { it.copy(language = language) }
            settingsDataStore.edit { it[KEY_LANG] = language.name }
        }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            _uiState.update { it.copy(theme = theme) }
            settingsDataStore.edit { it[KEY_THEME] = theme.name }
        }
    }

    fun setContentType(contentType: AppContentType) {
        viewModelScope.launch {
            _uiState.update { it.copy(contentType = contentType) }
            settingsDataStore.edit { it[KEY_CONTENT_TYPE] = contentType.name }
        }
    }

    fun setDevMirrorDb(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(devMirrorDbToDocuments = enabled) }
            settingsDataStore.edit { it[KEY_DEV_MIRROR_DB] = enabled }
        }
    }

    fun setDevHideShare(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(devHideShareButton = enabled) }
            settingsDataStore.edit { it[KEY_DEV_HIDE_SHARE] = enabled }
        }
    }

    fun setDevFpsOverlay(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(devFpsOverlay = enabled) }
            settingsDataStore.edit { it[KEY_DEV_FPS_OVERLAY] = enabled }
        }
    }

    fun checkAppUpdate(context: Context) {
        if (_uiState.value.updateStatus == AppUpdateStatus.LOADING) return
        viewModelScope.launch {
            _uiState.update { it.copy(updateStatus = AppUpdateStatus.LOADING) }
            if (_uiState.value.currentVersion.isEmpty()) {
                try {
                    val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    _uiState.update { it.copy(currentVersion = pInfo.versionName ?: "v1.0.0") }
                } catch (e: Exception) {
                    _uiState.update { it.copy(currentVersion = "v1.0.0") }
                }
            }
            val localVer = _uiState.value.currentVersion
            repository.checkGithubUpdate(
                    owner = BuildConfig.GITHUB_OWNER,
                    repo = BuildConfig.GITHUB_REPO
                )
                .fold(
                    onSuccess = { release ->
                        if (release != null) {
                            _uiState.update {
                                it.copy(
                                    updateStatus = if (isNewerVersion(localVer, release.tagName)) {
                                        AppUpdateStatus.UPDATE_AVAILABLE
                                    } else {
                                        AppUpdateStatus.NO_UPDATE
                                    },
                                    latestVersion = release.tagName,
                                    latestDownloadUrl = release.downloadUrl,
                                    updateChangelogMarkdown = release.body
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    updateStatus = AppUpdateStatus.NO_UPDATE,
                                    updateChangelogMarkdown = null
                                )
                            }
                        }
                    },
                    onFailure = {
                        _uiState.update { it.copy(updateStatus = AppUpdateStatus.ERROR) }
                    }
                )
        }
    }

    fun loadUpdateChangelog() {
        if (_uiState.value.isUpdateChangelogLoading) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUpdateChangelogLoading = true,
                    updateChangelogError = null
                )
            }
            repository.checkGithubUpdate(
                owner = BuildConfig.GITHUB_OWNER,
                repo = BuildConfig.GITHUB_REPO
            ).fold(
                onSuccess = { release ->
                    _uiState.update {
                        it.copy(
                            isUpdateChangelogLoading = false,
                            updateChangelogMarkdown = release?.body,
                            updateChangelogError = null,
                            latestVersion = release?.tagName ?: it.latestVersion,
                            latestDownloadUrl = release?.downloadUrl ?: it.latestDownloadUrl
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isUpdateChangelogLoading = false,
                            updateChangelogError = e.message ?: "Failed to load changelog"
                        )
                    }
                }
            )
        }
    }

    private fun isNewerVersion(local: String, remote: String): Boolean = runCatching {
        parseVersion(remote) > parseVersion(local)
    }.getOrElse { false }

    fun shareWithDb(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val dbFile = context.getDatabasePath("anime.db")
                val shareText = "Check out Vetro — media list manager: https://github.com/Phnem/Vetra"
                val sendIntent = if (dbFile.exists()) {
                    databaseFactory.checkpoint()
                    val exportDir = File(context.cacheDir, "share").apply { mkdirs() }
                    val exportFile = File(exportDir, "vetro_list.db")
                    dbFile.copyTo(exportFile, overwrite = true)
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        exportFile
                    )
                    Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        putExtra(Intent.EXTRA_STREAM, uri)
                        type = "text/plain"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } else {
                    Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }
                }
                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(sendIntent, null))
                }
            }
        }
    }

    fun exportLogs(context: Context) {
        if (_uiState.value.isExportingLogs) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExportingLogs = true) }
            try {
                val logFile = withContext(Dispatchers.IO) {
                    val exportDir = File(context.cacheDir, "share").apply { mkdirs() }
                    val exportFile = File(exportDir, "vetro_logcat.txt")
                    val pid = Process.myPid().toString()
                    val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "--pid", pid).start()
                    val logs = process.inputStream.bufferedReader().use { it.readText() }
                    process.waitFor()
                    exportFile.writeText(logs)
                    exportFile
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    logFile
                )
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "Vetro logs")
                    type = "text/plain"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(sendIntent, null))
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Failed to export logs", e)
            } finally {
                _uiState.update { it.copy(isExportingLogs = false) }
            }
        }
    }

    private fun parseVersion(versionStr: String): SemanticVersion {
        val clean = versionStr.removePrefix("v").trim()
        val dashSplit = clean.split("-", limit = 2)
        val dots = dashSplit[0].split(".").map { it.toIntOrNull() ?: 0 }
        return SemanticVersion(
            dots.getOrElse(0) { 0 },
            dots.getOrElse(1) { 0 },
            dots.getOrElse(2) { 0 },
            if (dashSplit.size > 1) dashSplit[1] else ""
        )
    }
}
