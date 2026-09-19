package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.EpisodeEntity
import com.example.data.entity.EpisodeEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(episode: EpisodeEntity)

    @Query("SELECT * FROM episodes WHERE id = :id LIMIT 1")
    suspend fun getEpisode(id: String): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE project_id = :projectId ORDER BY created_at DESC")
    fun observeEpisodes(projectId: String): Flow<List<EpisodeEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(event: EpisodeEventEntity)

    @Query("SELECT * FROM episode_events WHERE episode_id = :episodeId ORDER BY sequence_number ASC")
    fun observeEvents(episodeId: String): Flow<List<EpisodeEventEntity>>

    @Query("SELECT * FROM episode_events WHERE episode_id = :episodeId ORDER BY sequence_number ASC")
    suspend fun getEvents(episodeId: String): List<EpisodeEventEntity>

    @Query("SELECT MAX(sequence_number) FROM episode_events WHERE episode_id = :episodeId")
    suspend fun getMaxSequenceNumber(episodeId: String): Long?

    @Query("SELECT COUNT(*) FROM episodes WHERE project_id = :projectId")
    suspend fun countEpisodes(projectId: String): Int

    @Query("SELECT COUNT(*) FROM episode_events ee INNER JOIN episodes e ON ee.episode_id = e.id WHERE e.project_id = :projectId")
    suspend fun countEvents(projectId: String): Int

    @Query("SELECT COUNT(*) FROM episodes")
    suspend fun countAllEpisodes(): Int

    @Query("SELECT COUNT(*) FROM episode_events")
    suspend fun countAllEvents(): Int
}
