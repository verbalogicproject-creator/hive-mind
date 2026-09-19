package com.example.mcp

import com.example.domain.FakeEpisodeRepository
import com.example.domain.FakeProjectRepository
import com.example.domain.model.ClientInstallation
import com.example.domain.model.ClientLaneGrant
import com.example.domain.model.ClientProjectGrant
import com.example.domain.model.ConnectionContext
import com.example.domain.model.CredentialType
import com.example.domain.model.LaneKind
import com.example.domain.model.MemoryLane
import com.example.domain.model.AuthorizedLaneContext
import com.example.domain.model.AuthorizedProjectContext
import com.example.domain.model.PairingInvitation
import com.example.domain.model.ProjectScope
import com.example.domain.model.ServerConfig
import com.example.domain.model.Session
import com.example.domain.model.SessionInboxItem
import com.example.domain.repository.ClientIdentityRepository
import com.example.domain.security.CallerContext
import com.example.domain.usecase.GetSystemStatusUseCase
import com.example.domain.usecase.ReadStaticResourceUseCase
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

class McpTransportAuthenticationTest {

    private lateinit var manager: McpServerManager
    private lateinit var clientIdentityRepository: AuthOnlyClientIdentityRepository

    @Before
    fun setup() {
        clientIdentityRepository = AuthOnlyClientIdentityRepository()
        manager = McpServerManager(
            getSystemStatusUseCase = GetSystemStatusUseCase(FakeProjectRepository()),
            readStaticResourceUseCase = ReadStaticResourceUseCase(),
            getEpisodesUseCase = com.example.domain.usecase.GetEpisodesUseCase(FakeEpisodeRepository()),
            clientIdentityRepository = clientIdentityRepository
        )
    }

    @After
    fun tearDown() {
        manager.stop()
    }

    @Test
    fun streamableHttp_requiresBearerAndBindsSessionToInstallation() {
        val port = ServerSocket(0).use { it.localPort }
        assertTrue(
            manager.start(
                ServerConfig(host = "127.0.0.1", port = port, isReadOnly = true)
            ).isSuccess
        )

        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val endpoint = "http://127.0.0.1:$port/mcp"

        client.newCall(Request.Builder().url(endpoint).build()).execute().use { response ->
            assertEquals(401, response.code)
        }

        val initializeRequest = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer token-a")
            .header("Accept", "application/json, text/event-stream")
            .post(
                """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"raw-test","version":"1.0"}}}"""
                    .toRequestBody("application/json".toMediaType())
            )
            .build()

        client.newCall(initializeRequest).execute().use { initializeResponse ->
            assertEquals(initializeResponse.body?.string().orEmpty(), 200, initializeResponse.code)
            val sessionId = initializeResponse.header("Mcp-Session-Id").orEmpty()
            assertTrue(sessionId.isNotBlank())

            val wrongClientRequest = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer token-b")
                .header("Mcp-Session-Id", sessionId)
                .header("Mcp-Protocol-Version", "2025-11-25")
                .header("Accept", "application/json, text/event-stream")
                .post("""{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}""".toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(wrongClientRequest).execute().use { response ->
                assertEquals(403, response.code)
            }

            val missingBearerRequest = Request.Builder()
                .url(endpoint)
                .header("Mcp-Session-Id", sessionId)
                .header("Mcp-Protocol-Version", "2025-11-25")
                .header("Accept", "application/json, text/event-stream")
                .post("""{"jsonrpc":"2.0","id":3,"method":"tools/list","params":{}}""".toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(missingBearerRequest).execute().use { response ->
                assertEquals(401, response.code)
            }
        }
    }

    @Test
    fun officialKotlinClient_connectsListsToolsAndCallsSystemStatus() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        assertTrue(manager.start(ServerConfig(host = "127.0.0.1", port = port, isReadOnly = true)).isSuccess)

        val httpClient = HttpClient(CIO) { install(SSE) }
        val mcpClient = Client(
            clientInfo = Implementation(name = "project-memory-e2e", version = "1.0")
        )
        val transport = StreamableHttpClientTransport(
            client = httpClient,
            url = "http://127.0.0.1:$port/mcp",
            requestBuilder = { header(HttpHeaders.Authorization, "Bearer token-a") }
        )

        try {
            mcpClient.connect(transport)
            val toolNames = mcpClient.listTools().tools.map { it.name }
            assertTrue(toolNames.contains("system_status"))
            assertTrue(toolNames.contains("connection_context"))
            val result = mcpClient.callTool(name = "system_status", arguments = emptyMap())
            assertTrue(result.content.isNotEmpty())
            val connectionResult = mcpClient.callTool(name = "connection_context", arguments = emptyMap())
            val connectionText = connectionResult.content.joinToString("\n") { it.toString() }
            assertTrue(connectionText.contains("proj-client-a"))
            assertTrue(connectionText.contains("lane-client-a"))
            assertTrue(!connectionText.contains("proj-client-b"))
        } finally {
            mcpClient.close()
            httpClient.close()
        }
    }

    @Test
    fun streamableHttp_rejectsUntrustedHostAndOversizedBody() {
        val port = ServerSocket(0).use { it.localPort }
        assertTrue(manager.start(ServerConfig(host = "127.0.0.1", port = port)).isSuccess)
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val endpoint = "http://127.0.0.1:$port/mcp"
        val initializePayload =
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"security-test","version":"1.0"}}}"""

        val untrustedHost = Request.Builder()
            .url(endpoint)
            .header("Host", "attacker.example")
            .header("Authorization", "Bearer token-a")
            .header("Accept", "application/json, text/event-stream")
            .post(initializePayload.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(untrustedHost).execute().use { response ->
            assertEquals(403, response.code)
        }

        val oversizedBody = "x".repeat(1_048_577)
        val oversizedRequest = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer token-a")
            .header("Accept", "application/json, text/event-stream")
            .post(oversizedBody.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(oversizedRequest).execute().use { response ->
            assertEquals(413, response.code)
        }
    }

    @Test
    fun legacySse_remainsAuthenticatedAtCompatibilityPath() {
        val port = ServerSocket(0).use { it.localPort }
        assertTrue(manager.start(ServerConfig(host = "127.0.0.1", port = port)).isSuccess)
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val endpoint = "http://127.0.0.1:$port/mcp/sse"

        client.newCall(Request.Builder().url(endpoint).build()).execute().use { response ->
            assertEquals(401, response.code)
        }

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer token-a")
            .build()
        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
            val reader = response.body!!.charStream().buffered()
            assertTrue(reader.readLine().startsWith("event:"))
        }
    }

    @Test
    fun pairingRoute_redeemsWithoutBearerAndReturnsAccessTokenOnlyOnce() {
        val port = ServerSocket(0).use { it.localPort }
        assertTrue(manager.start(ServerConfig(host = "127.0.0.1", port = port)).isSuccess)

        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url("http://127.0.0.1:$port/pair")
            .header("Authorization", "Pairing pair-valid")
            .header("X-Installation-Name", "Codex laptop")
            .post(ByteArray(0).toRequestBody(null))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body!!.string()
            assertEquals(responseBody, 201, response.code)
            assertTrue(responseBody.contains("client-paired"))
            assertTrue(responseBody.contains("access-token-once"))
        }

        client.newCall(
            Request.Builder()
                .url("http://127.0.0.1:$port/pair")
                .header("Authorization", "Pairing pair-valid")
                .header("X-Installation-Name", "Replay")
                .post(ByteArray(0).toRequestBody(null))
                .build()
        ).execute().use { response ->
            assertEquals(400, response.code)
            assertTrue(!response.body!!.string().contains("access-token-once"))
        }
    }
}

private class AuthOnlyClientIdentityRepository : ClientIdentityRepository {
    private var pairingWasRedeemed = false

    override suspend fun authenticateBearerToken(token: String): CallerContext? = when (token) {
        "token-a" -> CallerContext("client-a", "Client A", "credential-a", CredentialType.ACCESS)
        "token-b" -> CallerContext("client-b", "Client B", "credential-b", CredentialType.ACCESS)
        else -> null
    }

    override suspend fun createPairingInvitation(
        targetProjectId: String,
        targetLaneKind: LaneKind,
        intendedClientName: String,
        scopes: List<String>,
        ttlMs: Long
    ): Pair<PairingInvitation, String> = unsupported()

    override suspend fun redeemPairingInvitation(
        pairingToken: String,
        installationName: String
    ): Result<Pair<ClientInstallation, String>> {
        if (pairingToken != "pair-valid" || pairingWasRedeemed) {
            return Result.failure(IllegalArgumentException("Invalid pairing token"))
        }
        pairingWasRedeemed = true
        return Result.success(
            ClientInstallation(
                id = "client-paired",
                installationName = installationName,
                createdAtEpochMs = 1L
            ) to "access-token-once"
        )
    }

    override suspend fun listInstallations(): List<ClientInstallation> = unsupported()
    override suspend fun revokeInstallation(installationId: String): Result<Unit> = unsupported()
    override suspend fun rotateAccessCredential(installationId: String): Result<String> = unsupported()
    override suspend fun getConnectionContext(caller: CallerContext): Result<ConnectionContext> =
        Result.success(
            ConnectionContext(
                clientInstallationId = caller.clientInstallationId,
                installationName = caller.installationName,
                projects = listOf(
                    AuthorizedProjectContext(
                        projectId = "proj-${caller.clientInstallationId}",
                        projectSlug = "project-${caller.clientInstallationId}",
                        projectName = "Project ${caller.clientInstallationId}",
                        scopes = listOf(ProjectScope.APPROVED_READ.value, ProjectScope.SESSION_START.value),
                        lanes = listOf(
                            AuthorizedLaneContext(
                                laneId = "lane-${caller.clientInstallationId}",
                                laneSlug = "private-${caller.clientInstallationId}",
                                laneKind = LaneKind.PRIVATE.value,
                                displayName = "Private lane",
                                canRead = true,
                                canWrite = true
                            )
                        )
                    )
                )
            )
        )
    override suspend fun getLanesForProject(projectId: String): List<MemoryLane> = unsupported()

    override suspend fun createMemoryLane(
        projectId: String,
        laneSlug: String,
        laneKind: LaneKind,
        displayName: String
    ): Result<MemoryLane> = unsupported()

    override suspend fun grantProjectScope(
        installationId: String,
        projectId: String,
        scopes: List<String>
    ): Result<ClientProjectGrant> = unsupported()

    override suspend fun grantLaneAccess(
        installationId: String,
        laneId: String,
        canRead: Boolean,
        canWrite: Boolean
    ): Result<ClientLaneGrant> = unsupported()

    override suspend fun hasProjectScope(installationId: String, projectId: String, scope: String): Boolean = false
    override suspend fun hasLaneAccess(installationId: String, laneId: String, writeRequired: Boolean): Boolean = false
    override suspend fun canAccessFileVersion(installationId: String, fileVersionId: String): Boolean = false
    override suspend fun canAccessVaultBlob(installationId: String, sha256: String): Boolean = false

    override suspend fun startSession(
        caller: CallerContext,
        projectId: String,
        laneId: String,
        reportedProvider: String?,
        reportedModel: String?
    ): Result<Session> = unsupported()

    override suspend fun closeSession(
        caller: CallerContext,
        sessionId: String,
        expectedRevision: Long
    ): Result<Unit> = unsupported()

    override suspend fun appendInboxItem(
        caller: CallerContext,
        projectId: String,
        sessionId: String,
        itemId: String,
        itemType: String,
        title: String,
        payloadJson: String,
        idempotencyKey: String,
        sourceFileVersionId: String?,
        supersedesVersionId: String?
    ): Result<SessionInboxItem> = unsupported()

    override suspend fun getInboxItems(
        caller: CallerContext,
        sessionId: String
    ): Result<List<SessionInboxItem>> = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("Not used by transport authentication tests")
}
