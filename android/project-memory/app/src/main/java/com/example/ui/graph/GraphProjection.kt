package com.example.ui.graph

import kotlinx.serialization.Serializable

@Serializable
data class GraphNode(
  val id: String,
  val type: String,
  val label: String,
  val sourceUri: String,
  val sourceId: String
)

@Serializable
data class GraphEdge(
  val id: String,
  val source: String,
  val target: String,
  val type: String,
  val sourceUri: String,
  val sourceId: String
)

@Serializable
data class GraphProjection(
  val projectId: String,
  val nodes: List<GraphNode>,
  val edges: List<GraphEdge>,
  val truncated: Boolean,
  val nodeLimit: Int = 100,
  val edgeLimit: Int = 150
) {
  companion object {
    fun fromDomain(projection: com.example.domain.model.GraphProjection?): GraphProjection {
      if (projection == null) return GraphProjection("", emptyList(), emptyList(), false)
      return GraphProjection(
        projectId = projection.projectId,
        nodes = projection.nodes.map { node ->
          GraphNode(node.id, node.type.name.lowercase(), node.label, node.sourceUri, node.sourceId)
        },
        edges = projection.edges.map { edge ->
          GraphEdge(edge.id, edge.sourceNodeId, edge.targetNodeId, edge.relation, edge.sourceUri, edge.sourceId)
        },
        truncated = projection.isTruncated
      )
    }
  }
}
