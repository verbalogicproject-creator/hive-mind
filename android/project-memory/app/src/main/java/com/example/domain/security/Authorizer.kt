package com.example.domain.security

import com.example.domain.model.ProjectScope
import com.example.domain.repository.ClientIdentityRepository

class Authorizer(
    private val clientIdentityRepository: ClientIdentityRepository? = null
) {
    suspend fun authenticateBearerToken(headerValue: String?): CallerContext? {
        if (headerValue.isNullOrBlank()) return null
        val repo = clientIdentityRepository ?: return null
        val token = if (headerValue.startsWith("Bearer ", ignoreCase = true)) {
            headerValue.substring(7).trim()
        } else {
            headerValue.trim()
        }
        return repo.authenticateBearerToken(token)
    }

    suspend fun checkProjectScope(caller: CallerContext, projectId: String, scope: ProjectScope): Boolean {
        if (caller.isSystemOwner) return true
        val repo = clientIdentityRepository ?: return false
        return repo.hasProjectScope(caller.clientInstallationId, projectId, scope.value)
    }

    suspend fun checkLaneAccess(caller: CallerContext, laneId: String, writeRequired: Boolean): Boolean {
        if (caller.isSystemOwner) return true
        val repo = clientIdentityRepository ?: return false
        return repo.hasLaneAccess(caller.clientInstallationId, laneId, writeRequired)
    }

    suspend fun checkFileVersionAccess(caller: CallerContext, fileVersionId: String): Boolean {
        if (caller.isSystemOwner) return true
        val repo = clientIdentityRepository ?: return false
        return repo.canAccessFileVersion(caller.clientInstallationId, fileVersionId)
    }

    suspend fun checkVaultBlobAccess(caller: CallerContext, sha256: String): Boolean {
        if (caller.isSystemOwner) return true
        val repo = clientIdentityRepository ?: return false
        return repo.canAccessVaultBlob(caller.clientInstallationId, sha256)
    }
}
