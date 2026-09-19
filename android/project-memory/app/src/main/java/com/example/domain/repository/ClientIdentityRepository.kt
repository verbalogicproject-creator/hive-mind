package com.example.domain.repository

import com.example.domain.model.ClientCredential
import com.example.domain.model.ClientInstallation
import com.example.domain.model.ClientLaneGrant
import com.example.domain.model.ClientProjectGrant
import com.example.domain.model.ConnectionContext
import com.example.domain.model.LaneKind
import com.example.domain.model.MemoryLane
import com.example.domain.model.PairingInvitation
import com.example.domain.model.Session
import com.example.domain.model.SessionInboxItem
import com.example.domain.security.CallerContext

interface ClientIdentityRepository {
    // Authenticate bearer token from transport boundary -> CallerContext or null
    suspend fun authenticateBearerToken(token: String): CallerContext?

    // Pairing Lifecycle
    suspend fun createPairingInvitation(
        targetProjectId: String,
        targetLaneKind: LaneKind,
        intendedClientName: String,
        scopes: List<String>,
        ttlMs: Long = 10 * 60 * 1000L // 10 min default
    ): Pair<PairingInvitation, String> // returns model + plaintext token (shown once)

    suspend fun redeemPairingInvitation(
        pairingToken: String,
        installationName: String
    ): Result<Pair<ClientInstallation, String>> // returns installation + plaintext access token

    // Installation & Credential Management
    suspend fun listInstallations(): List<ClientInstallation>
    suspend fun revokeInstallation(installationId: String): Result<Unit>
    suspend fun rotateAccessCredential(installationId: String): Result<String> // returns new plaintext token
    suspend fun getConnectionContext(caller: CallerContext): Result<ConnectionContext>

    // Memory Lanes & Grants
    suspend fun getLanesForProject(projectId: String): List<MemoryLane>
    suspend fun createMemoryLane(projectId: String, laneSlug: String, laneKind: LaneKind, displayName: String): Result<MemoryLane>
    suspend fun grantProjectScope(installationId: String, projectId: String, scopes: List<String>): Result<ClientProjectGrant>
    suspend fun grantLaneAccess(installationId: String, laneId: String, canRead: Boolean, canWrite: Boolean): Result<ClientLaneGrant>

    // Authorization checks
    suspend fun hasProjectScope(installationId: String, projectId: String, scope: String): Boolean
    suspend fun hasLaneAccess(installationId: String, laneId: String, writeRequired: Boolean): Boolean
    suspend fun canAccessFileVersion(installationId: String, fileVersionId: String): Boolean
    suspend fun canAccessVaultBlob(installationId: String, sha256: String): Boolean

    // Sessions & Inboxes
    suspend fun startSession(
        caller: CallerContext,
        projectId: String,
        laneId: String,
        reportedProvider: String?,
        reportedModel: String?
    ): Result<Session>

    suspend fun closeSession(
        caller: CallerContext,
        sessionId: String,
        expectedRevision: Long
    ): Result<Unit>

    suspend fun appendInboxItem(
        caller: CallerContext,
        projectId: String,
        sessionId: String,
        itemId: String,
        itemType: String,
        title: String,
        payloadJson: String,
        idempotencyKey: String,
        sourceFileVersionId: String? = null,
        supersedesVersionId: String? = null
    ): Result<SessionInboxItem>

    suspend fun getInboxItems(
        caller: CallerContext,
        sessionId: String
    ): Result<List<SessionInboxItem>>
}
