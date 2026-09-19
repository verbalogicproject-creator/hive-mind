package com.example.domain.security

import com.example.domain.model.CredentialType

/**
 * Authenticated identity context established at the transport boundary (Streamable HTTP / legacy SSE / Bearer Auth).
 * Never supplied or spoofed from tool arguments.
 */
data class CallerContext(
    val clientInstallationId: String,
    val installationName: String,
    val credentialId: String,
    val credentialType: CredentialType,
    val authenticatedAtEpochMs: Long = System.currentTimeMillis()
) {
    val isSystemOwner: Boolean
        get() = clientInstallationId == SYSTEM_OWNER_ID

    companion object {
        const val SYSTEM_OWNER_ID = "local-device-owner"
        const val SYSTEM_OWNER_NAME = "Local Device Owner"

        val LOCAL_OWNER = CallerContext(
            clientInstallationId = SYSTEM_OWNER_ID,
            installationName = SYSTEM_OWNER_NAME,
            credentialId = "owner-system-cred",
            credentialType = CredentialType.ACCESS
        )
    }
}
