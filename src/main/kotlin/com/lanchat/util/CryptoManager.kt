package com.lanchat.util

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-128-GCM 加解密管理器。
 * 密文格式: Base64( IV(12 bytes) + ciphertext + authTag(16 bytes) )
 */
object CryptoManager {

    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128
    private const val KEY_LENGTH = 16

    private const val DEFAULT_PASSPHRASE = "LanChat@2024!Encrypt"

    private val secureRandom = SecureRandom()

    @Volatile
    private var secretKey: SecretKey? = null

    @Volatile
    var isEnabled: Boolean = true

    fun initialize(passphrase: String? = null) {
        val phrase = if (passphrase.isNullOrBlank()) DEFAULT_PASSPHRASE else passphrase
        val keyBytes = deriveKey(phrase)
        secretKey = SecretKeySpec(keyBytes, ALGORITHM)
    }

    /**
     * PBKDF2-like key derivation: SHA-256 hash truncated to 128 bits.
     */
    private fun deriveKey(passphrase: String): ByteArray {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val salt = "LanChat-Salt-2024".toByteArray()
        digest.update(salt)
        digest.update(passphrase.toByteArray(Charsets.UTF_8))
        return digest.digest().copyOf(KEY_LENGTH)
    }

    fun encrypt(plaintext: String): String {
        val key = secretKey ?: throw IllegalStateException("CryptoManager not initialized")
        val iv = ByteArray(IV_LENGTH).also { secureRandom.nextBytes(it) }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val result = ByteArray(IV_LENGTH + cipherBytes.size)
        System.arraycopy(iv, 0, result, 0, IV_LENGTH)
        System.arraycopy(cipherBytes, 0, result, IV_LENGTH, cipherBytes.size)

        return Base64.getEncoder().encodeToString(result)
    }

    fun decrypt(ciphertext: String): String {
        val key = secretKey ?: throw IllegalStateException("CryptoManager not initialized")
        val data = Base64.getDecoder().decode(ciphertext)
        if (data.size < IV_LENGTH + 1) throw IllegalArgumentException("Invalid ciphertext")

        val iv = data.copyOfRange(0, IV_LENGTH)
        val encrypted = data.copyOfRange(IV_LENGTH, data.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val plainBytes = cipher.doFinal(encrypted)

        return String(plainBytes, Charsets.UTF_8)
    }

    fun updatePassphrase(passphrase: String) {
        initialize(passphrase)
    }
}
