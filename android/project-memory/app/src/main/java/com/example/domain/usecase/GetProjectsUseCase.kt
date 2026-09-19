package com.example.domain.usecase

import com.example.domain.model.Project
import com.example.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow

class GetProjectsUseCase(
    private val projectRepository: ProjectRepository
) {
    operator fun invoke(): Flow<List<Project>> {
        return projectRepository.observeProjects()
    }

    suspend fun getById(id: String): Project? {
        return projectRepository.getProject(id)
    }

    suspend fun count(): Int {
        return projectRepository.countProjects()
    }
}
