package com.example.myapplication.data.local

import androidx.datastore.preferences.core.booleanPreferencesKey

object DevPreferencesKeys {
    val ADAPTIVE_GLASS_SCROLL = booleanPreferencesKey("dev_adaptive_glass_scroll")
    /** When false (default), GitHub release checks and in-app APK updates are disabled (F-Droid mode). */
    val GITHUB_UPDATES_ENABLED = booleanPreferencesKey("dev_github_updates_enabled")

    /** Once the user has ever run «Дубляж названий», new titles are auto-enriched in the background. */
    val TITLE_DUBBING_EVER_ENABLED = booleanPreferencesKey("title_dubbing_ever_enabled")
}
