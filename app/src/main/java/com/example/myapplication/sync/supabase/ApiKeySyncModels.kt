package com.example.myapplication.sync.supabase

import kotlinx.serialization.Serializable
import javax.crypto.SecretKey

/** Строка таблицы `user_api_keys` (шифротекст ключа AI-провайдера). */
@Serializable
data class RemoteApiKeyDto(
    val user_id: String,
    val provider: String,
    val ciphertext: String,
    val iv: String,
    val updated_at: String,
    val deleted_at: String? = null,
)

/** Строка таблицы `user_sync_passphrase` (соль + verifier для E2EE-ключа). */
@Serializable
data class SyncPassphraseDto(
    val user_id: String,
    val salt: String,
    val verifier_hash: String,
    val updated_at: String,
)

/** Результат попытки разблокировать E2EE-ключ на текущем устройстве. */
sealed interface PassphraseUnlock {
    /** Ключ выведен и проверен — можно шифровать/дешифровать. */
    data class Unlocked(val key: SecretKey) : PassphraseUnlock

    /** В облаке есть passphrase-запись, но локальной фразы нет/не совпала — нужна recovery-фраза. */
    data object Locked : PassphraseUnlock

    /** Нет входа/гость — синк ключей неприменим. */
    data object NoUser : PassphraseUnlock
}
