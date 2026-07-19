package com.example.myapplication.sync.supabase

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Локальное шифрованное хранилище recovery-пассфразы для E2EE-синка ключей AI Connect.
 *
 * Пассфраза генерируется на устройстве один раз и не покидает его в открытом виде
 * (в Supabase уходят только соль + verifier). На новом устройстве пассфраза недоступна —
 * пользователь вводит recovery-фразу вручную (см. [SyncPassphraseManager.unlockWithRecoveryPhrase]).
 */
class SyncPassphraseStore(
    context: Context,
) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREF_FILE,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun getPassphrase(): String? = prefs.getString(KEY_PASSPHRASE, null)?.takeIf { it.isNotBlank() }

    fun savePassphrase(passphrase: String) {
        prefs.edit().putString(KEY_PASSPHRASE, passphrase).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_PASSPHRASE).apply()
    }

    private companion object {
        const val PREF_FILE = "secure_ai_sync"
        const val KEY_PASSPHRASE = "sync_passphrase"
    }
}
