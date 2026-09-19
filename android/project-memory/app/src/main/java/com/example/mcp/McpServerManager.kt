package com.example.mcp

import com.example.domain.model.ProjectScope
import com.example.domain.model.ServerConfig
import com.example.domain.model.SystemStatus
import com.example.domain.repository.ClientIdentityRepository
import com.example.domain.security.Authorizer
import com.example.domain.security.CallerContext
import com.example.domain.security.SecurityPolicy
import com.example.domain.usecase.AppendEpisodeEventUseCase
import com.example.domain.usecase.CreateEpisodeUseCase
import com.example.domain.usecase.GetEpisodeTimelineUseCase
import com.example.domain.usecase.GetEpisodesUseCase
import com.example.domain.usecase.GetFileVersionsUseCase
import com.example.domain.usecase.GetSystemStatusUseCase
import com.example.domain.usecase.IndexFileContentUseCase
import com.example.domain.usecase.ReadStaticResourceUseCase
import com.example.domain.usecase.ReadVaultBlobUseCase
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.util.AttributeKey
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.concurrent.ConcurrentHashMap

data class ServerRuntimeState(
    val isRunning: Boolean = false,
    val host: String = SecurityPolicy.LOOPBACK_HOST,
    val port: Int = SecurityPolicy.DEFAULT_PORT,
    val isReadOnly: Boolean = true,
    val startTimeEpochMs: Long = 0L,
    val clientRequestsHandled: Long = 0L,
    val lastError: String? = null
) {
    val uptimeMs: Long
        get() = if (isRunning && startTimeEpochMs > 0L) System.currentTimeMillis() - startTimeEpochMs else 0L
}

private data class AuthenticatedSseSession(
    val transport: SseServerTransport,
    val caller: CallerContext
)

private data class StreamableSessionOwner(
    val clientInstallationId: String
)

private const val MCP_SESSION_HEADER = "Mcp-Session-Id"
private const val MAX_MCP_REQUEST_BYTES = 1_048_576L
private const val MAX_STREAMABLE_SESSIONS = 4_096

@Serializable
private data class PairingRedeemResponse(
    val installationId: String,
    val installationName: String,
    val accessToken: String,
    val tokenType: String
)

class McpServerManager(
    private val getSystemStatusUseCase: GetSystemStatusUseCase,
    private val readStaticResourceUseCase: ReadStaticResourceUseCase,
    private val indexFileContentUseCase: IndexFileContentUseCase? = null,
    private val getFileVersionsUseCase: GetFileVersionsUseCase? = null,
    private val readVaultBlobUseCase: ReadVaultBlobUseCase? = null,
    private val createEpisodeUseCase: CreateEpisodeUseCase? = null,
    private val appendEpisodeEventUseCase: AppendEpisodeEventUseCase? = null,
    private val getEpisodesUseCase: GetEpisodesUseCase? = null,
    private val getEpisodeTimelineUseCase: GetEpisodeTimelineUseCase? = null,
    val clientIdentityRepository: ClientIdentityRepository? = null,
    val authorizer: Authorizer = Authorizer(clientIdentityRepository),
    private val securityPolicy: SecurityPolicy = SecurityPolicy()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val _runtimeState = MutableStateFlow(ServerRuntimeState())
    val runtimeState: StateFlow<ServerRuntimeState> = _runtimeState.asStateFlow()

    @Volatile
    private var engine: EmbeddedServer<*, *>? = null

    @Volatile
    private var activeConfig: ServerConfig = ServerConfig()

    fun getActiveConfig(): ServerConfig = activeConfig

    fun buildMcpServer(caller: CallerContext): Server {
        val server = Server(
            serverInfo = Implementation(
                name = "project-memory-provider",
                version = "0.1.0"
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true),
                    resources = ServerCapabilities.Resources(listChanged = true)
                )
            )
        )

        // 1. system_status tool
        server.addTool(
            name = "system_status",
            description = "Query health, runtime configuration, active project counts, and security invariants of Project Memory Provider",
            inputSchema = ToolSchema()
        ) { _ ->
            val status = getSystemStatusUseCase(
                isRunning = _runtimeState.value.isRunning,
                config = activeConfig,
                uptimeMs = _runtimeState.value.uptimeMs
            )
            _runtimeState.value = _runtimeState.value.copy(
                clientRequestsHandled = _runtimeState.value.clientRequestsHandled + 1
            )
            CallToolResult(content = listOf(TextContent(json.encodeToString(status))))
        }

        // 2. connection_context tool
        server.addTool(
            name = "connection_context",
            description = "List only the authenticated installation and its active authorized projects and memory lanes",
            inputSchema = ToolSchema(
                properties = buildJsonObject { },
                required = emptyList()
            ),
            outputSchema = connectionContextOutputSchema()
        ) { _ ->
            _runtimeState.value = _runtimeState.value.copy(
                clientRequestsHandled = _runtimeState.value.clientRequestsHandled + 1
            )
            val repository = clientIdentityRepository
            if (repository == null) {
                CallToolResult(
                    content = listOf(TextContent("""{"error":"ClientIdentityRepository not initialized"}""")),
                    isError = true
                )
            } else {
                repository.getConnectionContext(caller).fold(
                    onSuccess = { context ->
                        val structured = json.parseToJsonElement(json.encodeToString(context)).jsonObject
                        CallToolResult(
                            content = listOf(TextContent(json.encodeToString(context))),
                            structuredContent = structured
                        )
                    },
                    onFailure = { error ->
                        CallToolResult(
                            content = listOf(TextContent("""{"error":"${error.message ?: "Connection context unavailable"}"}""")),
                            isError = true
                        )
                    }
                )
            }
        }

        // 3. index_file_content tool
        server.addTool(
            name = "index_file_content",
            description = "Index and chunk a project file into the content-addressed vault with SHA-256 deduplication and version tracking",
            inputSchema = ToolSchema()
        ) { request ->
            _runtimeState.value = _runtimeState.value.copy(
                clientRequestsHandled = _runtimeState.value.clientRequestsHandled + 1
            )
            val arguments = request.arguments
            val projectId = arguments?.get("projectId")?.jsonPrimitive?.content ?: ""
            val laneId = arguments?.get("laneId")?.jsonPrimitive?.content ?: ""
            val path = arguments?.get("path")?.jsonPrimitive?.content ?: ""
            val content = arguments?.get("content")?.jsonPrimitive?.content ?: ""
            val mimeType = arguments?.get("mimeType")?.jsonPrimitive?.content ?: "text/plain"

            if (indexFileContentUseCase == null) {
                CallToolResult(content = listOf(TextContent("""{"error":"IndexFileContentUseCase not initialized"}""")), isError = true)
            } else if (projectId.isBlank() || laneId.isBlank() || path.isBlank()) {
                CallToolResult(content = listOf(TextContent("""{"error":"projectId, laneId, and path arguments are required"}""")), isError = true)
            } else if (!securityPolicy.canExecuteMutation(activeConfig)) {
                CallToolResult(content = listOf(TextContent("""{"error":"Server is read-only"}""")), isError = true)
            } else if (!caller.isSystemOwner) {
                CallToolResult(content = listOf(TextContent("""{"error":"Forbidden: legacy file indexing is owner-only; external clients must append to a granted session inbox"}""")), isError = true)
            } else {
                if (!authorizer.checkProjectScope(caller, projectId, ProjectScope.INBOX_APPEND)) {
                    CallToolResult(content = listOf(TextContent("""{"error":"Forbidden: Caller lacks inbox:append or write scope for project '$projectId'"}""")), isError = true)
                } else {
                    val indexResult = indexFileContentUseCase(projectId, laneId, path, content, mimeType)
                    indexResult.fold(
                        onSuccess = { res ->
                            val output = """
                                {
                                  "fileVersionId": "${res.fileVersion.id}",
                                  "projectId": "${res.fileVersion.projectId}",
                                  "path": "${res.fileVersion.path}",
                                  "sha256": "${res.fileVersion.sha256}",
                                  "versionNumber": ${res.fileVersion.versionNumber},
                                  "sizeBytes": ${res.fileVersion.sizeBytes},
                                  "chunkCount": ${res.chunkCount},
                                  "isDeduplicated": ${res.isDeduplicated}
                                }
                            """.trimIndent()
                            CallToolResult(content = listOf(TextContent(output)))
                        },
                        onFailure = { err ->
                            CallToolResult(content = listOf(TextContent("""{"error":"${err.message}"}""")), isError = true)
                        }
                    )
                }
            }
        }

        // 3. get_file_version tool
        server.addTool(
            name = "get_file_version",
            description = "Retrieve the latest version and content chunk breakdown of a tracked project file",
            inputSchema = ToolSchema()
        ) { request ->
            _runtimeState.value = _runtimeState.value.copy(
                clientRequestsHandled = _runtimeState.value.clientRequestsHandled + 1
            )
            val arguments = request.arguments
            val projectId = arguments?.get("projectId")?.jsonPrimitive?.content ?: ""
            val laneId = arguments?.get("laneId")?.jsonPrimitive?.content ?: ""
            val path = arguments?.get("path")?.jsonPrimitive?.content ?: ""

            if (getFileVersionsUseCase == null) {
                CallToolResult(content = listOf(TextContent("""{"error":"GetFileVersionsUseCase not initialized"}""")), isError = true)
            } else if (projectId.isBlank() || laneId.isBlank() || path.isBlank()) {
                CallToolResult(content = listOf(TextContent("""{"error":"projectId, laneId, and path arguments are required"}""")), isError = true)
            } else if (!authorizer.checkProjectScope(caller, projectId, ProjectScope.APPROVED_READ)) {
                CallToolResult(content = listOf(TextContent("""{"error":"Forbidden: Caller lacks approved:read scope for project '$projectId'"}""")), isError = true)
            } else {
                val latest = getFileVersionsUseCase.getLatestVersion(projectId, laneId, path)
                if (latest == null) {
                    CallToolResult(content = listOf(TextContent("""{"error":"File not found for project '$projectId' and path '$path'"}""")), isError = true)
                } else {
                    if (!authorizer.checkFileVersionAccess(caller, latest.id)) {
                        CallToolResult(content = listOf(TextContent("""{"error":"Forbidden: Caller lacks access to file version '${latest.id}'"}""")), isError = true)
                    } else {
                        val chunks = getFileVersionsUseCase.getChunks(latest.id)
                        val output = """
                            {
                              "id": "${latest.id}",
                              "projectId": "${latest.projectId}",
                              "path": "${latest.path}",
                              "sha256": "${latest.sha256}",
                              "versionNumber": ${latest.versionNumber},
                              "sizeBytes": ${latest.sizeBytes},
                              "chunkCount": ${chunks.size},
                              "chunks": [
                                ${chunks.joinToString(",") { """{"index":${it.chunkIndex},"tokens":${it.tokenCountEstimate},"sha256":"${it.sha256}"}""" }}
                              ]
                            }
                        """.trimIndent()
                        CallToolResult(content = listOf(TextContent(output)))
                    }
                }
            }
        }

        // 4. read_vault_blob tool (Strict Vault Access check: only via authorized FileVersion)
        server.addTool(
            name = "read_vault_blob",
            description = "Retrieve content directly from the SHA-256 content-addressed vault",
            inputSchema = ToolSchema()
        ) { request ->
            _runtimeState.value = _runtimeState.value.copy(
                clientRequestsHandled = _runtimeState.value.clientRequestsHandled + 1
            )
            val sha256 = request.arguments?.get("sha256")?.jsonPrimitive?.content ?: ""

            if (readVaultBlobUseCase == null) {
                CallToolResult(content = listOf(TextContent("""{"error":"ReadVaultBlobUseCase not initialized"}""")), isError = true)
            } else if (!authorizer.checkVaultBlobAccess(caller, sha256)) {
                CallToolResult(content = listOf(TextContent("""{"error":"Forbidden: Caller is not authorized to read vault blob with sha256 '$sha256'"}""")), isError = true)
            } else {
                val content = readVaultBlobUseCase.getText(sha256)
                if (content == null) {
                    CallToolResult(content = listOf(TextContent("""{"error":"Blob not found with SHA-256 '$sha256'"}""")), isError = true)
                } else {
                    CallToolResult(content = listOf(TextContent(content)))
                }
            }
        }

        // 5. create_episode tool
        server.addTool(
            name = "create_episode",
            description = "Start a new interaction episode recording task context, provenance, and intent",
            inputSchema = ToolSchema()
        ) { request ->
            _runtimeState.value = _runtimeState.value.copy(
                clientRequestsHandled = _runtimeState.value.clientRequestsHandled + 1
            )
            val arguments = request.arguments
            val projectId = arguments?.get("projectId")?.jsonPrimitive?.content ?: ""
            val laneId = arguments?.get("laneId")?.jsonPrimitive?.content ?: ""
            val title = arguments?.get("title")?.jsonPrimitive?.content ?: ""
            val summary = arguments?.get("summary")?.jsonPrimitive?.content ?: ""
            val source = arguments?.get("source")?.jsonPrimitive?.content ?: "agent"
            val tagsRaw = arguments?.get("tags")?.jsonPrimitive?.content ?: ""
            val tags = tagsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            if (createEpisodeUseCase == null) {
                CallToolResult(content = listOf(TextContent("""{"error":"CreateEpisodeUseCase not initialized"}""")), isError = true)
            } else if (projectId.isBlank() || laneId.isBlank() || title.isBlank()) {
                CallToolResult(content = listOf(TextContent("""{"error":"projectId, laneId, and title arguments are required"}""")), isError = true)
            } else if (!securityPolicy.canExecuteMutation(activeConfig)) {
                CallToolResult(content = listOf(TextContent("""{"error":"Server is read-only"}""")), isError = true)
            } else if (!caller.isSystemOwner) {
                CallToolResult(content = listOf(TextContent("""{"error":"Forbidden: legacy episodes are owner-only; external clients must use persistent sessions and inbox items"}""")), isError = true)
            } else if (!authorizer.checkProjectScope(caller, projectId, ProjectScope.SESSION_START)) {
                CallToolResult(content = listOf(TextContent("""{"error":"Forbidden: Caller lacks session:start or write scope for project '$projectId'"}""")), isError = true)
            } else {
                val res = createEpisodeUseCase(projectId, laneId, title, summary, source, tags)
                res.fold(
                    onSuccess = { ep -> CallToolResult(content = listOf(TextContent(json.encodeToString(ep)))) },
                    onFailure = { err -> CallToolResult(content = listOf(TextContent("""{"error":"${err.message}"}""")), isError = true) }
                )
            }
        }

        // 6. log_event tool
        server.addTool(
            name = "log_event",
            description = "Append an immutable, sequential event to an interaction episode with optional file version provenance",
            inputSchema = ToolSchema()
        ) { request ->
            _runtimeState.value = _runtimeState.value.copy(
                clientRequestsHandled = _runtimeState.value.clientRequestsHandled + 1
            )
            val arguments = request.arguments
            val episodeId = arguments?.get("episodeId")?.jsonPrimitive?.content ?: ""
            val eventType = arguments?.get("eventType")?.jsonPrimitive?.content ?: "message"
            val actor = arguments?.get("actor")?.jsonPrimitive?.content ?: "agent"
            val payload = arguments?.get("payload")?.jsonPrimitive?.content ?: ""
            val fileVersionId = arguments?.get("fileVersionId")?.jsonPrimitive?.content

            if (appendEpisodeEventUseCase == null) {
                CallToolResult(content = listOf(TextContent("""{"error":"AppendEpisodeEventUseCase not initialized"}""")), isError = true)
            } else if (episodeId.isBlank()) {
                CallToolResult(content = listOf(TextContent("""{"error":"episodeId argument is required"}""")), isError = true)
            } else if (!securityPolicy.canExecuteMutation(activeConfig)) {
                CallToolResult(content = listOf(TextContent("""{"error":"Server is read-only"}""")), isError = true)
            } else if (!caller.isSystemOwner) {
                CallToolResult(content = listOf(TextContent("""{"error":"Forbidden: legacy episode mutation is owner-only"}""")), isError = true)
            } else {
                val res = appendEpisodeEventUseCase(episodeId, eventType, actor, payload, fileVersionId)
                res.fold(
                    onSuccess = { ev -> CallToolResult(content = listOf(TextContent(json.encodeToString(ev)))) },
                    onFailure = { err -> CallToolResult(content = listOf(TextContent("""{"error":"${err.message}"}""")), isError = true) }
                )
            }
        }

        // 7. get_episode_timeline tool
        server.addTool(
            name = "get_episode_timeline",
            description = "Retrieve the chronological event log with sequence numbers and provenance for an episode",
            inputSchema = ToolSchema()
        ) { request ->
            _runtimeState.value = _runtimeState.value.copy(
                clientRequestsHandled = _runtimeState.value.clientRequestsHandled + 1
            )
            val episodeId = request.arguments?.get("episodeId")?.jsonPrimitive?.content ?: ""
            if (getEpisodeTimelineUseCase == null || getEpisodesUseCase == null) {
                CallToolResult(content = listOf(TextContent("""{"error":"Episode use cases not initialized"}""")), isError = true)
            } else if (episodeId.isBlank()) {
                CallToolResult(content = listOf(TextContent("""{"error":"episodeId argument is required"}""")), isError = true)
            } else {
                val episode = getEpisodesUseCase.getEpisode(episodeId)
                if (episode == null) {
                    CallToolResult(content = listOf(TextContent("""{"error":"Episode not found"}""")), isError = true)
                } else if (!authorizer.checkProjectScope(caller, episode.projectId, ProjectScope.APPROVED_READ) ||
                    !authorizer.checkLaneAccess(caller, episode.laneId, writeRequired = false)
                ) {
                    CallToolResult(content = listOf(TextContent("""{"error":"Forbidden: Caller cannot read this episode"}""")), isError = true)
                } else {
                    val events = getEpisodeTimelineUseCase(episodeId)
                    CallToolResult(content = listOf(TextContent(json.encodeToString(events))))
                }
            }
        }

        // 8. start_session tool (v0.1 Contract)
        server.addTool(
            name = "start_session",
            description = "Start a persistent working session for an authorized client within a specific memory lane",
            inputSchema = ToolSchema()
        ) { request ->
            _runtimeState.value = _runtimeState.value.copy(
                clientRequestsHandled = _runtimeState.value.clientRequestsHandled + 1
            )
            val arguments = request.arguments
            val projectId = arguments?.get("projectId")?.jsonPrimitive?.content ?: ""
            val laneId = arguments?.get("laneId")?.jsonPrimitive?.content ?: ""
            val reportedProvider = arguments?.get("reportedProvider")?.jsonPrimitive?.content
            val reportedModel = arguments?.get("reportedModel")?.jsonPrimitive?.content

            if (clientIdentityRepository == null) {
                CallToolResult(content = listOf(TextContent("""{"error":"ClientIdentityRepository not initialized"}""")), isError = true)
            } else if (projectId.isBlank() || laneId.isBlank()) {
                CallToolResult(content = listOf(TextContent("""{"error":"projectId and laneId arguments are required"}""")), isError = true)
            } else if (!securityPolicy.canExecuteMutation(activeConfig)) {
                CallToolResult(content = listOf(TextContent("""{"error":"Server is read-only"}""")), isError = true)
            } else {
                val res = clientIdentityRepository.startSession(
                    caller = caller,
                    projectId = projectId,
                    laneId = laneId,
                    reportedProvider = reportedProvider,
                    reportedModel = reportedModel
                )
                res.fold(
                    onSuccess = { session ->
                        val output = """
                            {
                              "sessionId": "${session.id}",
                              "projectId": "${session.projectId}",
                              "clientInstallationId": "${session.clientInstallationId}",
                              "laneId": "${session.laneId}",
                              "reportedProvider": "${session.reportedProvider ?: "unknown"}",
                              "reportedModel": "${session.reportedModel ?: "unknown"}",
                              "status": "${session.status.value}",
                              "startedAt": ${session.startedAtEpochMs},
                              "revision": ${session.revision}
                            }
                        """.trimIndent()
                        CallToolResult(content = listOf(TextContent(output)))
                    },
                    onFailure = { err ->
                        CallToolResult(content = listOf(TextContent("""{"error":"${err.message}"}""")), isError = true)
                    }
                )
            }
        }

        // 9. close_session tool (v0.1 Contract with optimistic revision check)
        server.addTool(
            name = "close_session",
            description = "Close an active working session with optimistic revision check",
            inputSchema = ToolSchema()
        ) { request ->
            _runtimeState.value = _runtimeState.value.copy(
                clientRequestsHandled = _runtimeState.value.clientRequestsHandled + 1
            )
            val arguments = request.arguments
            val sessionId = arguments?.get("sessionId")?.jsonPrimitive?.content ?: ""
            val revision = arguments?.get("expectedRevision")?.jsonPrimitive?.content?.toLongOrNull() ?: 1L

            if (clientIdentityRepository == null) {
                CallToolResult(content = listOf(TextContent("""{"error":"ClientIdentityRepository not initialized"}""")), isError = true)
            } else if (sessionId.isBlank()) {
                CallToolResult(content = listOf(TextContent("""{"error":"sessionId argument is required"}""")), isError = true)
            } else if (!securityPolicy.canExecuteMutation(activeConfig)) {
                CallToolResult(content = listOf(TextContent("""{"error":"Server is read-only"}""")), isError = true)
            } else {
                val res = clientIdentityRepository.closeSession(caller, sessionId, revision)
                res.fold(
                    onSuccess = { CallToolResult(content = listOf(TextContent("""{"status":"closed","sessionId":"$sessionId"}"""))) },
                    onFailure = { err -> CallToolResult(content = listOf(TextContent("""{"error":"${err.message}"}""")), isError = true) }
                )
            }
        }

        // 10. append_inbox_item tool (v0.1 Contract with version addressability & idempotency)
        server.addTool(
            name = "append_inbox_item",
            description = "Append a persistent, immutable, version-addressable item to a session inbox",
            inputSchema = ToolSchema()
        ) { request ->
            _runtimeState.value = _runtimeState.value.copy(
                clientRequestsHandled = _runtimeState.value.clientRequestsHandled + 1
            )
            val arguments = request.arguments
            val projectId = arguments?.get("projectId")?.jsonPrimitive?.content ?: ""
            val sessionId = arguments?.get("sessionId")?.jsonPrimitive?.content ?: ""
            val itemId = arguments?.get("itemId")?.jsonPrimitive?.content ?: ""
            val itemType = arguments?.get("itemType")?.jsonPrimitive?.content ?: "checkpoint"
            val title = arguments?.get("title")?.jsonPrimitive?.content ?: ""
            val payloadJson = arguments?.get("payloadJson")?.jsonPrimitive?.content ?: "{}"
            val idempotencyKey = arguments?.get("idempotencyKey")?.jsonPrimitive?.content ?: ""
            val sourceFileVersionId = arguments?.get("sourceFileVersionId")?.jsonPrimitive?.content
            val supersedesVersionId = arguments?.get("supersedesVersionId")?.jsonPrimitive?.content

            if (clientIdentityRepository == null) {
                CallToolResult(content = listOf(TextContent("""{"error":"ClientIdentityRepository not initialized"}""")), isError = true)
            } else if (projectId.isBlank() || sessionId.isBlank() || itemId.isBlank() || idempotencyKey.isBlank() || title.isBlank()) {
                CallToolResult(content = listOf(TextContent("""{"error":"projectId, sessionId, itemId, title, and idempotencyKey are required"}""")), isError = true)
            } else if (!securityPolicy.canExecuteMutation(activeConfig)) {
                CallToolResult(content = listOf(TextContent("""{"error":"Server is read-only"}""")), isError = true)
            } else {
                val res = clientIdentityRepository.appendInboxItem(
                    caller = caller,
                    projectId = projectId,
                    sessionId = sessionId,
                    itemId = itemId,
                    itemType = itemType,
                    title = title,
                    payloadJson = payloadJson,
                    idempotencyKey = idempotencyKey,
                    sourceFileVersionId = sourceFileVersionId,
                    supersedesVersionId = supersedesVersionId
                )
                res.fold(
                    onSuccess = { item ->
                        val output = """
                            {
                              "itemId": "${item.itemId}",
                              "versionId": "${item.versionId}",
                              "supersedesVersionId": ${item.supersedesVersionId?.let { "\"$it\"" } ?: "null"},
                              "sessionId": "${item.sessionId}",
                              "sequenceNumber": ${item.sequenceNumber},
                              "itemType": "${item.itemType}",
                              "title": "${item.title}",
                              "payloadJson": ${item.payloadJson},
                              "sourceFileVersionId": ${item.sourceFileVersionId?.let { "\"$it\"" } ?: "null"},
                              "contentHash": "${item.contentHash}",
                              "createdAt": ${item.createdAtEpochMs}
                            }
                        """.trimIndent()
                        CallToolResult(content = listOf(TextContent(output)))
                    },
                    onFailure = { err ->
                        CallToolResult(content = listOf(TextContent("""{"error":"${err.message}"}""")), isError = true)
                    }
                )
            }
        }

        // 11. get_inbox_items tool
        server.addTool(
            name = "get_inbox_items",
            description = "Retrieve all items appended to a session inbox in sequential order",
            inputSchema = ToolSchema()
        ) { request ->
            _runtimeState.value = _runtimeState.value.copy(
                clientRequestsHandled = _runtimeState.value.clientRequestsHandled + 1
            )
            val sessionId = request.arguments?.get("sessionId")?.jsonPrimitive?.content ?: ""

            if (clientIdentityRepository == null) {
                CallToolResult(content = listOf(TextContent("""{"error":"ClientIdentityRepository not initialized"}""")), isError = true)
            } else if (sessionId.isBlank()) {
                CallToolResult(content = listOf(TextContent("""{"error":"sessionId argument is required"}""")), isError = true)
            } else {
                val res = clientIdentityRepository.getInboxItems(caller, sessionId)
                res.fold(
                    onSuccess = { items ->
                        val output = items.joinToString(",", prefix = "[", postfix = "]") { item ->
                            """{"itemId":"${item.itemId}","versionId":"${item.versionId}","sequenceNumber":${item.sequenceNumber},"title":"${item.title}","contentHash":"${item.contentHash}"}"""
                        }
                        CallToolResult(content = listOf(TextContent(output)))
                    },
                    onFailure = { err ->
                        CallToolResult(content = listOf(TextContent("""{"error":"${err.message}"}""")), isError = true)
                    }
                )
            }
        }

        // Register static project://manifest resource
        server.addResource(
            uri = "project://manifest",
            name = "Project Memory Manifest",
            description = "Static manifest declaring provider capabilities, contract schemas, and versioning",
            mimeType = "application/json"
        ) { request ->
            val result = readStaticResourceUseCase(request.uri)
            _runtimeState.value = _runtimeState.value.copy(
                clientRequestsHandled = _runtimeState.value.clientRequestsHandled + 1
            )
            val text = result.getOrElse { """{"error":"Manifest unavailable"}""" }
            io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult(
                contents = listOf(
                    io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents(
                        uri = request.uri,
                        mimeType = "application/json",
                        text = text
                    )
                )
            )
        }

        return server
    }

    private fun connectionContextOutputSchema(): ToolSchema = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("clientInstallationId") { put("type", "string") }
            putJsonObject("installationName") { put("type", "string") }
            putJsonObject("projects") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("projectId") { put("type", "string") }
                        putJsonObject("projectSlug") { put("type", "string") }
                        putJsonObject("projectName") { put("type", "string") }
                        putJsonObject("scopes") {
                            put("type", "array")
                            putJsonObject("items") { put("type", "string") }
                        }
                        putJsonObject("lanes") {
                            put("type", "array")
                            putJsonObject("items") {
                                put("type", "object")
                                putJsonObject("properties") {
                                    putJsonObject("laneId") { put("type", "string") }
                                    putJsonObject("laneSlug") { put("type", "string") }
                                    putJsonObject("laneKind") { put("type", "string") }
                                    putJsonObject("displayName") { put("type", "string") }
                                    putJsonObject("canRead") { put("type", "boolean") }
                                    putJsonObject("canWrite") { put("type", "boolean") }
                                }
                                putJsonArray("required") {
                                    add("laneId")
                                    add("laneSlug")
                                    add("laneKind")
                                    add("displayName")
                                    add("canRead")
                                    add("canWrite")
                                }
                            }
                        }
                    }
                    putJsonArray("required") {
                        add("projectId")
                        add("projectSlug")
                        add("projectName")
                        add("scopes")
                        add("lanes")
                    }
                }
            }
        },
        required = listOf("clientInstallationId", "installationName", "projects")
    )

    @Synchronized
    fun start(config: ServerConfig = ServerConfig()): Result<Unit> {
        if (_runtimeState.value.isRunning) {
            return Result.success(Unit)
        }

        val validation = securityPolicy.validateConfig(config)
        if (validation.isFailure) {
            val error = validation.exceptionOrNull()?.message ?: "Security validation failed"
            _runtimeState.value = _runtimeState.value.copy(lastError = error)
            return Result.failure(validation.exceptionOrNull()!!)
        }

        return try {
            activeConfig = config
            val legacySseSessions = ConcurrentHashMap<String, AuthenticatedSseSession>()
            val streamableSessionOwners = ConcurrentHashMap<String, StreamableSessionOwner>()
            // appfactory: release-server-ok -- The release app is intentionally an MCP
            // server. Every request requires a revocable per-client bearer credential,
            // and each stateful transport session is bound to its authenticated client installation.
            val callerKey = AttributeKey<CallerContext>("AuthenticatedCallerContext")
            val sessionOwnerPlugin = createApplicationPlugin(name = "McpSessionOwnerBinding") {
                onCallRespond { call, _ ->
                    if (call.request.path() != "/mcp" || !call.attributes.contains(callerKey)) {
                        return@onCallRespond
                    }
                    val caller = call.attributes[callerKey]
                    val createdSessionId = call.response.headers[MCP_SESSION_HEADER]
                    if (!createdSessionId.isNullOrBlank()) {
                        streamableSessionOwners.putIfAbsent(
                            createdSessionId,
                            StreamableSessionOwner(caller.clientInstallationId)
                        )
                    }
                    val responseCode = call.response.status()?.value
                    if (call.request.httpMethod == HttpMethod.Delete && responseCode != null && responseCode in 200..299) {
                        call.request.header(MCP_SESSION_HEADER)?.let(streamableSessionOwners::remove)
                    }
                }
            }

            val newEngine = embeddedServer(CIO, port = config.port, host = config.host) {
                intercept(ApplicationCallPipeline.Plugins) {
                    val call = context
                    // Pairing uses a separate short-lived, one-time secret. It must be
                    // redeemable before the client owns an MCP bearer credential.
                    if (call.request.path() == "/pair") return@intercept

                    val authenticatedCaller = authorizer.authenticateBearerToken(
                        call.request.header("Authorization")
                    )
                    if (authenticatedCaller == null) {
                        call.respond(HttpStatusCode.Unauthorized, "Missing or invalid Bearer token")
                        finish()
                        return@intercept
                    }
                    call.attributes.put(callerKey, authenticatedCaller)

                    if (call.request.path() == "/mcp") {
                        val declaredLength = call.request.header("Content-Length")?.toLongOrNull()
                        if (declaredLength != null && declaredLength > MAX_MCP_REQUEST_BYTES) {
                            call.respond(HttpStatusCode.PayloadTooLarge, "MCP request exceeds 1 MiB")
                            finish()
                            return@intercept
                        }

                        val sessionId = call.request.header(MCP_SESSION_HEADER)
                        if (sessionId.isNullOrBlank() && streamableSessionOwners.size >= MAX_STREAMABLE_SESSIONS) {
                            call.respond(HttpStatusCode.ServiceUnavailable, "MCP session capacity reached")
                            finish()
                            return@intercept
                        }

                        if (!sessionId.isNullOrBlank()) {
                            val owner = streamableSessionOwners[sessionId]
                            if (owner != null && owner.clientInstallationId != authenticatedCaller.clientInstallationId) {
                                call.respond(HttpStatusCode.Forbidden, "Session belongs to another client installation")
                                finish()
                                return@intercept
                            }
                        }
                    }
                }
                install(sessionOwnerPlugin)
                install(RequestBodyLimit) {
                    bodyLimit { call ->
                        if (call.request.path() == "/mcp") MAX_MCP_REQUEST_BYTES else Long.MAX_VALUE
                    }
                }
                mcpStreamableHttp(
                    path = "/mcp",
                    enableDnsRebindingProtection = true
                ) {
                    buildMcpServer(call.attributes[callerKey])
                }
                routing {
                    post("/pair") {
                        val repository = clientIdentityRepository
                        if (repository == null) {
                            call.respond(HttpStatusCode.ServiceUnavailable, "Pairing is unavailable")
                            return@post
                        }

                        val pairingAuthorization = call.request.header("Authorization").orEmpty()
                        val pairingToken = pairingAuthorization
                            .takeIf { it.startsWith("Pairing ", ignoreCase = true) }
                            ?.substringAfter(' ')
                            ?.trim()
                            .orEmpty()
                        val installationName = call.request.header("X-Installation-Name").orEmpty()
                        if (pairingToken.isBlank() || pairingToken.length > 512) {
                            call.respond(HttpStatusCode.Unauthorized, "Missing or invalid Pairing token")
                            return@post
                        }
                        if (installationName.length > 120) {
                            call.respond(HttpStatusCode.BadRequest, "Installation name is too long")
                            return@post
                        }

                        repository.redeemPairingInvitation(
                            pairingToken = pairingToken,
                            installationName = installationName.trim()
                        ).fold(
                            onSuccess = { (installation, accessToken) ->
                                val response = PairingRedeemResponse(
                                    installationId = installation.id,
                                    installationName = installation.installationName,
                                    accessToken = accessToken,
                                    tokenType = "Bearer"
                                )
                                call.respondText(
                                    text = json.encodeToString(response),
                                    contentType = ContentType.Application.Json,
                                    status = HttpStatusCode.Created
                                )
                            },
                            onFailure = {
                                // Avoid exposing whether a token was unknown, expired, or consumed.
                                call.respond(HttpStatusCode.BadRequest, "Pairing failed")
                            }
                        )
                    }

                    route("/mcp/sse") {
                        sse {
                            if (!call.attributes.contains(callerKey)) {
                                return@sse
                            }
                            val authenticatedCaller = call.attributes[callerKey]

                            val transport = SseServerTransport("", this)
                            val mcpServer = buildMcpServer(authenticatedCaller)
                            legacySseSessions[transport.sessionId] = AuthenticatedSseSession(
                                transport = transport,
                                caller = authenticatedCaller
                            )
                            mcpServer.onClose {
                                legacySseSessions.remove(transport.sessionId)
                            }
                            mcpServer.createSession(transport)

                            try {
                                awaitCancellation()
                            } finally {
                                legacySseSessions.remove(transport.sessionId)
                            }
                        }

                        post {
                            if (!call.attributes.contains(callerKey)) {
                                return@post
                            }
                            val authenticatedCaller = call.attributes[callerKey]

                            val sessionId = call.request.queryParameters["sessionId"]
                            if (sessionId.isNullOrBlank()) {
                                call.respond(HttpStatusCode.BadRequest, "sessionId query parameter is required")
                                return@post
                            }

                            val boundSession = legacySseSessions[sessionId]
                            if (boundSession == null) {
                                call.respond(HttpStatusCode.NotFound, "Session not found")
                                return@post
                            }
                            if (boundSession.caller.clientInstallationId != authenticatedCaller.clientInstallationId) {
                                call.respond(HttpStatusCode.Forbidden, "Session belongs to another client installation")
                                return@post
                            }

                            boundSession.transport.handlePostMessage(call)
                        }
                    }
                }
            }

            newEngine.start(wait = false)
            engine = newEngine

            _runtimeState.value = ServerRuntimeState(
                isRunning = true,
                host = config.host,
                port = config.port,
                isReadOnly = config.isReadOnly,
                startTimeEpochMs = System.currentTimeMillis(),
                clientRequestsHandled = 0L,
                lastError = null
            )
            Result.success(Unit)
        } catch (e: Exception) {
            _runtimeState.value = _runtimeState.value.copy(
                isRunning = false,
                lastError = e.message ?: "Failed to start MCP server"
            )
            Result.failure(e)
        }
    }

    @Synchronized
    fun stop(): Result<Unit> {
        return try {
            engine?.stop(gracePeriodMillis = 500, timeoutMillis = 1500)
            engine = null
            _runtimeState.value = _runtimeState.value.copy(
                isRunning = false,
                startTimeEpochMs = 0L
            )
            Result.success(Unit)
        } catch (e: Exception) {
            _runtimeState.value = _runtimeState.value.copy(
                lastError = e.message ?: "Failed to stop MCP server"
            )
            Result.failure(e)
        }
    }

    suspend fun executeSystemStatusToolDirectly(): SystemStatus {
        return getSystemStatusUseCase(
            isRunning = _runtimeState.value.isRunning,
            config = activeConfig,
            uptimeMs = _runtimeState.value.uptimeMs
        )
    }

    fun readStaticResourceDirectly(uri: String): Result<String> {
        return readStaticResourceUseCase(uri)
    }

    suspend fun executeCreateEpisodeDirectly(
        projectId: String,
        laneId: String,
        title: String,
        summary: String = "",
        source: String = "agent",
        tags: List<String> = emptyList()
    ): Result<com.example.domain.model.Episode> {
        return createEpisodeUseCase?.invoke(projectId, laneId, title, summary, source, tags)
            ?: Result.failure(IllegalStateException("CreateEpisodeUseCase not initialized"))
    }

    suspend fun executeLogEventDirectly(
        episodeId: String,
        eventType: String,
        actor: String,
        payload: String,
        fileVersionId: String? = null
    ): Result<com.example.domain.model.EpisodeEvent> {
        return appendEpisodeEventUseCase?.invoke(episodeId, eventType, actor, payload, fileVersionId)
            ?: Result.failure(IllegalStateException("AppendEpisodeEventUseCase not initialized"))
    }

    suspend fun executeGetTimelineDirectly(episodeId: String): List<com.example.domain.model.EpisodeEvent> {
        return getEpisodeTimelineUseCase?.invoke(episodeId) ?: emptyList()
    }
}
