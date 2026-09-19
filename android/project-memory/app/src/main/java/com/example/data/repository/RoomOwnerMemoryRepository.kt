package com.example.data.repository

import com.example.data.database.AppDatabase
import com.example.data.entity.SessionEntity
import com.example.data.entity.SessionInboxItemEntity
import com.example.domain.model.AgentConnectionState
import com.example.domain.model.AgentMemoryEntry
import com.example.domain.model.AgentSessionSummary
import com.example.domain.model.AgentSummary
import com.example.domain.model.ClientInstallation
import com.example.domain.model.MemoryEntryDetail
import com.example.domain.model.SessionStatus
import com.example.domain.repository.OwnerMemoryRepository

class RoomOwnerMemoryRepository(
    database: AppDatabase
) : OwnerMemoryRepository {
    private val dao = database.clientIdentityDao()

    override suspend fun getAgentSummaries(projectId: String): List<AgentSummary> {
        require(projectId.isNotBlank()) { "Choose a project" }
        val installations = dao.getExternalInstallationsForProject(projectId)
        val sessions = dao.getSessionsForProject(projectId).groupBy { it.clientInstallationId }
        val memory = dao.getInboxItemsForProject(projectId).groupBy { item ->
            sessions.values.flatten().firstOrNull { it.id == item.sessionId }?.clientInstallationId
        }
        return installations.map { installation ->
            val agentSessions = sessions[installation.id].orEmpty()
            val agentMemory = memory[installation.id].orEmpty()
            val latest = agentSessions.maxByOrNull { it.startedAt }
            val lastActivity = maxOfOrNull(
                installation.createdAt,
                latest?.closedAt ?: latest?.startedAt,
                agentMemory.maxOfOrNull { it.createdAt }
            )
            AgentSummary(
                installationId = installation.id,
                installationName = installation.installationName,
                latestReportedProvider = latest?.reportedProvider,
                latestReportedModel = latest?.reportedModel,
                connectionState = when {
                    installation.revokedAt != null -> AgentConnectionState.REVOKED
                    agentSessions.any { it.status == SessionStatus.ACTIVE.value } -> AgentConnectionState.CONNECTED
                    else -> AgentConnectionState.DISCONNECTED
                },
                sessionCount = agentSessions.size,
                memoryCount = agentMemory.size,
                lastActivityEpochMs = lastActivity
            )
        }.sortedWith(compareByDescending<AgentSummary> { it.lastActivityEpochMs }.thenBy { it.installationName })
    }

    override suspend fun getAgentSessions(
        projectId: String,
        installationId: String
    ): List<AgentSessionSummary> = dao.getSessionsForClient(projectId, installationId).map { it.toSummary() }

    override suspend fun getAgentMemory(
        projectId: String,
        installationId: String
    ): List<AgentMemoryEntry> {
        val sessions = dao.getSessionsForClient(projectId, installationId).associateBy { it.id }
        return dao.getInboxItemsForAgent(projectId, installationId).mapNotNull { item ->
            val session = sessions[item.sessionId] ?: return@mapNotNull null
            item.toSummary(session)
        }
    }

    override suspend fun getProjectMemory(projectId: String): List<AgentMemoryEntry> {
        val sessions = dao.getSessionsForProject(projectId).associateBy { it.id }
        return dao.getInboxItemsForProject(projectId).mapNotNull { item ->
            val session = sessions[item.sessionId] ?: return@mapNotNull null
            item.toSummary(session)
        }
    }

    override suspend fun getMemoryDetail(projectId: String, versionId: String): MemoryEntryDetail? {
        val item = dao.getInboxItemForProject(projectId, versionId) ?: return null
        val session = dao.getSessionById(item.sessionId) ?: return null
        if (session.projectId != projectId) return null
        return MemoryEntryDetail(
            projectId = projectId,
            versionId = item.versionId,
            itemId = item.itemId,
            itemType = item.itemType,
            title = item.title,
            sequenceNumber = item.sequenceNumber,
            createdAtEpochMs = item.createdAt,
            contentHash = item.contentHash,
            sessionId = item.sessionId,
            installationId = session.clientInstallationId,
            laneId = session.laneId,
            reportedProvider = session.reportedProvider,
            reportedModel = session.reportedModel,
            sourceFileVersionId = item.sourceFileVersionId,
            supersedesVersionId = item.supersedesVersionId,
            payloadJson = item.payloadJson,
            sourceUri = memoryUri(projectId, item.itemType, item.itemId, item.versionId)
        )
    }

    override suspend fun getLocalOwner(): ClientInstallation? =
        dao.getInstallationById(RoomProjectProvisioningRepository.OWNER_ID)?.toDomain()

    private fun SessionEntity.toSummary() = AgentSessionSummary(
        sessionId = id,
        installationId = clientInstallationId,
        projectId = projectId,
        laneId = laneId,
        status = SessionStatus.fromValue(status),
        startedAtEpochMs = startedAt,
        closedAtEpochMs = closedAt,
        reportedProvider = reportedProvider,
        reportedModel = reportedModel
    )

    private fun SessionInboxItemEntity.toSummary(session: SessionEntity) = AgentMemoryEntry(
        versionId = versionId,
        itemId = itemId,
        itemType = itemType,
        title = title,
        sequenceNumber = sequenceNumber,
        createdAtEpochMs = createdAt,
        contentHash = contentHash,
        sessionId = sessionId,
        installationId = session.clientInstallationId,
        supersedesVersionId = supersedesVersionId,
        payloadPreview = payloadJson.replace(Regex("\\s+"), " ").take(PREVIEW_LIMIT)
    )

    private fun memoryUri(projectId: String, type: String, itemId: String, versionId: String): String =
        "memory://project/$projectId/${type.ifBlank { "item" }}/$itemId/version/$versionId"

    private fun maxOfOrNull(vararg values: Long?): Long? = values.filterNotNull().maxOrNull()

    companion object {
        private const val PREVIEW_LIMIT = 200
    }
}
