package com.example.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class GraphNodeType {
    PROJECT,
    AGENT,
    SESSION,
    MEMORY
}

@Serializable
data class GraphNode(
    val id: String,
    val type: GraphNodeType,
    val label: String,
    val subtitle: String,
    val sourceUri: String,
    val sourceId: String,
    val timestampEpochMs: Long,
    val status: String
)

@Serializable
data class GraphEdge(
    val id: String,
    val sourceNodeId: String,
    val targetNodeId: String,
    val relation: String,
    val sourceUri: String,
    val sourceId: String,
    val timestampEpochMs: Long
)

@Serializable
data class GraphProjection(
    val projectId: String,
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val isTruncated: Boolean,
    val omittedNodeCount: Int,
    val omittedEdgeCount: Int,
    val generatedAtEpochMs: Long
)

data class GraphFilter(
    val agentInstallationId: String? = null,
    val nodeTypes: Set<GraphNodeType> = emptySet()
)
