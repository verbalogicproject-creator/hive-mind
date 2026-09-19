package com.example.domain.usecase

import com.example.domain.model.Episode
import com.example.domain.repository.EpisodeRepository
import java.util.UUID

class CreateEpisodeUseCase(
    private val episodeRepository: EpisodeRepository
) {
    suspend operator fun invoke(
        projectId: String,
        laneId: String,
        title: String,
        summary: String = "",
        source: String = "agent",
        tags: List<String> = emptyList()
    ): Result<Episode> {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) {
            return Result.failure(IllegalArgumentException("Episode title cannot be empty"))
        }
        if (projectId.trim().isEmpty()) {
            return Result.failure(IllegalArgumentException("Project ID cannot be empty"))
        }
        if (laneId.trim().isEmpty()) {
            return Result.failure(IllegalArgumentException("Lane ID cannot be empty"))
        }

        val episode = Episode(
            id = UUID.randomUUID().toString(),
            projectId = projectId.trim(),
            laneId = laneId.trim(),
            title = trimmedTitle,
            summary = summary.trim(),
            source = source.trim().ifEmpty { "agent" },
            tags = tags.map { it.trim() }.filter { it.isNotEmpty() },
            createdAt = System.currentTimeMillis()
        )

        return try {
            val created = episodeRepository.createEpisode(episode)
            Result.success(created)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
