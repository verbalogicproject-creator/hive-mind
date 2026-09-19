package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Pure domain representation of a Project record.
 * Contains no Android, Room, Ktor, or MCP dependencies.
 */
@Serializable
data class Project(
    val id: String,
    val slug: String,
    val name: String,
    val description: String = "",
    val rootUri: String,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)
