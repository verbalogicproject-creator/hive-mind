package com.example.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.ClientInstallation
import com.example.domain.model.CredentialType
import com.example.domain.model.LaneKind
import com.example.domain.model.SessionStatus

@Entity(
    tableName = "client_installations",
    indices = [
        Index(value = ["revoked_at"], name = "index_client_installations_revoked")
    ]
)
data class ClientInstallationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "installation_name")
    val installationName: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "revoked_at")
    val revokedAt: Long? = null
) {
    fun toDomain(): ClientInstallation = ClientInstallation(
        id = id,
        installationName = installationName,
        createdAtEpochMs = createdAt,
        revokedAtEpochMs = revokedAt
    )
}

@Entity(
    tableName = "client_credentials",
    foreignKeys = [
        ForeignKey(
            entity = ClientInstallationEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_installation_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["token_hash"], unique = true, name = "index_client_credentials_token_hash"),
        Index(value = ["client_installation_id"], name = "index_client_credentials_installation")
    ]
)
data class ClientCredentialEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "client_installation_id")
    val clientInstallationId: String,

    @ColumnInfo(name = "credential_type")
    val credentialType: String, // 'pairing', 'access'

    @ColumnInfo(name = "token_hash")
    val tokenHash: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "expires_at")
    val expiresAt: Long? = null,

    @ColumnInfo(name = "revoked_at")
    val revokedAt: Long? = null
)

@Entity(
    tableName = "pairing_invitations",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["target_project_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["token_hash"], unique = true, name = "index_pairing_invitations_token_hash")
    ]
)
data class PairingInvitationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "token_hash")
    val tokenHash: String,

    @ColumnInfo(name = "target_project_id")
    val targetProjectId: String,

    @ColumnInfo(name = "target_lane_kind")
    val targetLaneKind: String,

    @ColumnInfo(name = "intended_client_name")
    val intendedClientName: String,

    @ColumnInfo(name = "scopes_csv")
    val scopesCsv: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "expires_at")
    val expiresAt: Long,

    @ColumnInfo(name = "consumed_at")
    val consumedAt: Long? = null
)

@Entity(
    tableName = "memory_lanes",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["project_id", "lane_slug"], unique = true, name = "index_memory_lanes_project_slug")
    ]
)
data class MemoryLaneEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "project_id")
    val projectId: String,

    @ColumnInfo(name = "lane_slug")
    val laneSlug: String,

    @ColumnInfo(name = "lane_kind")
    val laneKind: String, // 'system_legacy', 'shared_approved', 'private'

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "client_project_grants",
    foreignKeys = [
        ForeignKey(
            entity = ClientInstallationEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_installation_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["client_installation_id", "project_id"], name = "index_client_project_grants_lookup")
    ]
)
data class ClientProjectGrantEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "client_installation_id")
    val clientInstallationId: String,

    @ColumnInfo(name = "project_id")
    val projectId: String,

    @ColumnInfo(name = "scopes_csv")
    val scopesCsv: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "revoked_at")
    val revokedAt: Long? = null
)

@Entity(
    tableName = "client_lane_grants",
    foreignKeys = [
        ForeignKey(
            entity = ClientInstallationEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_installation_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = MemoryLaneEntity::class,
            parentColumns = ["id"],
            childColumns = ["lane_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["client_installation_id", "lane_id"], name = "index_client_lane_grants_lookup")
    ]
)
data class ClientLaneGrantEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "client_installation_id")
    val clientInstallationId: String,

    @ColumnInfo(name = "lane_id")
    val laneId: String,

    @ColumnInfo(name = "can_read")
    val canRead: Boolean,

    @ColumnInfo(name = "can_write")
    val canWrite: Boolean,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "revoked_at")
    val revokedAt: Long? = null
)

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ClientInstallationEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_installation_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = MemoryLaneEntity::class,
            parentColumns = ["id"],
            childColumns = ["lane_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["project_id", "client_installation_id"], name = "index_sessions_project_client")
    ]
)
data class SessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "project_id")
    val projectId: String,

    @ColumnInfo(name = "client_installation_id")
    val clientInstallationId: String,

    @ColumnInfo(name = "lane_id")
    val laneId: String,

    @ColumnInfo(name = "reported_provider")
    val reportedProvider: String?,

    @ColumnInfo(name = "reported_model")
    val reportedModel: String?,

    @ColumnInfo(name = "status")
    val status: String, // 'active', 'closed'

    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    @ColumnInfo(name = "closed_at")
    val closedAt: Long? = null,

    @ColumnInfo(name = "revision")
    val revision: Long = 1L
)

@Entity(
    tableName = "session_inbox_items",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = FileVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_file_version_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = SessionInboxItemEntity::class,
            parentColumns = ["version_id"],
            childColumns = ["supersedes_version_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["session_id", "sequence_number"], unique = true, name = "index_session_inbox_seq"),
        Index(value = ["content_hash"], name = "index_session_inbox_hash"),
        Index(value = ["item_id"], name = "index_session_inbox_item_id")
    ]
)
data class SessionInboxItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "version_id")
    val versionId: String,

    @ColumnInfo(name = "item_id")
    val itemId: String,

    @ColumnInfo(name = "supersedes_version_id")
    val supersedesVersionId: String? = null,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "sequence_number")
    val sequenceNumber: Long,

    @ColumnInfo(name = "item_type")
    val itemType: String, // 'checkpoint', 'note', etc.

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String,

    @ColumnInfo(name = "source_file_version_id")
    val sourceFileVersionId: String? = null,

    @ColumnInfo(name = "content_hash")
    val contentHash: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "idempotency_records",
    foreignKeys = [
        ForeignKey(
            entity = ClientInstallationEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_installation_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(
            value = ["client_installation_id", "project_id", "operation", "idempotency_key"],
            unique = true,
            name = "index_idempotency_scope"
        )
    ]
)
data class IdempotencyRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "client_installation_id")
    val clientInstallationId: String,

    @ColumnInfo(name = "project_id")
    val projectId: String,

    @ColumnInfo(name = "operation")
    val operation: String,

    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String,

    @ColumnInfo(name = "request_hash")
    val requestHash: String,

    @ColumnInfo(name = "result_json")
    val resultJson: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
