package com.example.data.repository

import com.example.data.dao.ProjectDao
import com.example.data.entity.ProjectEntity
import com.example.domain.model.Project
import com.example.domain.repository.ProjectRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ProjectRepositoryImpl(
    private val projectDao: ProjectDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ProjectRepository {

    override fun observeProjects(): Flow<List<Project>> {
        return projectDao.observeAll().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getProject(id: String): Project? = withContext(ioDispatcher) {
        projectDao.getById(id)?.toDomain()
    }

    override suspend fun getProjectBySlug(slug: String): Project? = withContext(ioDispatcher) {
        projectDao.getBySlug(slug)?.toDomain()
    }

    override suspend fun insertOrUpdate(project: Project) = withContext(ioDispatcher) {
        projectDao.insertOrUpdate(ProjectEntity.fromDomain(project))
    }

    override suspend fun countProjects(): Int = withContext(ioDispatcher) {
        projectDao.count()
    }

    override suspend fun deleteProject(id: String): Boolean = withContext(ioDispatcher) {
        projectDao.deleteById(id) > 0
    }
}
