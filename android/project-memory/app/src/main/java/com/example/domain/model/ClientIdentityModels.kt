package com.example.domain.model

import kotlinx.serialization.Serializable

enum class CredentialType(val value: String) {
    PAIRING("pairing"),
    ACCESS("access");

    companion object {
        fun fromValue(value: String): CredentialType =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: ACCESS
    }
}

enum class LaneKind(val value: String) {
    SYSTEM_LEGACY("system_legacy"),
    SHARED_APPROVED("shared_approved"),
    PRIVATE("private");

    companion object {
        fun fromValue(value: String): LaneKind =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: PRIVATE
    }
}

enum class SessionStatus(val value: String) {
    ACTIVE("active"),
    CLOSED("closed");

    companion object {
        fun fromValue(value: String): SessionStatus =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: ACTIVE
    }
}

enum class ProjectScope(val value: String) {
    PROJECT_DISCOVER("project:discover"),
    APPROVED_READ("approved:read"),
    INBOX_APPEND("inbox:append"),
    SESSION_START("session:start");

    companion object {
        fun fromValue(value: String): ProjectScope? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

data class ClientInstallation(
    val id: String,
    val installationName: String,
    val createdAtEpochMs: Long,
    val revokedAtEpochMs: Long? = null
) {
    val isRevoked: Boolean get() = revokedAtEpochMs != null
}

data class ClientCredential(
    val id: String,
    val clientInstallationId: String,
    val credentialType: CredentialType,
    val tokenHash: String,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long? = null,
    val revokedAtEpochMs: Long? = null
) {
    val isRevoked: Boolean get() = revokedAtEpochMs != null
    fun isExpired(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        expiresAtEpochMs != null && expiresAtEpochMs <= nowEpochMs
    fun isValid(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        !isRevoked && !isExpired(nowEpochMs)
}

data class PairingInvitation(
    val id: String,
    val tokenHash: String,
    val targetProjectId: String,
    val targetLaneKind: LaneKind,
    val intendedClientName: String,
    val scopesCsv: String,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val consumedAtEpochMs: Long? = null
) {
    val isConsumed: Boolean get() = consumedAtEpochMs != null
    fun isExpired(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        expiresAtEpochMs <= nowEpochMs
    fun isValid(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        !isConsumed && !isExpired(nowEpochMs)
}

data class ClientProjectGrant(
    val id: String,
    val clientInstallationId: String,
    val projectId: String,
    val scopesCsv: String,
    val createdAtEpochMs: Long,
    val revokedAtEpochMs: Long? = null
) {
    val isRevoked: Boolean get() = revokedAtEpochMs != null
    val scopes: Set<String>
        get() = scopesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun hasScope(scope: ProjectScope): Boolean =
        !isRevoked && scopes.contains(scope.value)
}

data class MemoryLane(
    val id: String,
    val projectId: String,
    val laneSlug: String,
    val laneKind: LaneKind,
    val displayName: String,
    val isArchived: Boolean = false,
    val createdAtEpochMs: Long
)

data class ClientLaneGrant(
    val id: String,
    val clientInstallationId: String,
    val laneId: String,
    val canRead: Boolean,
    val canWrite: Boolean,
    val createdAtEpochMs: Long,
    val revokedAtEpochMs: Long? = null
) {
    val isRevoked: Boolean get() = revokedAtEpochMs != null
}

data class Session(
    val id: String,
    val projectId: String,
    val clientInstallationId: String,
    val laneId: String,
    val reportedProvider: String?,
    val reportedModel: String?,
    val status: SessionStatus,
    val startedAtEpochMs: Long,
    val closedAtEpochMs: Long? = null,
    val revision: Long = 1L
) {
    val normalizedProvider: String get() = reportedProvider?.ifBlank { null } ?: "unknown"
    val normalizedModel: String get() = reportedModel?.ifBlank { null } ?: "unknown"
}

data class SessionInboxItem(
    val itemId: String,
    val versionId: String,
    val supersedesVersionId: String? = null,
    val sessionId: String,
    val sequenceNumber: Long,
    val itemType: String,
    val title: String,
    val payloadJson: String,
    val sourceFileVersionId: String? = null,
    val contentHash: String,
    val createdAtEpochMs: Long
)

data class IdempotencyRecord(
    val id: String,
    val clientInstallationId: String,
    val projectId: String,
    val operation: String,
    val idempotencyKey: String,
    val requestHash: String,
    val resultJson: String,
    val createdAtEpochMs: Long
)

@Serializable
data class ConnectionContext(
    val clientInstallationId: String,
    val installationName: String,
    val projects: List<AuthorizedProjectContext>
)

@Serializable
data class AuthorizedProjectContext(
    val projectId: String,
    val projectSlug: String,
    val projectName: String,
    val scopes: List<String>,
    val lanes: List<AuthorizedLaneContext>
)

@Serializable
data class AuthorizedLaneContext(
    val laneId: String,
    val laneSlug: String,
    val laneKind: String,
    val displayName: String,
    val canRead: Boolean,
    val canWrite: Boolean
)
