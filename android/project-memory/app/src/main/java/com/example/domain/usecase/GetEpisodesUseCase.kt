package com.example.domain.usecase

import com.example.domain.model.Episode
import com.example.domain.repository.EpisodeRepository
import kotlinx.coroutines.flow.Flow

class GetEpisodesUseCase(
    private val episodeRepository: EpisodeRepository
) {
    operator fun invoke(projectId: String): Flow<List<Episode>> {
        return episodeRepository.observeEpisodes(projectId)
    }

    suspend fun getEpisode(id: String): Episode? {
        return episodeRepository.getEpisode(id)
    }

    suspend fun countEpisodes(projectId: String): Int {
        return episodeRepository.countEpisodes(projectId)
    }
}
