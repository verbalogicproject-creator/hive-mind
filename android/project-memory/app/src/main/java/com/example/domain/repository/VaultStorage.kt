package com.example.domain.repository

import com.example.domain.model.VaultBlob

interface VaultStorage {
    suspend fun store(bytes: ByteArray, mimeType: String = "text/plain"): VaultBlob
    suspend fun get(sha256: String): ByteArray?
    suspend fun has(sha256: String): Boolean
    suspend fun countBlobs(): Int
    suspend fun totalSizeBytes(): Long
}
