package com.smart_finance_app.server

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256 GCM encryption utility for sensitive data at rest.
 *
 * Used to encrypt TrueLayer access tokens and refresh tokens before
 * storing them in the database.
 *
 * Each encryption call generates a fresh random IV (Initialisation Vector)
 * and prepends it to the ciphertext so it can be used during decryption.
 * Format stored in DB: Base64(iv + ciphertext + authTag)
 *
 * Requires environment variable: ENCRYPTION_KEY
 * Generate a suitable key with:
 *   [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
 */
object Encryption {

    private const val ALGORITHM       = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM   = "AES"
    private const val GCM_IV_LENGTH   = 12  // 96 bits — recommended for GCM
    private const val GCM_TAG_LENGTH  = 128 // bits

    private val secretKey: SecretKey by lazy {
        val base64Key = System.getenv("ENCRYPTION_KEY")
            ?: error("Missing environment variable: ENCRYPTION_KEY")
        val keyBytes = Base64.getDecoder().decode(base64Key)
        require(keyBytes.size == 32) {
            "ENCRYPTION_KEY must be exactly 32 bytes (256 bits) when Base64-decoded"
        }
        SecretKeySpec(keyBytes, KEY_ALGORITHM)
    }

    /**
     * Encrypts a plaintext string.
     * Returns a Base64-encoded string containing the IV + ciphertext + auth tag.
     */
    fun encrypt(plaintext: String): String {
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITHM).apply {
            init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        }
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Prepend IV to ciphertext so we have everything needed for decryption
        val combined = iv + ciphertext
        return Base64.getEncoder().encodeToString(combined)
    }

    /**
     * Decrypts a Base64-encoded string produced by [encrypt].
     * Returns the original plaintext string.
     */
    fun decrypt(encoded: String): String {
        val combined = Base64.getDecoder().decode(encoded)
        val iv         = combined.sliceArray(0 until GCM_IV_LENGTH)
        val ciphertext = combined.sliceArray(GCM_IV_LENGTH until combined.size)

        val cipher = Cipher.getInstance(ALGORITHM).apply {
            init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        }
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }
}