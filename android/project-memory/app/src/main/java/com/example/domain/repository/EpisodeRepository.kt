package com.example.domain.repository

import com.example.domain.model.Episode
import com.example.domain.model.EpisodeEvent
import kotlinx.coroutines.flow.Flow

interface EpisodeRepository {
    fun observeEpisodes(projectId: String): Flow<List<Episode>>
    suspend fun getEpisode(id: String): Episode?
    suspend fun createEpisode(episode: Episode): Episode
    suspend fun appendEvent(event: EpisodeEvent): EpisodeEvent
    fun observeEvents(episodeId: String): Flow<List<EpisodeEvent>>
    suspend fun getEvents(episodeId: String): List<EpisodeEvent>
    suspend fun countEpisodes(projectId: String): Int
    suspend fun countEvents(projectId: String): Int
    suspend fun countAllEpisodes(): Int
    suspend fun countAllEvents(): Int
    suspend fun getNextSequenceNumber(episodeId: String): Long
}
