package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Server runtime configuration.
 * Defaults strictly to loopback (127.0.0.1) and read-only.
 */
@Serializable
data class ServerConfig(
    val host: String = "127.0.0.1",
    val port: Int = 8080,
    val isReadOnly: Boolean = true,
    val isLanEnabled: Boolean = false
)
