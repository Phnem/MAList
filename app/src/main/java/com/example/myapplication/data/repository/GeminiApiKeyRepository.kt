package com.example.myapplication.data.repository

import kotlinx.coroutines.flow.Flow

interface GeminiApiKeyRepository {
    val apiKeyFlow: Flow<String>

    suspend fun saveApiKey(apiKey: String): Result<Unit>

    suspend fun clearApiKey(): Result<Unit>

    fun isValidKey(apiKey: String): Boolean
}
