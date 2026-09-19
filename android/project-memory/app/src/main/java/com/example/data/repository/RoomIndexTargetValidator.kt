package com.example.data.repository

import com.example.data.database.AppDatabase
import com.example.domain.repository.IndexTargetValidator
import com.example.domain.repository.ValidatedIndexTarget

class RoomIndexTargetValidator(
    database: AppDatabase
) : IndexTargetValidator {
    private val projects = database.projectDao()
    private val identities = database.clientIdentityDao()

    override suspend fun validate(projectId: String, laneId: String): ValidatedIndexTarget {
        require(projectId.isNotBlank()) { "Choose a project before indexing" }
        require(laneId.isNotBlank()) { "Choose a memory lane before indexing" }
        projects.getById(projectId)
            ?: throw IllegalArgumentException("Project '$projectId' does not exist")
        val lane = identities.getLaneById(laneId)
            ?: throw IllegalArgumentException("Memory lane '$laneId' does not exist")
        require(lane.projectId == projectId) {
            "Memory lane '$laneId' belongs to another project"
        }
        require(!lane.isArchived) { "Memory lane '$laneId' is archived" }
        return ValidatedIndexTarget(projectId, laneId)
    }
}
