package com.example.data.vault

import com.example.domain.model.VaultBlob
import com.example.domain.repository.VaultStorage
import com.example.domain.usecase.CalculateSha256UseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DiskVaultStorage(
    private val vaultDirectory: File,
    private val calculateSha256UseCase: CalculateSha256UseCase = CalculateSha256UseCase()
) : VaultStorage {

    init {
        if (!vaultDirectory.exists()) {
            vaultDirectory.mkdirs()
        }
    }

    override suspend fun store(bytes: ByteArray, mimeType: String): VaultBlob = withContext(Dispatchers.IO) {
        val hash = calculateSha256UseCase(bytes)
        val prefix = hash.substring(0, 2.coerceAtMost(hash.length))
        val bucketDir = File(vaultDirectory, prefix)
        if (!bucketDir.exists()) {
            bucketDir.mkdirs()
        }
        val targetFile = File(bucketDir, hash)
        if (!targetFile.exists()) {
            // Write atomically via temporary file
            val tempFile = File.createTempFile("vault_", ".tmp", vaultDirectory)
            tempFile.writeBytes(bytes)
            if (!tempFile.renameTo(targetFile)) {
                targetFile.writeBytes(bytes)
                tempFile.delete()
            }
        }
        VaultBlob(
            sha256 = hash,
            sizeBytes = bytes.size.toLong(),
            mimeType = mimeType,
            createdAt = targetFile.lastModified()
        )
    }

    override suspend fun get(sha256: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = getBlobFile(sha256)
        if (file.exists()) {
            file.readBytes()
        } else {
            null
        }
    }

    override suspend fun has(sha256: String): Boolean = withContext(Dispatchers.IO) {
        getBlobFile(sha256).exists()
    }

    override suspend fun countBlobs(): Int = withContext(Dispatchers.IO) {
        var count = 0
        vaultDirectory.listFiles()?.forEach { bucket ->
            if (bucket.isDirectory) {
                count += (bucket.listFiles()?.size ?: 0)
            }
        }
        count
    }

    override suspend fun totalSizeBytes(): Long = withContext(Dispatchers.IO) {
        var total = 0L
        vaultDirectory.listFiles()?.forEach { bucket ->
            if (bucket.isDirectory) {
                bucket.listFiles()?.forEach { file ->
                    total += file.length()
                }
            }
        }
        total
    }

    private fun getBlobFile(sha256: String): File {
        val prefix = if (sha256.length >= 2) sha256.substring(0, 2) else "00"
        return File(File(vaultDirectory, prefix), sha256)
    }
}
