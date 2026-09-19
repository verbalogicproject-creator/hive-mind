package com.example.domain.model

data class FileVersion(
    val id: String,
    val projectId: String,
    val laneId: String,
    val path: String,
    val sha256: String,
    val sizeBytes: Long,
    val mimeType: String,
    val versionNumber: Int,
    val createdAt: Long = System.currentTimeMillis()
)
