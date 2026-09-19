package com.example.mcp

import com.example.data.vault.DiskVaultStorage
import com.example.domain.FakeEpisodeRepository
import com.example.domain.FakeFileVersionRepository
import com.example.domain.FakeProjectRepository
import com.example.domain.model.Project
import com.example.domain.model.ServerConfig
import com.example.domain.security.SecurityPolicy
import com.example.domain.repository.IndexTargetValidator
import com.example.domain.repository.ValidatedIndexTarget
import com.example.domain.usecase.AppendEpisodeEventUseCase
import com.example.domain.usecase.CalculateSha256UseCase
import com.example.domain.usecase.ChunkTextUseCase
import com.example.domain.usecase.CreateEpisodeUseCase
import com.example.domain.usecase.GetEpisodeTimelineUseCase
import com.example.domain.usecase.GetEpisodesUseCase
import com.example.domain.usecase.GetFileVersionsUseCase
import com.example.domain.usecase.GetSystemStatusUseCase
import com.example.domain.usecase.IndexFileContentUseCase
import com.example.domain.usecase.ReadStaticResourceUseCase
import com.example.domain.usecase.ReadVaultBlobUseCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class McpServerManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fakeRepository: FakeProjectRepository
    private lateinit var fakeFileVersionRepository: FakeFileVersionRepository
    private lateinit var vaultStorage: DiskVaultStorage
    private lateinit var getSystemStatusUseCase: GetSystemStatusUseCase
    private lateinit var readStaticResourceUseCase: ReadStaticResourceUseCase
    private lateinit var indexFileContentUseCase: IndexFileContentUseCase
    private lateinit var getFileVersionsUseCase: GetFileVersionsUseCase
    private lateinit var readVaultBlobUseCase: ReadVaultBlobUseCase
    private lateinit var fakeEpisodeRepository: FakeEpisodeRepository
    private lateinit var createEpisodeUseCase: CreateEpisodeUseCase
    private lateinit var appendEpisodeEventUseCase: AppendEpisodeEventUseCase
    private lateinit var getEpisodesUseCase: GetEpisodesUseCase
    private lateinit var getEpisodeTimelineUseCase: GetEpisodeTimelineUseCase
    private lateinit var securityPolicy: SecurityPolicy
    private lateinit var mcpServerManager: McpServerManager

    @Before
    fun setup() {
        fakeRepository = FakeProjectRepository()
        fakeFileVersionRepository = FakeFileVersionRepository()
        fakeEpisodeRepository = FakeEpisodeRepository()
        vaultStorage = DiskVaultStorage(tempFolder.newFolder("vault_mcp_test"))
        val calculateSha256UseCase = CalculateSha256UseCase()
        val chunkTextUseCase = ChunkTextUseCase(calculateSha256UseCase)

        indexFileContentUseCase = IndexFileContentUseCase(
            vaultStorage = vaultStorage,
            fileVersionRepository = fakeFileVersionRepository,
            indexTargetValidator = IndexTargetValidator { projectId, laneId -> ValidatedIndexTarget(projectId, laneId) },
            calculateSha256UseCase = calculateSha256UseCase,
            chunkTextUseCase = chunkTextUseCase
        )
        getFileVersionsUseCase = GetFileVersionsUseCase(fakeFileVersionRepository)
        readVaultBlobUseCase = ReadVaultBlobUseCase(vaultStorage)
        getSystemStatusUseCase = GetSystemStatusUseCase(fakeRepository)
        readStaticResourceUseCase = ReadStaticResourceUseCase()
        createEpisodeUseCase = CreateEpisodeUseCase(fakeEpisodeRepository)
        appendEpisodeEventUseCase = AppendEpisodeEventUseCase(fakeEpisodeRepository)
        getEpisodesUseCase = GetEpisodesUseCase(fakeEpisodeRepository)
        getEpisodeTimelineUseCase = GetEpisodeTimelineUseCase(fakeEpisodeRepository)
        securityPolicy = SecurityPolicy()

        mcpServerManager = McpServerManager(
            getSystemStatusUseCase = getSystemStatusUseCase,
            readStaticResourceUseCase = readStaticResourceUseCase,
            indexFileContentUseCase = indexFileContentUseCase,
            getFileVersionsUseCase = getFileVersionsUseCase,
            readVaultBlobUseCase = readVaultBlobUseCase,
            createEpisodeUseCase = createEpisodeUseCase,
            appendEpisodeEventUseCase = appendEpisodeEventUseCase,
            getEpisodesUseCase = getEpisodesUseCase,
            getEpisodeTimelineUseCase = getEpisodeTimelineUseCase,
            securityPolicy = securityPolicy
        )
    }

    @After
    fun tearDown() {
        mcpServerManager.stop()
    }

    @Test
    fun executeSystemStatusToolDirectly_returnsAccurateStatus() = runBlocking {
        fakeRepository.insertOrUpdate(
            Project(
                id = "p-mcp",
                slug = "mcp-project",
                name = "MCP Project",
                rootUri = "content://root"
            )
        )

        val status = mcpServerManager.executeSystemStatusToolDirectly()
        assertEquals("STOPPED", status.status)
        assertEquals(1, status.activeProjectsCount)
        assertEquals("127.0.0.1", status.host)
        assertTrue(status.isReadOnly)
        assertEquals("Loopback-Authenticated", status.securityMode)
    }

    @Test
    fun readStaticResourceDirectly_returnsManifestJson() {
        val result = mcpServerManager.readStaticResourceDirectly("project://manifest")
        assertTrue(result.isSuccess)
        val manifest = result.getOrThrow()
        assertTrue(manifest.contains("Project Memory Provider"))
        assertTrue(manifest.contains("system_status"))
    }

    @Test
    fun startAndStopServer_updatesRuntimeState() {
        // Start on test loopback port 9090
        val config = ServerConfig(host = "127.0.0.1", port = 9090, isReadOnly = true)
        val startResult = mcpServerManager.start(config)
        assertTrue(startResult.isSuccess)
        assertTrue(mcpServerManager.runtimeState.value.isRunning)
        assertEquals(9090, mcpServerManager.runtimeState.value.port)

        val stopResult = mcpServerManager.stop()
        assertTrue(stopResult.isSuccess)
        assertFalse(mcpServerManager.runtimeState.value.isRunning)
    }

    @Test
    fun startWithInvalidSecurityConfig_isRejected() {
        val insecureConfig = ServerConfig(host = "0.0.0.0", port = 8080, isLanEnabled = false)
        val result = mcpServerManager.start(insecureConfig)
        assertTrue(result.isFailure)
        assertFalse(mcpServerManager.runtimeState.value.isRunning)
        assertNotNull(mcpServerManager.runtimeState.value.lastError)
    }

    @Test
    fun phase1_indexAndRetrieveVaultBlobDirectly() = runBlocking {
        val indexRes = indexFileContentUseCase("proj-mcp-1", "lane-mcp-1", "test.rs", "fn main() { println!(\"MCP Phase 1\"); }")
        assertTrue(indexRes.isSuccess)
        val indexed = indexRes.getOrThrow()
        assertEquals(1, indexed.fileVersion.versionNumber)

        val blobText = readVaultBlobUseCase.getText(indexed.fileVersion.sha256)
        assertEquals("fn main() { println!(\"MCP Phase 1\"); }", blobText)

        val retrievedVersion = getFileVersionsUseCase.getLatestVersion("proj-mcp-1", "lane-mcp-1", "test.rs")
        assertNotNull(retrievedVersion)
        assertEquals(indexed.fileVersion.id, retrievedVersion?.id)
    }

    @Test
    fun phase2_createEpisodeAndLogEventsDirectly() {
        runBlocking {
            val epResult = mcpServerManager.executeCreateEpisodeDirectly(
                projectId = "proj-mcp-1",
                laneId = "lane-mcp-1",
                title = "MCP Interaction Session",
                summary = "Testing MCP Phase 2 episode flow",
                source = "agent",
                tags = listOf("mcp", "phase-2")
            )
            assertTrue(epResult.isSuccess)
            val ep = epResult.getOrThrow()
            assertEquals("proj-mcp-1", ep.projectId)
            assertEquals("MCP Interaction Session", ep.title)

            // Log event 1 (message)
            val ev1Result = mcpServerManager.executeLogEventDirectly(
                episodeId = ep.id,
                eventType = "message",
                actor = "user",
                payload = "Run Rust build"
            )
            assertTrue(ev1Result.isSuccess)
            assertEquals(1L, ev1Result.getOrThrow().sequenceNumber)

            // Log event 2 (decision)
            val ev2Result = mcpServerManager.executeLogEventDirectly(
                episodeId = ep.id,
                eventType = "decision",
                actor = "agent",
                payload = "Use cargo check instead of cargo build for faster loop"
            )
            assertTrue(ev2Result.isSuccess)
            assertEquals(2L, ev2Result.getOrThrow().sequenceNumber)

            // Retrieve timeline
            val timeline = mcpServerManager.executeGetTimelineDirectly(ep.id)
            assertEquals(2, timeline.size)
            assertEquals(1L, timeline[0].sequenceNumber)
            assertEquals(2L, timeline[1].sequenceNumber)
            assertEquals("user", timeline[0].actor)
            assertEquals("agent", timeline[1].actor)
        }
    }
}
