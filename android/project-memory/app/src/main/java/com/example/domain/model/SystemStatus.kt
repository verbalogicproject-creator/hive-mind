package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Pure domain representation of the system and server status.
 * Serialized to JSON for the MCP system_status tool and UI telemetry.
 */
@Serializable
data class SystemStatus(
    val status: String,
    val version: String,
    val host: String,
    val port: Int,
    val isReadOnly: Boolean,
    val securityMode: String,
    val activeProjectsCount: Int,
    val uptimeMs: Long,
    val transport: String = "streamable-http"
)
