package com.example.domain.usecase

import com.example.domain.model.EpisodeEvent
import com.example.domain.repository.EpisodeRepository
import kotlinx.coroutines.flow.Flow

class GetEpisodeTimelineUseCase(
    private val episodeRepository: EpisodeRepository
) {
    fun observeEvents(episodeId: String): Flow<List<EpisodeEvent>> {
        return episodeRepository.observeEvents(episodeId)
    }

    suspend operator fun invoke(episodeId: String): List<EpisodeEvent> {
        return episodeRepository.getEvents(episodeId)
    }

    suspend fun countEvents(projectId: String): Int {
        return episodeRepository.countEvents(projectId)
    }
}
