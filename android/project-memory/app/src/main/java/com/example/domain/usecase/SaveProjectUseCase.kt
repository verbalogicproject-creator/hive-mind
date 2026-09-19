package com.example.domain.usecase

import com.example.domain.model.Project
import com.example.domain.repository.ProjectRepository

class SaveProjectUseCase(
    private val projectRepository: ProjectRepository
) {
    suspend operator fun invoke(project: Project): Result<Project> {
        if (project.id.isBlank()) {
            return Result.failure(IllegalArgumentException("Project id cannot be empty."))
        }
        if (project.slug.isBlank()) {
            return Result.failure(IllegalArgumentException("Project slug cannot be empty."))
        }
        if (project.name.isBlank()) {
            return Result.failure(IllegalArgumentException("Project name cannot be empty."))
        }
        if (project.rootUri.isBlank()) {
            return Result.failure(IllegalArgumentException("Project rootUri cannot be empty."))
        }

        val sanitized = project.copy(
            slug = project.slug.trim().lowercase().replace("\\s+".toRegex(), "-"),
            name = project.name.trim(),
            description = project.description.trim(),
            updatedAtEpochMs = System.currentTimeMillis()
        )

        projectRepository.insertOrUpdate(sanitized)
        return Result.success(sanitized)
    }
}
