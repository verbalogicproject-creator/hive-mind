package com.example.domain.usecase

import com.example.domain.model.ServerConfig
import com.example.domain.model.SystemStatus
import com.example.domain.repository.ProjectRepository

class GetSystemStatusUseCase(
    private val projectRepository: ProjectRepository
) {
    suspend operator fun invoke(
        isRunning: Boolean,
        config: ServerConfig,
        uptimeMs: Long,
        version: String = "0.1.0"
    ): SystemStatus {
        val projectsCount = projectRepository.countProjects()
        val securityMode = when {
            config.isLanEnabled -> "LAN-ClientCredentials"
            config.host == "127.0.0.1" -> "Loopback-Authenticated"
            else -> "Restricted"
        }

        return SystemStatus(
            status = if (isRunning) "RUNNING" else "STOPPED",
            version = version,
            host = config.host,
            port = config.port,
            isReadOnly = config.isReadOnly,
            securityMode = securityMode,
            activeProjectsCount = projectsCount,
            uptimeMs = if (isRunning) uptimeMs else 0L,
            transport = "streamable-http"
        )
    }
}
