package com.example.myapplication

enum class SyncState {
    IDLE, SYNCING, DONE, ERROR, AUTH_REQUIRED, CONFLICT
}

enum class SyncMode {
    AUTO, MANUAL
}

data class SyncReport(
    val lastSyncTime: Long = 0L,
    val itemsSynced: Int = 0,
    val itemsFailed: Int = 0
)
