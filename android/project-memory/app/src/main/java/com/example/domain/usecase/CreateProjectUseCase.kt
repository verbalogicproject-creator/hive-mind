package com.example.domain.usecase

import com.example.domain.model.Project
import com.example.domain.repository.ProjectProvisioningRepository
import com.example.domain.repository.ProvisionedProject
import java.util.Locale
import java.util.UUID

class CreateProjectUseCase(
    private val provisioningRepository: ProjectProvisioningRepository
) {
    suspend operator fun invoke(
        name: String,
        rootUri: String,
        description: String = ""
    ): Result<ProvisionedProject> {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return Result.failure(IllegalArgumentException("Enter a project name"))
        if (rootUri.isBlank()) return Result.failure(IllegalArgumentException("Choose a project folder"))

        val suffix = UUID.randomUUID().toString().take(8)
        val slugBase = cleanName
            .lowercase(Locale.ROOT)
            .replace("[^a-z0-9]+".toRegex(), "-")
            .trim('-')
            .ifEmpty { "project" }
        val now = System.currentTimeMillis()
        val project = Project(
            id = "proj-$suffix",
            slug = "$slugBase-$suffix",
            name = cleanName,
            description = description.trim(),
            rootUri = rootUri,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        return provisioningRepository.createProject(project)
    }
}
