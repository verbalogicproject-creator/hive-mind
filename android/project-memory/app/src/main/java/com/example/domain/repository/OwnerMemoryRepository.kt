package com.example.domain.repository

import com.example.domain.model.AgentMemoryEntry
import com.example.domain.model.AgentSessionSummary
import com.example.domain.model.AgentSummary
import com.example.domain.model.ClientInstallation
import com.example.domain.model.MemoryEntryDetail

interface OwnerMemoryRepository {
    suspend fun getAgentSummaries(projectId: String): List<AgentSummary>
    suspend fun getAgentSessions(projectId: String, installationId: String): List<AgentSessionSummary>
    suspend fun getAgentMemory(projectId: String, installationId: String): List<AgentMemoryEntry>
    suspend fun getProjectMemory(projectId: String): List<AgentMemoryEntry>
    suspend fun getMemoryDetail(projectId: String, versionId: String): MemoryEntryDetail?
    suspend fun getLocalOwner(): ClientInstallation?
}
