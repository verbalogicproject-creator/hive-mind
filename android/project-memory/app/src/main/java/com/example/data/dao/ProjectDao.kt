package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE slug = :slug LIMIT 1")
    suspend fun getBySlug(slug: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: ProjectEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ProjectEntity)

    @Query("SELECT * FROM projects ORDER BY created_at ASC")
    suspend fun getAll(): List<ProjectEntity>

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun count(): Int

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: String): Int
}
