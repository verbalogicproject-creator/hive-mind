package com.example.domain.usecase

import com.example.domain.model.GraphEdge
import com.example.domain.model.GraphFilter
import com.example.domain.model.GraphNode
import com.example.domain.model.GraphNodeType
import com.example.domain.model.GraphProjection
import com.example.domain.repository.OwnerMemoryRepository
import com.example.domain.repository.ProjectRepository

class GetGraphProjectionUseCase(
    private val projects: ProjectRepository,
    private val ownerMemory: OwnerMemoryRepository
) {
    suspend operator fun invoke(
        projectId: String,
        filter: GraphFilter = GraphFilter(),
        nodeLimit: Int = DEFAULT_NODE_LIMIT,
        edgeLimit: Int = DEFAULT_EDGE_LIMIT
    ): Result<GraphProjection> = runCatching {
        require(nodeLimit in 1..MAX_NODE_LIMIT) { "Node limit must be between 1 and $MAX_NODE_LIMIT" }
        require(edgeLimit in 0..MAX_EDGE_LIMIT) { "Edge limit must be between 0 and $MAX_EDGE_LIMIT" }
        val project = projects.getProject(projectId)
            ?: throw IllegalArgumentException("Project '$projectId' does not exist")
        val agents = ownerMemory.getAgentSummaries(projectId)
            .filter { filter.agentInstallationId == null || it.installationId == filter.agentInstallationId }

        val allNodes = mutableListOf(
            GraphNode(
                id = "project:${project.id}",
                type = GraphNodeType.PROJECT,
                label = project.name,
                subtitle = "Selected project",
                sourceUri = "memory://project/${project.id}",
                sourceId = project.id,
                timestampEpochMs = project.updatedAtEpochMs,
                status = "selected"
            )
        )
        val allEdges = mutableListOf<GraphEdge>()

        agents.forEach { agent ->
            val agentNodeId = "agent:${agent.installationId}"
            allNodes += GraphNode(
                id = agentNodeId,
                type = GraphNodeType.AGENT,
                label = agent.installationName,
                subtitle = listOfNotNull(agent.latestReportedProvider, agent.latestReportedModel).joinToString(" · "),
                sourceUri = "memory://project/$projectId/agent/${agent.installationId}",
                sourceId = agent.installationId,
                timestampEpochMs = agent.lastActivityEpochMs ?: 0L,
                status = agent.connectionState.name.lowercase()
            )
            allEdges += edge(
                projectId,
                "project:${project.id}",
                agentNodeId,
                "project_contains_agent",
                agent.installationId,
                agent.lastActivityEpochMs ?: 0L
            )

            ownerMemory.getAgentSessions(projectId, agent.installationId).forEach { session ->
                val sessionNodeId = "session:${session.sessionId}"
                allNodes += GraphNode(
                    id = sessionNodeId,
                    type = GraphNodeType.SESSION,
                    label = "Session ${session.sessionId.take(8)}",
                    subtitle = listOfNotNull(session.reportedProvider, session.reportedModel).joinToString(" · "),
                    sourceUri = "memory://project/$projectId/session/${session.sessionId}",
                    sourceId = session.sessionId,
                    timestampEpochMs = session.startedAtEpochMs,
                    status = session.status.value
                )
                allEdges += edge(
                    projectId,
                    agentNodeId,
                    sessionNodeId,
                    "agent_started_session",
                    session.sessionId,
                    session.startedAtEpochMs
                )
            }

            ownerMemory.getAgentMemory(projectId, agent.installationId).forEach { memory ->
                val memoryNodeId = "memory:${memory.versionId}"
                allNodes += GraphNode(
                    id = memoryNodeId,
                    type = GraphNodeType.MEMORY,
                    label = memory.title,
                    subtitle = memory.itemType,
                    sourceUri = "memory://project/$projectId/${memory.itemType}/${memory.itemId}/version/${memory.versionId}",
                    sourceId = memory.versionId,
                    timestampEpochMs = memory.createdAtEpochMs,
                    status = if (memory.supersedesVersionId == null) "immutable" else "superseding"
                )
                allEdges += edge(
                    projectId,
                    "session:${memory.sessionId}",
                    memoryNodeId,
                    "session_contains_memory",
                    memory.versionId,
                    memory.createdAtEpochMs
                )
                memory.supersedesVersionId?.let { priorVersionId ->
                    allEdges += edge(
                        projectId,
                        memoryNodeId,
                        "memory:$priorVersionId",
                        "memory_supersedes_memory",
                        memory.versionId,
                        memory.createdAtEpochMs
                    )
                }
            }
        }

        val typeFiltered = allNodes.filter {
            filter.nodeTypes.isEmpty() || it.type == GraphNodeType.PROJECT || it.type in filter.nodeTypes
        }
        val sortedNodes = typeFiltered.sortedWith(
            compareByDescending<GraphNode> { it.type == GraphNodeType.PROJECT }
                .thenByDescending { it.timestampEpochMs }
                .thenBy { it.id }
        )
        val visibleNodes = sortedNodes.take(nodeLimit)
        val visibleIds = visibleNodes.mapTo(mutableSetOf()) { it.id }
        val eligibleEdges = allEdges
            .filter { it.sourceNodeId in visibleIds && it.targetNodeId in visibleIds }
            .sortedWith(compareByDescending<GraphEdge> { it.timestampEpochMs }.thenBy { it.id })
        val visibleEdges = eligibleEdges.take(edgeLimit)
        val omittedNodes = (sortedNodes.size - visibleNodes.size).coerceAtLeast(0)
        val omittedEdges = (eligibleEdges.size - visibleEdges.size).coerceAtLeast(0) +
            allEdges.count { it.sourceNodeId !in visibleIds || it.targetNodeId !in visibleIds }

        GraphProjection(
            projectId = projectId,
            nodes = visibleNodes,
            edges = visibleEdges,
            isTruncated = omittedNodes > 0 || omittedEdges > 0,
            omittedNodeCount = omittedNodes,
            omittedEdgeCount = omittedEdges,
            generatedAtEpochMs = System.currentTimeMillis()
        )
    }

    private fun edge(
        projectId: String,
        source: String,
        target: String,
        relation: String,
        sourceId: String,
        timestamp: Long
    ): GraphEdge {
        val id = "$relation:$source:$target"
        return GraphEdge(
            id = id,
            sourceNodeId = source,
            targetNodeId = target,
            relation = relation,
            sourceUri = "memory://project/$projectId/relation/$id",
            sourceId = sourceId,
            timestampEpochMs = timestamp
        )
    }

    companion object {
        const val DEFAULT_NODE_LIMIT = 100
        const val DEFAULT_EDGE_LIMIT = 150
        const val MAX_NODE_LIMIT = 100
        const val MAX_EDGE_LIMIT = 150
    }
}
