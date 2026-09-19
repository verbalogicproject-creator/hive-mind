package com.example.ui.status

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Episode
import com.example.domain.model.AgentMemoryEntry
import com.example.domain.model.AgentSessionSummary
import com.example.domain.model.AgentSummary
import com.example.domain.model.GraphProjection
import com.example.domain.model.MemoryEntryDetail
import com.example.domain.model.EpisodeEvent
import com.example.domain.model.ClientInstallation
import com.example.domain.model.FileChunk
import com.example.domain.model.FileVersion
import com.example.domain.model.LaneKind
import com.example.domain.model.Project
import com.example.domain.model.ProjectScope
import com.example.domain.model.ServerConfig
import com.example.domain.repository.ClientIdentityRepository
import com.example.domain.repository.OwnerMemoryRepository
import com.example.domain.repository.VaultStorage
import com.example.domain.usecase.AppendEpisodeEventUseCase
import com.example.domain.usecase.CreateEpisodeUseCase
import com.example.domain.usecase.GetEpisodeTimelineUseCase
import com.example.domain.usecase.GetEpisodesUseCase
import com.example.domain.usecase.GetFileVersionsUseCase
import com.example.domain.usecase.GetGraphProjectionUseCase
import com.example.domain.usecase.GetProjectsUseCase
import com.example.domain.usecase.IndexFileContentUseCase
import com.example.domain.usecase.SaveProjectUseCase
import com.example.mcp.McpServerManager
import com.example.mcp.ServerRuntimeState
import com.example.service.McpForegroundService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class ConsoleOutput(
    val title: String,
    val content: String,
    val timestampMs: Long = System.currentTimeMillis()
)

data class PairingTokenReveal(
    val token: String,
    val clientName: String,
    val projectId: String
)

@OptIn(ExperimentalCoroutinesApi::class)
class ServerStatusViewModel(
    private val mcpServerManager: McpServerManager,
    private val getProjectsUseCase: GetProjectsUseCase,
    private val saveProjectUseCase: SaveProjectUseCase,
    private val indexFileContentUseCase: IndexFileContentUseCase? = null,
    private val getFileVersionsUseCase: GetFileVersionsUseCase? = null,
    private val vaultStorage: VaultStorage? = null,
    private val createEpisodeUseCase: CreateEpisodeUseCase? = null,
    private val appendEpisodeEventUseCase: AppendEpisodeEventUseCase? = null,
    private val getEpisodesUseCase: GetEpisodesUseCase? = null,
    private val getEpisodeTimelineUseCase: GetEpisodeTimelineUseCase? = null,
    private val clientIdentityRepository: ClientIdentityRepository? = null,
    private val ownerMemoryRepository: OwnerMemoryRepository? = null,
    private val getGraphProjectionUseCase: GetGraphProjectionUseCase? = null
) : ViewModel() {

    val serverState: StateFlow<ServerRuntimeState> = mcpServerManager.runtimeState

    val projects: StateFlow<List<Project>> = getProjectsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedProjectId = MutableStateFlow<String?>(null)
    val selectedProjectId: StateFlow<String?> = _selectedProjectId.asStateFlow()

    val fileVersions: StateFlow<List<FileVersion>> = _selectedProjectId.flatMapLatest { pId ->
        if (pId != null && getFileVersionsUseCase != null) {
            getFileVersionsUseCase.observeVersions(pId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedChunks = MutableStateFlow<List<FileChunk>>(emptyList())
    val selectedChunks: StateFlow<List<FileChunk>> = _selectedChunks.asStateFlow()

    val episodes: StateFlow<List<Episode>> = _selectedProjectId.flatMapLatest { pId ->
        if (pId != null && getEpisodesUseCase != null) {
            getEpisodesUseCase(pId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedEpisodeId = MutableStateFlow<String?>(null)
    val selectedEpisodeId: StateFlow<String?> = _selectedEpisodeId.asStateFlow()

    val episodeTimeline: StateFlow<List<EpisodeEvent>> = _selectedEpisodeId.flatMapLatest { epId ->
        if (epId != null && getEpisodeTimelineUseCase != null) {
            getEpisodeTimelineUseCase.observeEvents(epId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _vaultBlobCount = MutableStateFlow(0)
    val vaultBlobCount: StateFlow<Int> = _vaultBlobCount.asStateFlow()

    private val _vaultSizeBytes = MutableStateFlow(0L)
    val vaultSizeBytes: StateFlow<Long> = _vaultSizeBytes.asStateFlow()

    private val _consoleOutput = MutableStateFlow<ConsoleOutput?>(null)
    val consoleOutput: StateFlow<ConsoleOutput?> = _consoleOutput.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _portInput = MutableStateFlow("8080")
    val portInput: StateFlow<String> = _portInput.asStateFlow()

    private val _serverReadOnly = MutableStateFlow(true)
    val serverReadOnly: StateFlow<Boolean> = _serverReadOnly.asStateFlow()

    private val _clientInstallations = MutableStateFlow<List<ClientInstallation>>(emptyList())
    val clientInstallations: StateFlow<List<ClientInstallation>> = _clientInstallations.asStateFlow()

    private val _agentSummaries = MutableStateFlow<List<AgentSummary>>(emptyList())
    val agentSummaries: StateFlow<List<AgentSummary>> = _agentSummaries.asStateFlow()

    private val _selectedAgentSessions = MutableStateFlow<List<AgentSessionSummary>>(emptyList())
    val selectedAgentSessions: StateFlow<List<AgentSessionSummary>> = _selectedAgentSessions.asStateFlow()

    private val _selectedAgentMemory = MutableStateFlow<List<AgentMemoryEntry>>(emptyList())
    val selectedAgentMemory: StateFlow<List<AgentMemoryEntry>> = _selectedAgentMemory.asStateFlow()

    private val _projectMemory = MutableStateFlow<List<AgentMemoryEntry>>(emptyList())
    val projectMemory: StateFlow<List<AgentMemoryEntry>> = _projectMemory.asStateFlow()

    private val _graphProjection = MutableStateFlow<GraphProjection?>(null)
    val graphProjection: StateFlow<GraphProjection?> = _graphProjection.asStateFlow()

    private val _memoryEntryDetail = MutableStateFlow<MemoryEntryDetail?>(null)
    val memoryEntryDetail: StateFlow<MemoryEntryDetail?> = _memoryEntryDetail.asStateFlow()

    private val _pairingToken = MutableStateFlow<PairingTokenReveal?>(null)
    val pairingToken: StateFlow<PairingTokenReveal?> = _pairingToken.asStateFlow()

    private val json = Json { prettyPrint = true }

    init {
        refreshVaultStats()
        refreshClients()
    }

    fun selectTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun selectProject(projectId: String?) {
        _selectedProjectId.value = projectId
        _selectedChunks.value = emptyList()
        _selectedEpisodeId.value = null
        _memoryEntryDetail.value = null
        _selectedAgentSessions.value = emptyList()
        _selectedAgentMemory.value = emptyList()
        if (projectId == null) {
            _agentSummaries.value = emptyList()
            _projectMemory.value = emptyList()
            _graphProjection.value = null
        } else {
            refreshOwnerProjectData(projectId)
        }
    }

    fun selectEpisode(episodeId: String?) {
        _selectedEpisodeId.value = episodeId
    }

    fun selectAgent(installationId: String) {
        val projectId = _selectedProjectId.value ?: return
        viewModelScope.launch {
            runCatching {
                _selectedAgentSessions.value = ownerMemoryRepository?.getAgentSessions(projectId, installationId).orEmpty()
                _selectedAgentMemory.value = ownerMemoryRepository?.getAgentMemory(projectId, installationId).orEmpty()
            }.onFailure(::reportUiError)
        }
    }

    fun createSampleEpisode(projectId: String, title: String, summary: String = "", tags: List<String> = listOf("task", "agent")) {
        viewModelScope.launch {
            if (createEpisodeUseCase == null) {
                _consoleOutput.value = ConsoleOutput("Error", "CreateEpisodeUseCase not available")
                return@launch
            }
            val laneId = ownerLaneId(projectId)
            if (laneId == null) {
                _consoleOutput.value = ConsoleOutput("Episode not created", "This project has no writable on-device owner lane.")
                return@launch
            }
            val res = createEpisodeUseCase(projectId, laneId, title, summary, "agent", tags)
            res.fold(
                onSuccess = { ep ->
                    _selectedEpisodeId.value = ep.id
                    _consoleOutput.value = ConsoleOutput(
                        title = "Episode Created",
                        content = "Created episode '${ep.title}' (${ep.id.take(8)}...)"
                    )
                },
                onFailure = { err ->
                    _consoleOutput.value = ConsoleOutput("Create Episode Failed", err.message ?: "Unknown error")
                }
            )
        }
    }

    fun appendSampleEvent(
        episodeId: String,
        eventType: String,
        actor: String,
        payload: String,
        fileVersionId: String? = null
    ) {
        viewModelScope.launch {
            if (appendEpisodeEventUseCase == null) {
                _consoleOutput.value = ConsoleOutput("Error", "AppendEpisodeEventUseCase not available")
                return@launch
            }
            val res = appendEpisodeEventUseCase(episodeId, eventType, actor, payload, fileVersionId)
            res.fold(
                onSuccess = { ev ->
                    _consoleOutput.value = ConsoleOutput(
                        title = "Event Appended #${ev.sequenceNumber}",
                        content = "Event ${ev.eventType} by ${ev.actor}: ${ev.payload}"
                    )
                },
                onFailure = { err ->
                    _consoleOutput.value = ConsoleOutput("Append Event Failed", err.message ?: "Unknown error")
                }
            )
        }
    }

    fun updatePort(port: String) {
        _portInput.value = port.filter { it.isDigit() }
    }

    fun setServerReadOnly(readOnly: Boolean) {
        if (!serverState.value.isRunning) {
            _serverReadOnly.value = readOnly
        }
    }

    fun refreshClients() {
        viewModelScope.launch {
            _clientInstallations.value = clientIdentityRepository?.listInstallations().orEmpty()
        }
    }

    fun createPairingToken(projectId: String, clientName: String) {
        val name = clientName.trim()
        if (projectId.isBlank() || name.isBlank()) {
            _consoleOutput.value = ConsoleOutput("Pairing not created", "Choose a project and enter a client name.")
            return
        }
        viewModelScope.launch {
            val repository = clientIdentityRepository
            if (repository == null) {
                _consoleOutput.value = ConsoleOutput("Pairing not created", "Client identity storage is unavailable.")
                return@launch
            }
            try {
                val (_, rawToken) = repository.createPairingInvitation(
                    targetProjectId = projectId,
                    targetLaneKind = LaneKind.PRIVATE,
                    intendedClientName = name,
                    scopes = listOf(
                        ProjectScope.APPROVED_READ.value,
                        ProjectScope.INBOX_APPEND.value,
                        ProjectScope.SESSION_START.value
                    )
                )
                _pairingToken.value = PairingTokenReveal(rawToken, name, projectId)
            } catch (error: Exception) {
                _consoleOutput.value = ConsoleOutput(
                    "Pairing not created",
                    error.message ?: "Check the selected project and try again."
                )
            }
        }
    }

    fun clearPairingToken() {
        _pairingToken.value = null
    }

    fun revokeClient(installationId: String) {
        if (installationId == "local-device-owner") return
        viewModelScope.launch {
            val result = clientIdentityRepository?.revokeInstallation(installationId)
                ?: Result.failure(IllegalStateException("Client identity storage is unavailable"))
            result.fold(
                onSuccess = { refreshClients() },
                onFailure = { error ->
                    _consoleOutput.value = ConsoleOutput(
                        "Client not revoked",
                        error.message ?: "Try again."
                    )
                }
            )
        }
    }

    fun refreshVaultStats() {
        viewModelScope.launch {
            vaultStorage?.let {
                _vaultBlobCount.value = it.countBlobs()
                _vaultSizeBytes.value = it.totalSizeBytes()
            }
        }
    }

    fun loadChunks(fileVersionId: String) {
        viewModelScope.launch {
            if (getFileVersionsUseCase != null) {
                val chunks = getFileVersionsUseCase.getChunks(fileVersionId)
                _selectedChunks.value = chunks
                _consoleOutput.value = ConsoleOutput(
                    title = "File Chunks Loaded (${chunks.size} chunks)",
                    content = chunks.joinToString("\n---\n") {
                        "Chunk #${it.chunkIndex} (${it.tokenCountEstimate} tokens, sha256: ${it.sha256}):\n${it.content}"
                    }
                )
            }
        }
    }

    fun toggleServer(context: Context) {
        val current = serverState.value
        val port = _portInput.value.toIntOrNull() ?: 8080

        if (current.isRunning) {
            McpForegroundService.stopService(context)
        } else {
            McpForegroundService.startService(
                context = context,
                host = "127.0.0.1",
                port = port,
                isReadOnly = _serverReadOnly.value
            )
        }
    }

    fun seedSampleProject() {
        _consoleOutput.value = ConsoleOutput(
            title = "Choose a project folder",
            content = "Create projects from Home with the Android folder picker. Sample projects are no longer created."
        )
    }

    fun indexSampleFile(projectId: String, path: String, content: String) {
        viewModelScope.launch {
            if (indexFileContentUseCase == null) {
                _consoleOutput.value = ConsoleOutput(
                    title = "Indexing Error",
                    content = "IndexFileContentUseCase not available"
                )
                return@launch
            }
            val laneId = ownerLaneId(projectId)
            if (laneId == null) {
                _consoleOutput.value = ConsoleOutput("Indexing Failed", "This project has no writable on-device owner lane.")
                return@launch
            }
            val result = indexFileContentUseCase(projectId, laneId, path, content, "text/plain")
            result.fold(
                onSuccess = { res ->
                    refreshVaultStats()
                    _consoleOutput.value = ConsoleOutput(
                        title = if (res.isDeduplicated) "File Deduplicated in Vault" else "File Indexed into Vault",
                        content = """
                            Path: ${res.fileVersion.path}
                            SHA-256: ${res.fileVersion.sha256}
                            Version: ${res.fileVersion.versionNumber}
                            Size: ${res.fileVersion.sizeBytes} bytes
                            Chunks: ${res.chunkCount}
                            Deduplicated: ${res.isDeduplicated}
                        """.trimIndent()
                    )
                },
                onFailure = { err ->
                    _consoleOutput.value = ConsoleOutput(
                        title = "Indexing Failed",
                        content = err.message ?: "Unknown error"
                    )
                }
            )
        }
    }

    fun runSystemStatusToolDirectly() {
        viewModelScope.launch {
            try {
                val status = mcpServerManager.executeSystemStatusToolDirectly()
                _consoleOutput.value = ConsoleOutput(
                    title = "MCP Tool Invocation: system_status",
                    content = json.encodeToString(status)
                )
            } catch (e: Exception) {
                _consoleOutput.value = ConsoleOutput(
                    title = "MCP Tool Error",
                    content = "Failed to run system_status: ${e.message}"
                )
            }
        }
    }

    fun runReadManifestResourceDirectly() {
        viewModelScope.launch {
            val result = mcpServerManager.readStaticResourceDirectly("project://manifest")
            val content = result.getOrElse { "Error: ${it.message}" }
            _consoleOutput.value = ConsoleOutput(
                title = "MCP Resource Read: project://manifest",
                content = content
            )
        }
    }

    fun clearConsole() {
        _consoleOutput.value = null
    }

    fun reportUiError(error: Throwable) {
        _consoleOutput.value = ConsoleOutput(
            title = "Action could not be completed",
            content = error.message ?: "Try again."
        )
    }

    fun loadMemoryDetail(versionId: String) {
        val projectId = _selectedProjectId.value ?: return
        viewModelScope.launch {
            _memoryEntryDetail.value = ownerMemoryRepository?.getMemoryDetail(projectId, versionId)
        }
    }

    private fun refreshOwnerProjectData(projectId: String) {
        viewModelScope.launch {
            runCatching {
                _agentSummaries.value = ownerMemoryRepository?.getAgentSummaries(projectId).orEmpty()
                _projectMemory.value = ownerMemoryRepository?.getProjectMemory(projectId).orEmpty()
                _graphProjection.value = getGraphProjectionUseCase?.invoke(projectId)?.getOrThrow()
            }.onFailure(::reportUiError)
        }
    }

    private suspend fun ownerLaneId(projectId: String): String? =
        clientIdentityRepository
            ?.getLanesForProject(projectId)
            ?.firstOrNull { !it.isArchived && it.laneKind == LaneKind.PRIVATE }
            ?.id

    companion object {
        fun provideFactory(
            mcpServerManager: McpServerManager,
            getProjectsUseCase: GetProjectsUseCase,
            saveProjectUseCase: SaveProjectUseCase,
            indexFileContentUseCase: IndexFileContentUseCase? = null,
            getFileVersionsUseCase: GetFileVersionsUseCase? = null,
            vaultStorage: VaultStorage? = null,
            createEpisodeUseCase: CreateEpisodeUseCase? = null,
            appendEpisodeEventUseCase: AppendEpisodeEventUseCase? = null,
            getEpisodesUseCase: GetEpisodesUseCase? = null,
            getEpisodeTimelineUseCase: GetEpisodeTimelineUseCase? = null,
            clientIdentityRepository: ClientIdentityRepository? = null,
            ownerMemoryRepository: OwnerMemoryRepository? = null,
            getGraphProjectionUseCase: GetGraphProjectionUseCase? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ServerStatusViewModel(
                    mcpServerManager = mcpServerManager,
                    getProjectsUseCase = getProjectsUseCase,
                    saveProjectUseCase = saveProjectUseCase,
                    indexFileContentUseCase = indexFileContentUseCase,
                    getFileVersionsUseCase = getFileVersionsUseCase,
                    vaultStorage = vaultStorage,
                    createEpisodeUseCase = createEpisodeUseCase,
                    appendEpisodeEventUseCase = appendEpisodeEventUseCase,
                    getEpisodesUseCase = getEpisodesUseCase,
                    getEpisodeTimelineUseCase = getEpisodeTimelineUseCase,
                    clientIdentityRepository = clientIdentityRepository,
                    ownerMemoryRepository = ownerMemoryRepository,
                    getGraphProjectionUseCase = getGraphProjectionUseCase
                ) as T
            }
        }
    }
}
