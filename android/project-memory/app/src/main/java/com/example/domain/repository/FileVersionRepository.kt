package com.example.domain.repository

import com.example.domain.model.FileChunk
import com.example.domain.model.FileVersion
import kotlinx.coroutines.flow.Flow

interface FileVersionRepository {
    fun observeFileVersions(projectId: String): Flow<List<FileVersion>>
    suspend fun getFileVersion(id: String): FileVersion?
    suspend fun getLatestVersion(projectId: String, laneId: String, path: String): FileVersion?
    suspend fun recordVersion(version: FileVersion, chunks: List<FileChunk>)
    suspend fun getChunksForVersion(fileVersionId: String): List<FileChunk>
    suspend fun countFiles(projectId: String): Int
    suspend fun countVersions(projectId: String): Int
    suspend fun countChunks(projectId: String): Int
    suspend fun countAllVersions(): Int
    suspend fun countAllChunks(): Int
}
