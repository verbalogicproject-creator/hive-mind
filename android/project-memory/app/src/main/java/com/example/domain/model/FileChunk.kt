package com.example.domain.model

data class FileChunk(
    val id: String,
    val fileVersionId: String,
    val chunkIndex: Int,
    val content: String,
    val tokenCountEstimate: Int,
    val sha256: String
)
