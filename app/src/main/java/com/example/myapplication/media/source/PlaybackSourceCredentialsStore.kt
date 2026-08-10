package com.example.myapplication.media.source

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Encrypted local credentials for user-owned playback sources. */
class PlaybackSourceCredentialsStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREF_FILE,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun webDav(): WebDavConfig? {
        val baseUrl = prefs.getString(WEB_DAV_BASE_URL, null) ?: return null
        return WebDavConfig(
            baseUrl = baseUrl,
            rootPath = prefs.getString(WEB_DAV_ROOT, "").orEmpty(),
            username = prefs.getString(WEB_DAV_USERNAME, "").orEmpty(),
            password = prefs.getString(WEB_DAV_PASSWORD, "").orEmpty(),
            downloadAllowed = prefs.getBoolean(WEB_DAV_DOWNLOAD, false),
            allowInsecureHttp = prefs.getBoolean(WEB_DAV_INSECURE_HTTP, false),
        ).takeIf(WebDavConfig::isValid)
    }

    fun saveWebDav(config: WebDavConfig) {
        require(config.isValid()) { "Invalid WebDAV configuration" }
        prefs.edit()
            .putString(WEB_DAV_BASE_URL, config.baseUrl.trim())
            .putString(WEB_DAV_ROOT, config.rootPath.trim())
            .putString(WEB_DAV_USERNAME, config.username.trim())
            .putString(WEB_DAV_PASSWORD, config.password)
            .putBoolean(WEB_DAV_DOWNLOAD, config.downloadAllowed)
            .putBoolean(WEB_DAV_INSECURE_HTTP, config.allowInsecureHttp)
            .apply()
    }

    fun clearWebDav() {
        prefs.edit().apply {
            listOf(
                WEB_DAV_BASE_URL,
                WEB_DAV_ROOT,
                WEB_DAV_USERNAME,
                WEB_DAV_PASSWORD,
                WEB_DAV_DOWNLOAD,
                WEB_DAV_INSECURE_HTTP,
            ).forEach(::remove)
        }.apply()
    }

    private companion object {
        const val PREF_FILE = "secure_playback_sources"
        const val WEB_DAV_BASE_URL = "webdav_base_url"
        const val WEB_DAV_ROOT = "webdav_root"
        const val WEB_DAV_USERNAME = "webdav_username"
        const val WEB_DAV_PASSWORD = "webdav_password"
        const val WEB_DAV_DOWNLOAD = "webdav_download_allowed"
        const val WEB_DAV_INSECURE_HTTP = "webdav_allow_insecure_http"
    }
}
