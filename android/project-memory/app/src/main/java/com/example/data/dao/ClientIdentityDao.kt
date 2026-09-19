package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.entity.ClientCredentialEntity
import com.example.data.entity.ClientInstallationEntity
import com.example.data.entity.ClientLaneGrantEntity
import com.example.data.entity.ClientProjectGrantEntity
import com.example.data.entity.IdempotencyRecordEntity
import com.example.data.entity.MemoryLaneEntity
import com.example.data.entity.PairingInvitationEntity
import com.example.data.entity.SessionEntity
import com.example.data.entity.SessionInboxItemEntity

@Dao
interface ClientIdentityDao {

    // --- Client Installations ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInstallation(installation: ClientInstallationEntity)

    @Query("SELECT * FROM client_installations WHERE id = :id")
    suspend fun getInstallationById(id: String): ClientInstallationEntity?

    @Query("SELECT * FROM client_installations ORDER BY created_at DESC")
    suspend fun getAllInstallations(): List<ClientInstallationEntity>

    @Query(
        """
        SELECT DISTINCT ci.* FROM client_installations ci
        INNER JOIN client_project_grants pg ON pg.client_installation_id = ci.id
        WHERE pg.project_id = :projectId AND ci.id != 'local-device-owner'
        ORDER BY ci.created_at DESC
        """
    )
    suspend fun getExternalInstallationsForProject(projectId: String): List<ClientInstallationEntity>

    @Query("UPDATE client_installations SET revoked_at = :revokedAt WHERE id = :id AND revoked_at IS NULL")
    suspend fun revokeInstallation(id: String, revokedAt: Long): Int

    // --- Client Credentials ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCredential(credential: ClientCredentialEntity)

    @Query("SELECT * FROM client_credentials WHERE token_hash = :tokenHash AND revoked_at IS NULL")
    suspend fun getActiveCredentialByHash(tokenHash: String): ClientCredentialEntity?

    @Query("UPDATE client_credentials SET revoked_at = :revokedAt WHERE id = :id AND revoked_at IS NULL")
    suspend fun revokeCredential(id: String, revokedAt: Long): Int

    @Query("UPDATE client_credentials SET revoked_at = :revokedAt WHERE client_installation_id = :installationId AND revoked_at IS NULL")
    suspend fun revokeAllCredentialsForInstallation(installationId: String, revokedAt: Long): Int

    // --- Pairing Invitations ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPairingInvitation(invitation: PairingInvitationEntity)

    @Query("SELECT * FROM pairing_invitations WHERE token_hash = :tokenHash AND consumed_at IS NULL")
    suspend fun getActivePairingInvitationByHash(tokenHash: String): PairingInvitationEntity?

    @Query("UPDATE pairing_invitations SET consumed_at = :consumedAt WHERE id = :id AND consumed_at IS NULL")
    suspend fun consumePairingInvitation(id: String, consumedAt: Long): Int

    @Query("SELECT * FROM pairing_invitations WHERE target_project_id = :projectId ORDER BY created_at DESC")
    suspend fun getPairingInvitationsForProject(projectId: String): List<PairingInvitationEntity>

    // --- Memory Lanes ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLane(lane: MemoryLaneEntity)

    @Query("SELECT * FROM memory_lanes WHERE id = :laneId")
    suspend fun getLaneById(laneId: String): MemoryLaneEntity?

    @Query("SELECT * FROM memory_lanes WHERE project_id = :projectId AND lane_slug = :laneSlug")
    suspend fun getLaneBySlug(projectId: String, laneSlug: String): MemoryLaneEntity?

    @Query("SELECT * FROM memory_lanes WHERE project_id = :projectId AND lane_kind = 'private' AND lane_slug LIKE 'local-owner%' AND is_archived = 0 ORDER BY created_at ASC LIMIT 1")
    suspend fun getActiveOwnerLane(projectId: String): MemoryLaneEntity?

    @Query("SELECT * FROM memory_lanes WHERE project_id = :projectId ORDER BY created_at ASC")
    suspend fun getLanesForProject(projectId: String): List<MemoryLaneEntity>

    // --- Client Project Grants ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProjectGrant(grant: ClientProjectGrantEntity)

    @Query("SELECT * FROM client_project_grants WHERE client_installation_id = :installationId AND project_id = :projectId AND revoked_at IS NULL")
    suspend fun getActiveProjectGrant(installationId: String, projectId: String): ClientProjectGrantEntity?

    @Query("SELECT * FROM client_project_grants WHERE client_installation_id = :installationId ORDER BY created_at DESC")
    suspend fun getProjectGrantsForInstallation(installationId: String): List<ClientProjectGrantEntity>

    @Query("UPDATE client_project_grants SET revoked_at = :revokedAt WHERE id = :id AND revoked_at IS NULL")
    suspend fun revokeProjectGrant(id: String, revokedAt: Long): Int

    // --- Client Lane Grants ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLaneGrant(grant: ClientLaneGrantEntity)

    @Query("SELECT * FROM client_lane_grants WHERE client_installation_id = :installationId AND lane_id = :laneId AND revoked_at IS NULL")
    suspend fun getActiveLaneGrant(installationId: String, laneId: String): ClientLaneGrantEntity?

    @Query("SELECT * FROM client_lane_grants WHERE client_installation_id = :installationId ORDER BY created_at DESC")
    suspend fun getLaneGrantsForInstallation(installationId: String): List<ClientLaneGrantEntity>

    @Query("UPDATE client_lane_grants SET revoked_at = :revokedAt WHERE id = :id AND revoked_at IS NULL")
    suspend fun revokeLaneGrant(id: String, revokedAt: Long): Int

    // --- Sessions ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE project_id = :projectId AND client_installation_id = :installationId ORDER BY started_at DESC")
    suspend fun getSessionsForClient(projectId: String, installationId: String): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE project_id = :projectId ORDER BY started_at DESC")
    suspend fun getSessionsForProject(projectId: String): List<SessionEntity>

    @Query("UPDATE sessions SET status = 'closed', closed_at = :closedAt, revision = revision + 1 WHERE id = :sessionId AND status = 'active' AND revision = :expectedRevision")
    suspend fun closeSession(sessionId: String, expectedRevision: Long, closedAt: Long): Int

    // --- Session Inbox Items ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInboxItem(item: SessionInboxItemEntity)

    @Query("SELECT COALESCE(MAX(sequence_number), 0) FROM session_inbox_items WHERE session_id = :sessionId")
    suspend fun getMaxSequenceNumber(sessionId: String): Long

    @Query("SELECT * FROM session_inbox_items WHERE session_id = :sessionId ORDER BY sequence_number ASC")
    suspend fun getInboxItemsForSession(sessionId: String): List<SessionInboxItemEntity>

    @Query("SELECT * FROM session_inbox_items WHERE version_id = :versionId")
    suspend fun getInboxItemByVersionId(versionId: String): SessionInboxItemEntity?

    @Query(
        """
        SELECT i.* FROM session_inbox_items i
        INNER JOIN sessions s ON s.id = i.session_id
        WHERE s.project_id = :projectId
        ORDER BY i.created_at DESC, i.sequence_number DESC
        """
    )
    suspend fun getInboxItemsForProject(projectId: String): List<SessionInboxItemEntity>

    @Query(
        """
        SELECT i.* FROM session_inbox_items i
        INNER JOIN sessions s ON s.id = i.session_id
        WHERE s.project_id = :projectId AND s.client_installation_id = :installationId
        ORDER BY i.created_at DESC, i.sequence_number DESC
        """
    )
    suspend fun getInboxItemsForAgent(projectId: String, installationId: String): List<SessionInboxItemEntity>

    @Query(
        """
        SELECT i.* FROM session_inbox_items i
        INNER JOIN sessions s ON s.id = i.session_id
        WHERE s.project_id = :projectId AND i.version_id = :versionId
        LIMIT 1
        """
    )
    suspend fun getInboxItemForProject(projectId: String, versionId: String): SessionInboxItemEntity?

    // --- Idempotency ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIdempotencyRecord(record: IdempotencyRecordEntity)

    @Query("SELECT * FROM idempotency_records WHERE client_installation_id = :clientInstallationId AND project_id = :projectId AND operation = :operation AND idempotency_key = :idempotencyKey")
    suspend fun getIdempotencyRecord(
        clientInstallationId: String,
        projectId: String,
        operation: String,
        idempotencyKey: String
    ): IdempotencyRecordEntity?
}
