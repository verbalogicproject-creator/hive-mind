package com.example.data.repository

import com.example.data.dao.FileVersionDao
import com.example.data.entity.FileChunkEntity
import com.example.data.entity.FileVersionEntity
import com.example.domain.model.FileChunk
import com.example.domain.model.FileVersion
import com.example.domain.repository.FileVersionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FileVersionRepositoryImpl(
    private val dao: FileVersionDao
) : FileVersionRepository {

    override fun observeFileVersions(projectId: String): Flow<List<FileVersion>> {
        return dao.observeFileVersions(projectId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getFileVersion(id: String): FileVersion? = withContext(Dispatchers.IO) {
        dao.getFileVersion(id)?.toDomain()
    }

    override suspend fun getLatestVersion(projectId: String, laneId: String, path: String): FileVersion? = withContext(Dispatchers.IO) {
        dao.getLatestVersion(projectId, laneId, path)?.toDomain()
    }

    override suspend fun recordVersion(version: FileVersion, chunks: List<FileChunk>) = withContext(Dispatchers.IO) {
        val versionEntity = FileVersionEntity.fromDomain(version)
        val chunkEntities = chunks.map { FileChunkEntity.fromDomain(it) }
        dao.insertVersionWithChunks(versionEntity, chunkEntities)
    }

    override suspend fun getChunksForVersion(fileVersionId: String): List<FileChunk> = withContext(Dispatchers.IO) {
        dao.getChunksForVersion(fileVersionId).map { it.toDomain() }
    }

    override suspend fun countFiles(projectId: String): Int = withContext(Dispatchers.IO) {
        dao.countFiles(projectId)
    }

    override suspend fun countVersions(projectId: String): Int = withContext(Dispatchers.IO) {
        dao.countVersions(projectId)
    }

    override suspend fun countChunks(projectId: String): Int = withContext(Dispatchers.IO) {
        dao.countChunks(projectId)
    }

    override suspend fun countAllVersions(): Int = withContext(Dispatchers.IO) {
        dao.countAllVersions()
    }

    override suspend fun countAllChunks(): Int = withContext(Dispatchers.IO) {
        dao.countAllChunks()
    }
}
