package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.entity.FileChunkEntity
import com.example.data.entity.FileVersionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FileVersionDao {

    @Query("SELECT * FROM file_versions WHERE project_id = :projectId ORDER BY created_at DESC")
    fun observeFileVersions(projectId: String): Flow<List<FileVersionEntity>>

    @Query("SELECT * FROM file_versions WHERE id = :id LIMIT 1")
    suspend fun getFileVersion(id: String): FileVersionEntity?

    @Query("SELECT * FROM file_versions WHERE project_id = :projectId AND lane_id = :laneId AND path = :path ORDER BY version_number DESC LIMIT 1")
    suspend fun getLatestVersion(projectId: String, laneId: String, path: String): FileVersionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFileVersion(entity: FileVersionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFileChunks(chunks: List<FileChunkEntity>)

    @Transaction
    suspend fun insertVersionWithChunks(version: FileVersionEntity, chunks: List<FileChunkEntity>) {
        insertFileVersion(version)
        if (chunks.isNotEmpty()) {
            insertFileChunks(chunks)
        }
    }

    @Query("SELECT * FROM file_chunks WHERE file_version_id = :fileVersionId ORDER BY chunk_index ASC")
    suspend fun getChunksForVersion(fileVersionId: String): List<FileChunkEntity>

    @Query("SELECT COUNT(DISTINCT path) FROM file_versions WHERE project_id = :projectId")
    suspend fun countFiles(projectId: String): Int

    @Query("SELECT COUNT(*) FROM file_versions WHERE project_id = :projectId")
    suspend fun countVersions(projectId: String): Int

    @Query("SELECT COUNT(*) FROM file_chunks WHERE file_version_id IN (SELECT id FROM file_versions WHERE project_id = :projectId)")
    suspend fun countChunks(projectId: String): Int

    @Query("SELECT COUNT(*) FROM file_versions")
    suspend fun countAllVersions(): Int

    @Query("SELECT COUNT(*) FROM file_chunks")
    suspend fun countAllChunks(): Int
}
