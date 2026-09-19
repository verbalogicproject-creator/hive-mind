package com.example.domain.usecase

import com.example.domain.repository.VaultStorage

class ReadVaultBlobUseCase(
    private val vaultStorage: VaultStorage
) {
    suspend operator fun invoke(sha256: String): ByteArray? {
        if (sha256.isBlank()) return null
        return vaultStorage.get(sha256)
    }

    suspend fun getText(sha256: String): String? {
        return invoke(sha256)?.toString(Charsets.UTF_8)
    }

    suspend fun has(sha256: String): Boolean {
        return vaultStorage.has(sha256)
    }
}
