package com.example.domain.repository

import com.example.domain.model.Project
import kotlinx.coroutines.flow.Flow

/**
 * Domain interface for Project persistence.
 * Inversion of control: implemented in data layer without leaking Room into domain.
 */
interface ProjectRepository {
    fun observeProjects(): Flow<List<Project>>
    suspend fun getProject(id: String): Project?
    suspend fun getProjectBySlug(slug: String): Project?
    suspend fun insertOrUpdate(project: Project)
    suspend fun countProjects(): Int
    suspend fun deleteProject(id: String): Boolean
}
