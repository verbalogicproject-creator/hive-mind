package com.example.domain

import com.example.domain.model.Episode
import com.example.domain.model.EpisodeEvent
import com.example.domain.repository.EpisodeRepository
import com.example.domain.usecase.AppendEpisodeEventUseCase
import com.example.domain.usecase.CreateEpisodeUseCase
import com.example.domain.usecase.GetEpisodeTimelineUseCase
import com.example.domain.usecase.GetEpisodesUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeEpisodeRepository : EpisodeRepository {
    private val episodes = MutableStateFlow<List<Episode>>(emptyList())
    private val events = MutableStateFlow<List<EpisodeEvent>>(emptyList())

    override fun observeEpisodes(projectId: String): Flow<List<Episode>> {
        return episodes.map { list -> list.filter { it.projectId == projectId } }
    }

    override suspend fun getEpisode(id: String): Episode? {
        return episodes.value.find { it.id == id }
    }

    override suspend fun createEpisode(episode: Episode): Episode {
        episodes.value = episodes.value + episode
        return episode
    }

    override suspend fun appendEvent(event: EpisodeEvent): EpisodeEvent {
        events.value = events.value + event
        return event
    }

    override fun observeEvents(episodeId: String): Flow<List<EpisodeEvent>> {
        return events.map { list -> list.filter { it.episodeId == episodeId }.sortedBy { it.sequenceNumber } }
    }

    override suspend fun getEvents(episodeId: String): List<EpisodeEvent> {
        return events.value.filter { it.episodeId == episodeId }.sortedBy { it.sequenceNumber }
    }

    override suspend fun countEpisodes(projectId: String): Int {
        return episodes.value.count { it.projectId == projectId }
    }

    override suspend fun countEvents(projectId: String): Int {
        val epIds = episodes.value.filter { it.projectId == projectId }.map { it.id }.toSet()
        return events.value.count { it.episodeId in epIds }
    }

    override suspend fun countAllEpisodes(): Int {
        return episodes.value.size
    }

    override suspend fun countAllEvents(): Int {
        return events.value.size
    }

    override suspend fun getNextSequenceNumber(episodeId: String): Long {
        val currentMax = events.value.filter { it.episodeId == episodeId }.maxOfOrNull { it.sequenceNumber } ?: 0L
        return currentMax + 1L
    }
}

class EpisodeUseCasesTest {

    private lateinit var fakeEpisodeRepository: FakeEpisodeRepository
    private lateinit var createEpisodeUseCase: CreateEpisodeUseCase
    private lateinit var appendEpisodeEventUseCase: AppendEpisodeEventUseCase
    private lateinit var getEpisodesUseCase: GetEpisodesUseCase
    private lateinit var getEpisodeTimelineUseCase: GetEpisodeTimelineUseCase

    @Before
    fun setUp() {
        fakeEpisodeRepository = FakeEpisodeRepository()
        createEpisodeUseCase = CreateEpisodeUseCase(fakeEpisodeRepository)
        appendEpisodeEventUseCase = AppendEpisodeEventUseCase(fakeEpisodeRepository)
        getEpisodesUseCase = GetEpisodesUseCase(fakeEpisodeRepository)
        getEpisodeTimelineUseCase = GetEpisodeTimelineUseCase(fakeEpisodeRepository)
    }

    @Test
    fun createEpisode_withValidInputs_succeedsAndIsObservable() {
        runBlocking {
            val result = createEpisodeUseCase(
                projectId = "p-1",
                laneId = "lane-1",
                title = "Initial Project Setup",
                summary = "Setting up Gradle dependencies and scaffolding",
                source = "agent",
                tags = listOf("setup", "phase-2")
            )

            assertTrue(result.isSuccess)
            val episode = result.getOrThrow()
            assertEquals("p-1", episode.projectId)
            assertEquals("Initial Project Setup", episode.title)
            assertEquals(2, episode.tags.size)

            val observed = getEpisodesUseCase("p-1").first()
            assertEquals(1, observed.size)
            assertEquals(episode.id, observed[0].id)
        }
    }

    @Test
    fun createEpisode_withBlankTitleOrProjectId_fails() {
        runBlocking {
            val blankTitle = createEpisodeUseCase(projectId = "p-1", laneId = "lane-1", title = "   ")
            assertTrue(blankTitle.isFailure)

            val blankProject = createEpisodeUseCase(projectId = "   ", laneId = "lane-1", title = "Some Title")
            assertTrue(blankProject.isFailure)
        }
    }

    @Test
    fun appendEvent_assignsMonotonicallyIncreasingSequenceNumbers() {
        runBlocking {
            val ep = createEpisodeUseCase(projectId = "p-1", laneId = "lane-1", title = "Feature Work").getOrThrow()

            val ev1 = appendEpisodeEventUseCase(
                episodeId = ep.id,
                eventType = "message",
                actor = "user",
                payload = "Please implement file hashing"
            ).getOrThrow()

            val ev2 = appendEpisodeEventUseCase(
                episodeId = ep.id,
                eventType = "tool_call",
                actor = "agent",
                payload = "{\"tool\":\"index_file_content\"}"
            ).getOrThrow()

            val ev3 = appendEpisodeEventUseCase(
                episodeId = ep.id,
                eventType = "file_write",
                actor = "agent",
                payload = "Updated DiskVaultStorage.kt",
                fileVersionId = "fv-12345"
            ).getOrThrow()

            assertEquals(1L, ev1.sequenceNumber)
            assertEquals(2L, ev2.sequenceNumber)
            assertEquals(3L, ev3.sequenceNumber)
            assertEquals("fv-12345", ev3.fileVersionId)

            val timeline = getEpisodeTimelineUseCase(ep.id)
            assertEquals(3, timeline.size)
            assertEquals(listOf(1L, 2L, 3L), timeline.map { it.sequenceNumber })
        }
    }

    @Test
    fun appendEvent_toNonExistentEpisode_fails() {
        runBlocking {
            val res = appendEpisodeEventUseCase(
                episodeId = "non-existent-id",
                eventType = "message",
                actor = "user",
                payload = "Hello"
            )
            assertTrue(res.isFailure)
        }
    }
}
