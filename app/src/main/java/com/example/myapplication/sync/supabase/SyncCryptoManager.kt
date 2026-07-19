package com.example.myapplication.sync.supabase

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Шифротекст + IV (оба base64) — как хранится в Supabase (`ciphertext`, `iv`). */
data class EncryptedBlob(val ciphertext: String, val iv: String)

/**
 * Криптопримитивы для E2EE-синхронизации ключей AI Connect.
 *
 * Модель: пассфраза устройства → PBKDF2WithHmacSHA256(salt) → AES-256 ключ, которым
 * AES/GCM шифруются API-ключи. Supabase хранит только шифротекст + IV + соль + verifier;
 * сервер не видит открытые ключи. `verifier` позволяет на другом устройстве проверить,
 * что введённая recovery-фраза даёт тот же ключ, ещё до расшифровки данных.
 *
 * Класс без состояния — потокобезопасен.
 */
class SyncCryptoManager {

    private val secureRandom = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { secureRandom.nextBytes(it) }

    /** Случайная пассфраза устройства (не показывается как есть; служит recovery-секретом). */
    fun generatePassphrase(): String = encodeBase64(randomBytes(PASSPHRASE_BYTES))

    fun generateSalt(): String = encodeBase64(randomBytes(SALT_BYTES))

    fun deriveKey(passphrase: String, saltB64: String): SecretKey {
        val salt = decodeBase64(saltB64)
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /** Публичный проверочный хеш ключа — SHA-256 от материала ключа (не раскрывает сам ключ). */
    fun verifier(key: SecretKey): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.encoded)
        return encodeBase64(digest)
    }

    fun encrypt(key: SecretKey, plaintext: String): EncryptedBlob {
        val iv = randomBytes(GCM_IV_BYTES)
        val cipher = Cipher.getInstance(AES_GCM).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptedBlob(ciphertext = encodeBase64(ct), iv = encodeBase64(iv))
    }

    fun decrypt(key: SecretKey, blob: EncryptedBlob): String {
        val iv = decodeBase64(blob.iv)
        val cipher = Cipher.getInstance(AES_GCM).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return String(cipher.doFinal(decodeBase64(blob.ciphertext)), Charsets.UTF_8)
    }

    private fun encodeBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decodeBase64(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        const val AES_GCM = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val KEY_BITS = 256
        const val SALT_BYTES = 16
        const val PASSPHRASE_BYTES = 32
        const val PBKDF2_ITERATIONS = 120_000
    }
}
