package com.example.domain.model

enum class AgentConnectionState {
    CONNECTED,
    DISCONNECTED,
    REVOKED
}

data class AgentSummary(
    val installationId: String,
    val installationName: String,
    val latestReportedProvider: String?,
    val latestReportedModel: String?,
    val connectionState: AgentConnectionState,
    val sessionCount: Int,
    val memoryCount: Int,
    val lastActivityEpochMs: Long?
)

data class AgentSessionSummary(
    val sessionId: String,
    val installationId: String,
    val projectId: String,
    val laneId: String,
    val status: SessionStatus,
    val startedAtEpochMs: Long,
    val closedAtEpochMs: Long?,
    val reportedProvider: String?,
    val reportedModel: String?
)

data class AgentMemoryEntry(
    val versionId: String,
    val itemId: String,
    val itemType: String,
    val title: String,
    val sequenceNumber: Long,
    val createdAtEpochMs: Long,
    val contentHash: String,
    val sessionId: String,
    val installationId: String,
    val supersedesVersionId: String?,
    val payloadPreview: String
)

data class MemoryEntryDetail(
    val projectId: String,
    val versionId: String,
    val itemId: String,
    val itemType: String,
    val title: String,
    val sequenceNumber: Long,
    val createdAtEpochMs: Long,
    val contentHash: String,
    val sessionId: String,
    val installationId: String,
    val laneId: String,
    val reportedProvider: String?,
    val reportedModel: String?,
    val sourceFileVersionId: String?,
    val supersedesVersionId: String?,
    val payloadJson: String,
    val sourceUri: String
)
