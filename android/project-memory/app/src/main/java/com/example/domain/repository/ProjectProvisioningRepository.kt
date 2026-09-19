package com.example.domain.repository

import com.example.domain.model.MemoryLane
import com.example.domain.model.Project

/**
 * Creates and repairs the local owner's complete project access boundary.
 * Implementations must apply each operation atomically.
 */
interface ProjectProvisioningRepository {
    suspend fun createProject(project: Project): Result<ProvisionedProject>
    suspend fun repairOwnerAccess(): Result<OwnerAccessRepairResult>
}

data class ProvisionedProject(
    val project: Project,
    val ownerLane: MemoryLane
)

data class OwnerAccessRepairResult(
    val projectsChecked: Int,
    val projectsRepaired: Int
)
