package com.example.domain.usecase

import com.example.domain.model.EpisodeEvent
import com.example.domain.repository.EpisodeRepository
import java.util.UUID

class AppendEpisodeEventUseCase(
    private val episodeRepository: EpisodeRepository
) {
    suspend operator fun invoke(
        episodeId: String,
        eventType: String,
        actor: String,
        payload: String,
        fileVersionId: String? = null
    ): Result<EpisodeEvent> {
        val trimmedEpisodeId = episodeId.trim()
        if (trimmedEpisodeId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Episode ID cannot be empty"))
        }
        val episode = episodeRepository.getEpisode(trimmedEpisodeId)
            ?: return Result.failure(IllegalArgumentException("Episode not found with id: $trimmedEpisodeId"))

        val nextSeq = episodeRepository.getNextSequenceNumber(trimmedEpisodeId)

        val event = EpisodeEvent(
            id = UUID.randomUUID().toString(),
            episodeId = episode.id,
            sequenceNumber = nextSeq,
            eventType = eventType.trim().ifEmpty { "message" },
            actor = actor.trim().ifEmpty { "system" },
            payload = payload,
            fileVersionId = fileVersionId?.trim()?.ifEmpty { null },
            createdAt = System.currentTimeMillis()
        )

        return try {
            val appended = episodeRepository.appendEvent(event)
            Result.success(appended)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
