package com.example.domain.model

data class VaultBlob(
    val sha256: String,
    val sizeBytes: Long,
    val mimeType: String = "text/plain",
    val createdAt: Long = System.currentTimeMillis()
)
