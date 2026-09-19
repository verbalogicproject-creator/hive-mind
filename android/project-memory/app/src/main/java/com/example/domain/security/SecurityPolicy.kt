package com.example.domain.security

import com.example.domain.model.ServerConfig

/**
 * Encapsulates security enforcement and invariants for the memory provider:
 * 1. Default to stopped, loopback (127.0.0.1), and read-only.
 * 2. Reject non-loopback binds unless explicitly enabled. Transport requests are
 *    authenticated with revocable per-client credentials by McpServerManager.
 * 3. Enforce read-only mutation gate.
 * 4. Ban shell execution, arbitrary filesystem paths, and unrestricted URL fetching.
 */
class SecurityPolicy {

    companion object {
        const val LOOPBACK_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 8080
        val ALLOWED_PORT_RANGE = 1024..65535

        private val FORBIDDEN_TOOL_PREFIXES = listOf(
            "shell_", "exec_", "raw_sql_", "fs_arbitrary_", "url_fetch_"
        )
    }

    fun validateConfig(config: ServerConfig): Result<Unit> {
        if (config.port !in ALLOWED_PORT_RANGE) {
            return Result.failure(
                IllegalArgumentException("Port ${config.port} is outside valid unprivileged range ($ALLOWED_PORT_RANGE)")
            )
        }

        if (!config.isLanEnabled && config.host != LOOPBACK_HOST) {
            return Result.failure(
                SecurityException("Non-loopback host '${config.host}' is prohibited unless LAN mode is explicitly enabled.")
            )
        }

        return Result.success(Unit)
    }

    fun canExecuteMutation(config: ServerConfig): Boolean {
        return !config.isReadOnly
    }

    fun isToolPermitted(toolName: String): Boolean {
        return FORBIDDEN_TOOL_PREFIXES.none { toolName.startsWith(it, ignoreCase = true) }
    }
}
