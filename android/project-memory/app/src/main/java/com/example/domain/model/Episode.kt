package com.example.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Episode(
    val id: String,
    val projectId: String,
    val laneId: String,
    val title: String,
    val summary: String = "",
    val source: String = "agent",
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class EpisodeEvent(
    val id: String,
    val episodeId: String,
    val sequenceNumber: Long,
    val eventType: String,
    val actor: String,
    val payload: String,
    val fileVersionId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
