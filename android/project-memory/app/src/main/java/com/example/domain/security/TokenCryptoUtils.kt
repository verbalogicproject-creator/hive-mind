package com.example.domain.security

import java.security.MessageDigest
import java.security.SecureRandom

object TokenCryptoUtils {
    private val secureRandom = SecureRandom()

    /**
     * Generates a high-entropy bearer token with at least 256 bits (32 bytes) of cryptographic randomness.
     * Encoded as 64-char lowercase hexadecimal string.
     */
    fun generateSecureRandomToken(bytesCount: Int = 32): String {
        val bytes = ByteArray(bytesCount)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Deterministic SHA-256 hash used for indexed credential matching in SQLite/Room.
     * Stored as 64-char lowercase hex string.
     */
    fun sha256Hash(token: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(token.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Computes deterministic canonical UTF-8 SHA-256 for a request or payload.
     */
    fun canonicalHash(vararg parts: String?): String {
        val md = MessageDigest.getInstance("SHA-256")
        val joined = parts.joinToString("\u001F") { it ?: "\u0000" }
        val digest = md.digest(joined.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
