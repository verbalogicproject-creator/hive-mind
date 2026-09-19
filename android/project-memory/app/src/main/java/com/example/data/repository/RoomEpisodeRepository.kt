package com.example.data.repository

import com.example.data.dao.EpisodeDao
import com.example.data.entity.EpisodeEntity
import com.example.data.entity.EpisodeEventEntity
import com.example.domain.model.Episode
import com.example.domain.model.EpisodeEvent
import com.example.domain.repository.EpisodeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomEpisodeRepository(
    private val episodeDao: EpisodeDao
) : EpisodeRepository {

    override fun observeEpisodes(projectId: String): Flow<List<Episode>> {
        return episodeDao.observeEpisodes(projectId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getEpisode(id: String): Episode? {
        return episodeDao.getEpisode(id)?.toDomain()
    }

    override suspend fun createEpisode(episode: Episode): Episode {
        episodeDao.insertEpisode(EpisodeEntity.fromDomain(episode))
        return episode
    }

    override suspend fun appendEvent(event: EpisodeEvent): EpisodeEvent {
        episodeDao.insertEvent(EpisodeEventEntity.fromDomain(event))
        return event
    }

    override fun observeEvents(episodeId: String): Flow<List<EpisodeEvent>> {
        return episodeDao.observeEvents(episodeId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getEvents(episodeId: String): List<EpisodeEvent> {
        return episodeDao.getEvents(episodeId).map { it.toDomain() }
    }

    override suspend fun countEpisodes(projectId: String): Int {
        return episodeDao.countEpisodes(projectId)
    }

    override suspend fun countEvents(projectId: String): Int {
        return episodeDao.countEvents(projectId)
    }

    override suspend fun countAllEpisodes(): Int {
        return episodeDao.countAllEpisodes()
    }

    override suspend fun countAllEvents(): Int {
        return episodeDao.countAllEvents()
    }

    override suspend fun getNextSequenceNumber(episodeId: String): Long {
        val currentMax = episodeDao.getMaxSequenceNumber(episodeId) ?: 0L
        return currentMax + 1L
    }
}
