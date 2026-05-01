package com.example.myapplication.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.myapplication.data.repository.GeminiApiKeyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class EncryptedGeminiApiKeyRepository(
    context: Context
) : GeminiApiKeyRepository {

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        PREF_FILE,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _apiKeyFlow = MutableStateFlow(
        encryptedPrefs.getString(KEY_GEMINI_API, "").orEmpty()
    )
    override val apiKeyFlow: Flow<String> = _apiKeyFlow.asStateFlow()

    override suspend fun saveApiKey(apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = apiKey.trim()
            require(isValidKey(normalized)) { "Invalid Gemini API key format." }
            encryptedPrefs.edit().putString(KEY_GEMINI_API, normalized).apply()
            _apiKeyFlow.value = normalized
        }
    }

    override suspend fun clearApiKey(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            encryptedPrefs.edit().remove(KEY_GEMINI_API).apply()
            _apiKeyFlow.value = ""
        }
    }

    override fun isValidKey(apiKey: String): Boolean {
        return KEY_REGEX.matches(apiKey.trim())
    }

    private companion object {
        private const val PREF_FILE = "secure_gemini_prefs"
        private const val KEY_GEMINI_API = "gemini_api_key"
        private val KEY_REGEX = Regex("^AIza[A-Za-z0-9_-]{30,64}$")
    }
}
