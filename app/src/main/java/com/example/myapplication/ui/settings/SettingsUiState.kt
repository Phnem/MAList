package com.example.myapplication.ui.settings

import com.example.myapplication.network.AppContentType
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.data.models.AppTheme
import com.example.myapplication.data.models.AppUpdateStatus

/**
 * Immutable UI state для SettingsScreen
 */
data class SettingsUiState(
    val language: AppLanguage = AppLanguage.EN,
    val theme: AppTheme = AppTheme.SYSTEM,
    val contentType: AppContentType = AppContentType.ANIME,
    val devMirrorDbToDocuments: Boolean = false,
    val devHideShareButton: Boolean = false,
    val devFpsOverlay: Boolean = false,
    val isExportingLogs: Boolean = false,
    val isExportingPdf: Boolean = false,
    val isImportingDb: Boolean = false,
    val importDbMessage: String? = null,
    val updateStatus: AppUpdateStatus = AppUpdateStatus.IDLE,
    val currentVersion: String = "",
    val latestVersion: String? = null,
    val latestDownloadUrl: String? = null,
    val updateChangelogMarkdown: String? = null,
    val isUpdateChangelogLoading: Boolean = false,
    val updateChangelogError: String? = null,
    val latestApkSizeBytes: Long? = null,
    val isApkDownloading: Boolean = false,
    val apkDownloadProgress: Float = 0f,
    val pendingApkPathForInstall: String? = null,
    val latestReleaseHtmlUrl: String? = null,
)
