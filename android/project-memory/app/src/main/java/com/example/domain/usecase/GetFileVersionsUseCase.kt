package com.example.domain.usecase

import com.example.domain.model.FileChunk
import com.example.domain.model.FileVersion
import com.example.domain.repository.FileVersionRepository
import kotlinx.coroutines.flow.Flow

class GetFileVersionsUseCase(
    private val repository: FileVersionRepository
) {
    fun observeVersions(projectId: String): Flow<List<FileVersion>> =
        repository.observeFileVersions(projectId)

    suspend fun getLatestVersion(projectId: String, laneId: String, path: String): FileVersion? =
        repository.getLatestVersion(projectId, laneId, path)

    suspend fun getChunks(fileVersionId: String): List<FileChunk> =
        repository.getChunksForVersion(fileVersionId)

    suspend fun getFileVersion(id: String): FileVersion? =
        repository.getFileVersion(id)
}
