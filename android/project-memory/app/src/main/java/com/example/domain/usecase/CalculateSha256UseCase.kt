package com.example.domain.usecase

import java.security.MessageDigest

class CalculateSha256UseCase {
    operator fun invoke(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    operator fun invoke(text: String): String {
        return invoke(text.toByteArray(Charsets.UTF_8))
    }
}
