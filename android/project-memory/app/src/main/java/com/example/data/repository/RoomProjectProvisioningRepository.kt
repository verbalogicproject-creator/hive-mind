package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import com.example.data.entity.ClientInstallationEntity
import com.example.data.entity.ClientLaneGrantEntity
import com.example.data.entity.ClientProjectGrantEntity
import com.example.data.entity.MemoryLaneEntity
import com.example.data.entity.ProjectEntity
import com.example.domain.model.LaneKind
import com.example.domain.model.MemoryLane
import com.example.domain.model.Project
import com.example.domain.model.ProjectScope
import com.example.domain.repository.OwnerAccessRepairResult
import com.example.domain.repository.ProjectProvisioningRepository
import com.example.domain.repository.ProvisionedProject
import java.util.UUID

class RoomProjectProvisioningRepository(
    private val database: AppDatabase
) : ProjectProvisioningRepository {
    private val projects = database.projectDao()
    private val identities = database.clientIdentityDao()

    override suspend fun createProject(project: Project): Result<ProvisionedProject> = runCatching {
        database.withTransaction {
            require(project.id.isNotBlank()) { "Project ID cannot be empty" }
            require(project.slug.isNotBlank()) { "Project slug cannot be empty" }
            require(project.name.isNotBlank()) { "Project name cannot be empty" }
            require(project.rootUri.isNotBlank()) { "Choose a project folder" }
            check(projects.getById(project.id) == null) { "A project with this ID already exists" }
            check(projects.getBySlug(project.slug) == null) { "A project with this folder name already exists" }

            val now = System.currentTimeMillis()
            ensureOwnerInstallation(now)
            projects.insert(ProjectEntity.fromDomain(project))

            val lane = MemoryLaneEntity(
                id = UUID.randomUUID().toString(),
                projectId = project.id,
                laneSlug = OWNER_LANE_SLUG,
                laneKind = LaneKind.PRIVATE.value,
                displayName = OWNER_LANE_NAME,
                isArchived = false,
                createdAt = now
            )
            identities.insertLane(lane)
            identities.insertProjectGrant(ownerProjectGrant(project.id, now))
            identities.insertLaneGrant(ownerLaneGrant(lane.id, now))

            ProvisionedProject(
                project = project,
                ownerLane = lane.toDomain()
            )
        }
    }

    override suspend fun repairOwnerAccess(): Result<OwnerAccessRepairResult> = runCatching {
        database.withTransaction {
            val now = System.currentTimeMillis()
            ensureOwnerInstallation(now)
            val allProjects = projects.getAll()
            var repaired = 0

            allProjects.forEach { project ->
                var changed = false
                var lane = identities.getActiveOwnerLane(project.id)
                if (lane == null) {
                    lane = MemoryLaneEntity(
                        id = UUID.randomUUID().toString(),
                        projectId = project.id,
                        laneSlug = availableOwnerLaneSlug(project.id),
                        laneKind = LaneKind.PRIVATE.value,
                        displayName = OWNER_LANE_NAME,
                        isArchived = false,
                        createdAt = now
                    )
                    identities.insertLane(lane)
                    changed = true
                }
                if (identities.getActiveProjectGrant(OWNER_ID, project.id) == null) {
                    identities.insertProjectGrant(ownerProjectGrant(project.id, now))
                    changed = true
                }
                if (identities.getActiveLaneGrant(OWNER_ID, lane.id) == null) {
                    identities.insertLaneGrant(ownerLaneGrant(lane.id, now))
                    changed = true
                }
                if (changed) repaired++
            }

            OwnerAccessRepairResult(allProjects.size, repaired)
        }
    }

    private suspend fun ensureOwnerInstallation(now: Long) {
        if (identities.getInstallationById(OWNER_ID) == null) {
            identities.insertInstallation(
                ClientInstallationEntity(OWNER_ID, "Local Device Owner", now, null)
            )
        }
    }

    private suspend fun availableOwnerLaneSlug(projectId: String): String {
        if (identities.getLaneBySlug(projectId, OWNER_LANE_SLUG) == null) return OWNER_LANE_SLUG
        return "$OWNER_LANE_SLUG-${UUID.randomUUID().toString().take(8)}"
    }

    private fun ownerProjectGrant(projectId: String, now: Long) = ClientProjectGrantEntity(
        id = UUID.randomUUID().toString(),
        clientInstallationId = OWNER_ID,
        projectId = projectId,
        scopesCsv = ProjectScope.entries.joinToString(",") { it.value },
        createdAt = now,
        revokedAt = null
    )

    private fun ownerLaneGrant(laneId: String, now: Long) = ClientLaneGrantEntity(
        id = UUID.randomUUID().toString(),
        clientInstallationId = OWNER_ID,
        laneId = laneId,
        canRead = true,
        canWrite = true,
        createdAt = now,
        revokedAt = null
    )

    private fun MemoryLaneEntity.toDomain() = MemoryLane(
        id = id,
        projectId = projectId,
        laneSlug = laneSlug,
        laneKind = LaneKind.fromValue(laneKind),
        displayName = displayName,
        isArchived = isArchived,
        createdAtEpochMs = createdAt
    )

    companion object {
        const val OWNER_ID = "local-device-owner"
        const val OWNER_LANE_SLUG = "local-owner"
        const val OWNER_LANE_NAME = "On-device owner memory"
    }
}
