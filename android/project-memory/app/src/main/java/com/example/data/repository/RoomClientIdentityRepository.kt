package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import com.example.data.entity.ClientCredentialEntity
import com.example.data.entity.ClientInstallationEntity
import com.example.data.entity.ClientLaneGrantEntity
import com.example.data.entity.ClientProjectGrantEntity
import com.example.data.entity.IdempotencyRecordEntity
import com.example.data.entity.MemoryLaneEntity
import com.example.data.entity.PairingInvitationEntity
import com.example.data.entity.SessionEntity
import com.example.data.entity.SessionInboxItemEntity
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
import com.example.domain.model.Session
import com.example.domain.model.SessionInboxItem
import com.example.domain.model.SessionStatus
import com.example.domain.repository.ClientIdentityRepository
import com.example.domain.security.CallerContext
import com.example.domain.security.TokenCryptoUtils
import kotlinx.serialization.json.Json
import java.util.UUID

class RoomClientIdentityRepository(
    private val database: AppDatabase
) : ClientIdentityRepository {

    private val dao = database.clientIdentityDao()
    private val projectDao = database.projectDao()
    private val fileVersionDao = database.fileVersionDao()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun authenticateBearerToken(token: String): CallerContext? {
        if (token.isBlank()) return null
        val tokenHash = TokenCryptoUtils.sha256Hash(token)
        val cred = dao.getActiveCredentialByHash(tokenHash) ?: return null

        val now = System.currentTimeMillis()
        if (cred.expiresAt != null && cred.expiresAt <= now) return null
        if (cred.revokedAt != null) return null

        // Pairing tokens must NOT authorize standard MCP tools or resources
        val type = CredentialType.fromValue(cred.credentialType)
        if (type == CredentialType.PAIRING) return null

        // Check if installation is active
        val installation = dao.getInstallationById(cred.clientInstallationId) ?: return null
        if (installation.revokedAt != null) return null

        return CallerContext(
            clientInstallationId = installation.id,
            installationName = installation.installationName,
            credentialId = cred.id,
            credentialType = type
        )
    }

    override suspend fun createPairingInvitation(
        targetProjectId: String,
        targetLaneKind: LaneKind,
        intendedClientName: String,
        scopes: List<String>,
        ttlMs: Long
    ): Pair<PairingInvitation, String> {
        val plaintextToken = "pair_" + TokenCryptoUtils.generateSecureRandomToken(32)
        val tokenHash = TokenCryptoUtils.sha256Hash(plaintextToken)
        val now = System.currentTimeMillis()
        val expiresAt = now + ttlMs

        val invitationEntity = PairingInvitationEntity(
            id = UUID.randomUUID().toString(),
            tokenHash = tokenHash,
            targetProjectId = targetProjectId,
            targetLaneKind = targetLaneKind.value,
            intendedClientName = intendedClientName,
            scopesCsv = scopes.joinToString(","),
            createdAt = now,
            expiresAt = expiresAt,
            consumedAt = null
        )

        dao.insertPairingInvitation(invitationEntity)

        val domain = PairingInvitation(
            id = invitationEntity.id,
            tokenHash = tokenHash,
            targetProjectId = targetProjectId,
            targetLaneKind = targetLaneKind,
            intendedClientName = intendedClientName,
            scopesCsv = invitationEntity.scopesCsv,
            createdAtEpochMs = now,
            expiresAtEpochMs = expiresAt,
            consumedAtEpochMs = null
        )

        return Pair(domain, plaintextToken)
    }

    override suspend fun redeemPairingInvitation(
        pairingToken: String,
        installationName: String
    ): Result<Pair<ClientInstallation, String>> = runCatching {
        val tokenHash = TokenCryptoUtils.sha256Hash(pairingToken)
        val now = System.currentTimeMillis()

        database.withTransaction {
            val invitation = dao.getActivePairingInvitationByHash(tokenHash)
                ?: throw IllegalArgumentException("Pairing invitation not found or already consumed")

            if (invitation.expiresAt <= now) {
                throw IllegalStateException("Pairing invitation has expired")
            }

            // Consume invitation atomically
            val consumedRows = dao.consumePairingInvitation(invitation.id, now)
            if (consumedRows != 1) {
                throw IllegalStateException("Pairing invitation was already consumed")
            }

            // Create client installation
            val installationId = UUID.randomUUID().toString()
            val installationEntity = ClientInstallationEntity(
                id = installationId,
                installationName = installationName.ifBlank { invitation.intendedClientName },
                createdAt = now,
                revokedAt = null
            )
            dao.insertInstallation(installationEntity)

            // Generate high-entropy 256-bit access token
            val plaintextAccessToken = "pm_" + TokenCryptoUtils.generateSecureRandomToken(32)
            val accessTokenHash = TokenCryptoUtils.sha256Hash(plaintextAccessToken)

            val credEntity = ClientCredentialEntity(
                id = UUID.randomUUID().toString(),
                clientInstallationId = installationId,
                credentialType = CredentialType.ACCESS.value,
                tokenHash = accessTokenHash,
                createdAt = now,
                expiresAt = null,
                revokedAt = null
            )
            dao.insertCredential(credEntity)

            // Grant target project scopes
            val grantEntity = ClientProjectGrantEntity(
                id = UUID.randomUUID().toString(),
                clientInstallationId = installationId,
                projectId = invitation.targetProjectId,
                scopesCsv = invitation.scopesCsv,
                createdAt = now,
                revokedAt = null
            )
            dao.insertProjectGrant(grantEntity)

            // Find or create designated lane and grant access
            val laneSlug = if (invitation.targetLaneKind == LaneKind.SHARED_APPROVED.value) {
                "shared-approved"
            } else {
                "lane-${installationId.take(8)}"
            }
            var lane = dao.getLaneBySlug(invitation.targetProjectId, laneSlug)
            if (lane == null) {
                val newLane = MemoryLaneEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = invitation.targetProjectId,
                    laneSlug = laneSlug,
                    laneKind = invitation.targetLaneKind,
                    displayName = "${installationName.ifBlank { invitation.intendedClientName }} Lane",
                    isArchived = false,
                    createdAt = now
                )
                dao.insertLane(newLane)
                lane = newLane
            }

            val laneGrant = ClientLaneGrantEntity(
                id = UUID.randomUUID().toString(),
                clientInstallationId = installationId,
                laneId = lane.id,
                canRead = true,
                canWrite = true,
                createdAt = now,
                revokedAt = null
            )
            dao.insertLaneGrant(laneGrant)

            Pair(installationEntity.toDomain(), plaintextAccessToken)
        }
    }

    override suspend fun listInstallations(): List<ClientInstallation> {
        return dao.getAllInstallations().map { it.toDomain() }
    }

    override suspend fun getConnectionContext(caller: CallerContext): Result<ConnectionContext> = runCatching {
        database.withTransaction {
            val installation = dao.getInstallationById(caller.clientInstallationId)
                ?: throw SecurityException("Authenticated installation no longer exists")
            if (installation.revokedAt != null) {
                throw SecurityException("Authenticated installation has been revoked")
            }

            val activeProjectGrants = dao.getProjectGrantsForInstallation(caller.clientInstallationId)
                .filter { it.revokedAt == null }
            val activeLaneGrants = dao.getLaneGrantsForInstallation(caller.clientInstallationId)
                .filter { it.revokedAt == null }

            val projects = activeProjectGrants.mapNotNull { projectGrant ->
                val project = projectDao.getById(projectGrant.projectId) ?: return@mapNotNull null
                val lanes = activeLaneGrants.mapNotNull { laneGrant ->
                    val lane = dao.getLaneById(laneGrant.laneId) ?: return@mapNotNull null
                    if (lane.projectId != project.id || lane.isArchived) return@mapNotNull null
                    if (!caller.isSystemOwner && LaneKind.fromValue(lane.laneKind) == LaneKind.SYSTEM_LEGACY) {
                        return@mapNotNull null
                    }
                    AuthorizedLaneContext(
                        laneId = lane.id,
                        laneSlug = lane.laneSlug,
                        laneKind = lane.laneKind,
                        displayName = lane.displayName,
                        canRead = laneGrant.canRead,
                        canWrite = laneGrant.canWrite
                    )
                }.sortedWith(compareBy(AuthorizedLaneContext::displayName, AuthorizedLaneContext::laneId))

                AuthorizedProjectContext(
                    projectId = project.id,
                    projectSlug = project.slug,
                    projectName = project.name,
                    scopes = projectGrant.scopesCsv
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .sorted(),
                    lanes = lanes
                )
            }.sortedWith(compareBy(AuthorizedProjectContext::projectName, AuthorizedProjectContext::projectId))

            ConnectionContext(
                clientInstallationId = installation.id,
                installationName = installation.installationName,
                projects = projects
            )
        }
    }

    override suspend fun revokeInstallation(installationId: String): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.revokeInstallation(installationId, now)
            dao.revokeAllCredentialsForInstallation(installationId, now)
        }
    }

    override suspend fun rotateAccessCredential(installationId: String): Result<String> = runCatching {
        val now = System.currentTimeMillis()
        val plaintextAccessToken = "pm_" + TokenCryptoUtils.generateSecureRandomToken(32)
        val accessTokenHash = TokenCryptoUtils.sha256Hash(plaintextAccessToken)

        database.withTransaction {
            val installation = dao.getInstallationById(installationId)
                ?: throw IllegalArgumentException("Installation not found")
            if (installation.revokedAt != null) {
                throw IllegalStateException("Cannot rotate credentials for revoked installation")
            }

            // Revoke prior credentials
            dao.revokeAllCredentialsForInstallation(installationId, now)

            // Insert new credential
            val newCred = ClientCredentialEntity(
                id = UUID.randomUUID().toString(),
                clientInstallationId = installationId,
                credentialType = CredentialType.ACCESS.value,
                tokenHash = accessTokenHash,
                createdAt = now,
                expiresAt = null,
                revokedAt = null
            )
            dao.insertCredential(newCred)

            plaintextAccessToken
        }
    }

    override suspend fun getLanesForProject(projectId: String): List<MemoryLane> {
        return dao.getLanesForProject(projectId).map {
            MemoryLane(
                id = it.id,
                projectId = it.projectId,
                laneSlug = it.laneSlug,
                laneKind = LaneKind.fromValue(it.laneKind),
                displayName = it.displayName,
                isArchived = it.isArchived,
                createdAtEpochMs = it.createdAt
            )
        }
    }

    override suspend fun createMemoryLane(
        projectId: String,
        laneSlug: String,
        laneKind: LaneKind,
        displayName: String
    ): Result<MemoryLane> = runCatching {
        val entity = MemoryLaneEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            laneSlug = laneSlug,
            laneKind = laneKind.value,
            displayName = displayName,
            isArchived = false,
            createdAt = System.currentTimeMillis()
        )
        dao.insertLane(entity)
        MemoryLane(
            id = entity.id,
            projectId = entity.projectId,
            laneSlug = entity.laneSlug,
            laneKind = laneKind,
            displayName = entity.displayName,
            isArchived = false,
            createdAtEpochMs = entity.createdAt
        )
    }

    override suspend fun grantProjectScope(
        installationId: String,
        projectId: String,
        scopes: List<String>
    ): Result<ClientProjectGrant> = runCatching {
        val now = System.currentTimeMillis()
        val entity = ClientProjectGrantEntity(
            id = UUID.randomUUID().toString(),
            clientInstallationId = installationId,
            projectId = projectId,
            scopesCsv = scopes.joinToString(","),
            createdAt = now,
            revokedAt = null
        )
        dao.insertProjectGrant(entity)
        ClientProjectGrant(
            id = entity.id,
            clientInstallationId = entity.clientInstallationId,
            projectId = entity.projectId,
            scopesCsv = entity.scopesCsv,
            createdAtEpochMs = entity.createdAt,
            revokedAtEpochMs = null
        )
    }

    override suspend fun grantLaneAccess(
        installationId: String,
        laneId: String,
        canRead: Boolean,
        canWrite: Boolean
    ): Result<ClientLaneGrant> = runCatching {
        val now = System.currentTimeMillis()
        val entity = ClientLaneGrantEntity(
            id = UUID.randomUUID().toString(),
            clientInstallationId = installationId,
            laneId = laneId,
            canRead = canRead,
            canWrite = canWrite,
            createdAt = now,
            revokedAt = null
        )
        dao.insertLaneGrant(entity)
        ClientLaneGrant(
            id = entity.id,
            clientInstallationId = entity.clientInstallationId,
            laneId = entity.laneId,
            canRead = entity.canRead,
            canWrite = entity.canWrite,
            createdAtEpochMs = entity.createdAt,
            revokedAtEpochMs = null
        )
    }

    override suspend fun hasProjectScope(installationId: String, projectId: String, scope: String): Boolean {
        if (installationId == CallerContext.SYSTEM_OWNER_ID) return true
        val grant = dao.getActiveProjectGrant(installationId, projectId) ?: return false
        val scopes = grant.scopesCsv.split(",").map { it.trim() }
        return scopes.contains(scope)
    }

    override suspend fun hasLaneAccess(installationId: String, laneId: String, writeRequired: Boolean): Boolean {
        if (installationId == CallerContext.SYSTEM_OWNER_ID) return true
        val lane = dao.getLaneById(laneId) ?: return false

        // SYSTEM_LEGACY is strictly private to local device owner
        if (lane.laneKind == LaneKind.SYSTEM_LEGACY.value) return false

        val grant = dao.getActiveLaneGrant(installationId, laneId) ?: return false
        return if (writeRequired) grant.canWrite else grant.canRead
    }

    override suspend fun canAccessFileVersion(installationId: String, fileVersionId: String): Boolean {
        if (installationId == CallerContext.SYSTEM_OWNER_ID) return true
        val fileVersion = fileVersionDao.getFileVersion(fileVersionId) ?: return false
        // Caller must have project scope
        if (!hasProjectScope(installationId, fileVersion.projectId, ProjectScope.APPROVED_READ.value)) {
            return false
        }
        return hasLaneAccess(installationId, fileVersion.laneId, writeRequired = false)
    }

    override suspend fun canAccessVaultBlob(installationId: String, sha256: String): Boolean {
        if (installationId == CallerContext.SYSTEM_OWNER_ID) return true
        // Only authorize vault blobs through an authorized FileVersion that references this sha256
        val versions = fileVersionDao.observeFileVersions("").let {
            // Check direct query:
            database.openHelper.readableDatabase.query(
                "SELECT id FROM file_versions WHERE sha256 = ?",
                arrayOf(sha256)
            )
        }
        var isAuthorized = false
        while (versions.moveToNext()) {
            val fvId = versions.getString(0)
            if (canAccessFileVersion(installationId, fvId)) {
                isAuthorized = true
                break
            }
        }
        versions.close()
        return isAuthorized
    }

    override suspend fun startSession(
        caller: CallerContext,
        projectId: String,
        laneId: String,
        reportedProvider: String?,
        reportedModel: String?
    ): Result<Session> = runCatching {
        database.withTransaction {
            // 1. Verify project exists
            val project = projectDao.getById(projectId)
                ?: throw IllegalArgumentException("Project '$projectId' not found")

            // 2. Verify lane belongs to the same project
            val lane = dao.getLaneById(laneId)
                ?: throw IllegalArgumentException("Lane '$laneId' not found")
            if (lane.projectId != projectId) {
                throw IllegalArgumentException("Lane '$laneId' belongs to project '${lane.projectId}', not '$projectId'")
            }

            // 3. Verify caller has session:start scope on project
            if (!hasProjectScope(caller.clientInstallationId, projectId, ProjectScope.SESSION_START.value)) {
                throw SecurityException("Caller '${caller.clientInstallationId}' lacks session:start scope for project '$projectId'")
            }

            // 4. Verify caller has write access to target lane
            if (!hasLaneAccess(caller.clientInstallationId, laneId, writeRequired = true)) {
                throw SecurityException("Caller '${caller.clientInstallationId}' lacks write access to lane '$laneId'")
            }

            val now = System.currentTimeMillis()
            val sessionEntity = SessionEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                clientInstallationId = caller.clientInstallationId,
                laneId = laneId,
                reportedProvider = reportedProvider?.ifBlank { null } ?: "unknown",
                reportedModel = reportedModel?.ifBlank { null } ?: "unknown",
                status = SessionStatus.ACTIVE.value,
                startedAt = now,
                closedAt = null,
                revision = 1L
            )
            dao.insertSession(sessionEntity)

            Session(
                id = sessionEntity.id,
                projectId = sessionEntity.projectId,
                clientInstallationId = sessionEntity.clientInstallationId,
                laneId = sessionEntity.laneId,
                reportedProvider = sessionEntity.reportedProvider,
                reportedModel = sessionEntity.reportedModel,
                status = SessionStatus.ACTIVE,
                startedAtEpochMs = sessionEntity.startedAt,
                closedAtEpochMs = null,
                revision = 1L
            )
        }
    }

    override suspend fun closeSession(
        caller: CallerContext,
        sessionId: String,
        expectedRevision: Long
    ): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        database.withTransaction {
            val session = dao.getSessionById(sessionId)
                ?: throw IllegalArgumentException("Session '$sessionId' not found")

            if (caller.clientInstallationId != CallerContext.SYSTEM_OWNER_ID &&
                session.clientInstallationId != caller.clientInstallationId
            ) {
                throw SecurityException("Caller is not authorized to close session '$sessionId'")
            }

            val rowsUpdated = dao.closeSession(sessionId, expectedRevision, now)
            if (rowsUpdated == 0) {
                throw IllegalStateException("Stale session revision or session already closed for '$sessionId'")
            }
        }
    }

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
    ): Result<SessionInboxItem> = runCatching {
        val operation = "inbox_append"

        // Canonical request hash covering all semantic fields
        val requestHash = TokenCryptoUtils.canonicalHash(
            projectId,
            sessionId,
            itemId,
            itemType,
            title,
            payloadJson,
            sourceFileVersionId,
            supersedesVersionId
        )

        // Retry loop for serialized concurrency (e.g. SQLite BUSY or race condition)
        var attempts = 0
        while (attempts < 5) {
            attempts++
            try {
                return@runCatching database.withTransaction {
                    // Check Idempotency Record first
                    val existing = dao.getIdempotencyRecord(
                        clientInstallationId = caller.clientInstallationId,
                        projectId = projectId,
                        operation = operation,
                        idempotencyKey = idempotencyKey
                    )

                    if (existing != null) {
                        if (existing.requestHash == requestHash) {
                            // Replay prior result
                            val priorItem = dao.getInboxItemByVersionId(existing.resultJson)
                                ?: throw IllegalStateException("Cached inbox version not found")
                            return@withTransaction SessionInboxItem(
                                itemId = priorItem.itemId,
                                versionId = priorItem.versionId,
                                supersedesVersionId = priorItem.supersedesVersionId,
                                sessionId = priorItem.sessionId,
                                sequenceNumber = priorItem.sequenceNumber,
                                itemType = priorItem.itemType,
                                title = priorItem.title,
                                payloadJson = priorItem.payloadJson,
                                sourceFileVersionId = priorItem.sourceFileVersionId,
                                contentHash = priorItem.contentHash,
                                createdAtEpochMs = priorItem.createdAt
                            )
                        } else {
                            throw IllegalStateException("Conflict: Reusing idempotency key '$idempotencyKey' with mismatched request payload")
                        }
                    }

                    // Validate session
                    val session = dao.getSessionById(sessionId)
                        ?: throw IllegalArgumentException("Session '$sessionId' not found")

                    if (session.status != SessionStatus.ACTIVE.value) {
                        throw IllegalStateException("Cannot append to closed session '$sessionId'")
                    }

                    if (caller.clientInstallationId != CallerContext.SYSTEM_OWNER_ID &&
                        session.clientInstallationId != caller.clientInstallationId
                    ) {
                        throw SecurityException("Caller does not own session '$sessionId'")
                    }

                    if (session.projectId != projectId) {
                        throw IllegalArgumentException("Session project mismatch: expected '$projectId', found '${session.projectId}'")
                    }

                    // Validate inbox:append scope
                    if (!hasProjectScope(caller.clientInstallationId, projectId, ProjectScope.INBOX_APPEND.value)) {
                        throw SecurityException("Caller lacks inbox:append scope for project '$projectId'")
                    }

                    // Validate lane write permission
                    if (!hasLaneAccess(caller.clientInstallationId, session.laneId, writeRequired = true)) {
                        throw SecurityException("Caller lacks write permission on session lane '${session.laneId}'")
                    }

                    // Validate supersedesVersionId if provided
                    if (supersedesVersionId != null) {
                        val pred = dao.getInboxItemByVersionId(supersedesVersionId)
                            ?: throw IllegalArgumentException("Superseded version '$supersedesVersionId' not found")
                        if (pred.itemId != itemId) {
                            throw IllegalArgumentException("Superseded version item ID '${pred.itemId}' does not match target item ID '$itemId'")
                        }
                        if (pred.sessionId != sessionId) {
                            throw SecurityException("Superseded version belongs to a different session")
                        }
                    }

                    // Validate sourceFileVersionId if provided
                    if (sourceFileVersionId != null) {
                        val fv = fileVersionDao.getFileVersion(sourceFileVersionId)
                            ?: throw IllegalArgumentException("Source file version '$sourceFileVersionId' not found")
                        if (fv.projectId != projectId) {
                            throw IllegalArgumentException("Source file version project mismatch")
                        }
                        if (!canAccessFileVersion(caller.clientInstallationId, sourceFileVersionId)) {
                            throw SecurityException("Caller cannot reference source file version '$sourceFileVersionId'")
                        }
                    }

                    // Transactional sequence number allocation: COALESCE(MAX(seq), 0) + 1
                    val nextSeq = dao.getMaxSequenceNumber(sessionId) + 1L
                    val versionId = UUID.randomUUID().toString()
                    val now = System.currentTimeMillis()

                    // Canonical content-integrity hash
                    val contentHash = TokenCryptoUtils.canonicalHash(
                        itemId,
                        versionId,
                        supersedesVersionId,
                        itemType,
                        title,
                        payloadJson,
                        sourceFileVersionId
                    )

                    val itemEntity = SessionInboxItemEntity(
                        versionId = versionId,
                        itemId = itemId,
                        supersedesVersionId = supersedesVersionId,
                        sessionId = sessionId,
                        sequenceNumber = nextSeq,
                        itemType = itemType,
                        title = title,
                        payloadJson = payloadJson,
                        sourceFileVersionId = sourceFileVersionId,
                        contentHash = contentHash,
                        createdAt = now
                    )
                    dao.insertInboxItem(itemEntity)

                    // Record idempotency
                    val idempEntity = IdempotencyRecordEntity(
                        id = UUID.randomUUID().toString(),
                        clientInstallationId = caller.clientInstallationId,
                        projectId = projectId,
                        operation = operation,
                        idempotencyKey = idempotencyKey,
                        requestHash = requestHash,
                        resultJson = versionId,
                        createdAt = now
                    )
                    dao.insertIdempotencyRecord(idempEntity)

                    SessionInboxItem(
                        itemId = itemId,
                        versionId = versionId,
                        supersedesVersionId = supersedesVersionId,
                        sessionId = sessionId,
                        sequenceNumber = nextSeq,
                        itemType = itemType,
                        title = title,
                        payloadJson = payloadJson,
                        sourceFileVersionId = sourceFileVersionId,
                        contentHash = contentHash,
                        createdAtEpochMs = now
                    )
                }
            } catch (e: Exception) {
                if (attempts >= 5 || e is IllegalStateException || e is SecurityException || e is IllegalArgumentException) {
                    throw e
                }
                kotlinx.coroutines.delay(20L * attempts)
            }
        }
        throw IllegalStateException("Failed to append inbox item after $attempts attempts")
    }

    override suspend fun getInboxItems(
        caller: CallerContext,
        sessionId: String
    ): Result<List<SessionInboxItem>> = runCatching {
        val session = dao.getSessionById(sessionId)
            ?: throw IllegalArgumentException("Session '$sessionId' not found")

        if (caller.clientInstallationId != CallerContext.SYSTEM_OWNER_ID &&
            session.clientInstallationId != caller.clientInstallationId
        ) {
            throw SecurityException("Caller not authorized to read inbox of session '$sessionId'")
        }

        if (!hasLaneAccess(caller.clientInstallationId, session.laneId, writeRequired = false)) {
            throw SecurityException("Caller lacks read permission on session lane '${session.laneId}'")
        }

        dao.getInboxItemsForSession(sessionId).map {
            SessionInboxItem(
                itemId = it.itemId,
                versionId = it.versionId,
                supersedesVersionId = it.supersedesVersionId,
                sessionId = it.sessionId,
                sequenceNumber = it.sequenceNumber,
                itemType = it.itemType,
                title = it.title,
                payloadJson = it.payloadJson,
                sourceFileVersionId = it.sourceFileVersionId,
                contentHash = it.contentHash,
                createdAtEpochMs = it.createdAt
            )
        }
    }
}
