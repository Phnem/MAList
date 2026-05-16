package com.example.myapplication.data.models

/**
 * Persisted snapshot of the last GitHub release check ([com.example.myapplication.data.repository.AppUpdateRepository]).
 */
enum class AppUpdatePersistedKind {
    /** No successful check persisted yet — UI treats as [AppUpdateStatus.IDLE]. */
    IDLE,
    NO_UPDATE,
    UPDATE_AVAILABLE,
    ERROR,
}

fun AppUpdatePersistedKind.toUiStatus(): AppUpdateStatus = when (this) {
    AppUpdatePersistedKind.IDLE -> AppUpdateStatus.IDLE
    AppUpdatePersistedKind.NO_UPDATE -> AppUpdateStatus.NO_UPDATE
    AppUpdatePersistedKind.UPDATE_AVAILABLE -> AppUpdateStatus.UPDATE_AVAILABLE
    AppUpdatePersistedKind.ERROR -> AppUpdateStatus.ERROR
}

data class AppUpdateSnapshot(
    val persistedKind: AppUpdatePersistedKind,
    val latestTag: String?,
    val latestDownloadUrl: String?,
    val latestHtmlUrl: String?,
    val latestApkSizeBytes: Long?,
    val updateChangelogMarkdown: String?,
    val lastSuccessfulCheckEpochMs: Long,
    val startupDismissedTag: String?,
) {
    val startupOverlayEligible: Boolean
        get() = persistedKind == AppUpdatePersistedKind.UPDATE_AVAILABLE &&
            !latestTag.isNullOrBlank() &&
            latestTag != startupDismissedTag
}
