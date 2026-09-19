package com.example.domain.usecase

import com.example.domain.model.FileVersion
import com.example.domain.repository.FileVersionRepository
import com.example.domain.repository.IndexTargetValidator
import com.example.domain.repository.VaultStorage
import java.util.UUID

data class IndexResult(
    val fileVersion: FileVersion,
    val chunkCount: Int,
    val isDeduplicated: Boolean
)

class IndexFileContentUseCase(
    private val vaultStorage: VaultStorage,
    private val fileVersionRepository: FileVersionRepository,
    private val indexTargetValidator: IndexTargetValidator,
    private val calculateSha256UseCase: CalculateSha256UseCase = CalculateSha256UseCase(),
    private val chunkTextUseCase: ChunkTextUseCase = ChunkTextUseCase()
) {
    suspend operator fun invoke(
        projectId: String,
        laneId: String,
        rawPath: String,
        content: ByteArray,
        mimeType: String = "text/plain"
    ): Result<IndexResult> = runCatching {
        val sanitizedPath = sanitizePath(rawPath)
        require(sanitizedPath.isNotEmpty()) { "File path cannot be empty or invalid" }

        val target = indexTargetValidator.validate(projectId.trim(), laneId.trim())
        val sha256 = calculateSha256UseCase(content)
        val latest = fileVersionRepository.getLatestVersion(
            target.projectId,
            target.laneId,
            sanitizedPath
        )
        if (latest != null && latest.sha256 == sha256) {
            val existingChunks = fileVersionRepository.getChunksForVersion(latest.id)
            return@runCatching IndexResult(latest, existingChunks.size, true)
        }

        // The target is valid before the filesystem changes. Vault blobs are content-addressed;
        // retaining a blob after a later Room failure is safe and intentionally non-destructive.
        vaultStorage.store(content, mimeType)

        val fileVersionId = UUID.randomUUID().toString()
        val fileVersion = FileVersion(
            id = fileVersionId,
            projectId = target.projectId,
            laneId = target.laneId,
            path = sanitizedPath,
            sha256 = sha256,
            sizeBytes = content.size.toLong(),
            mimeType = mimeType,
            versionNumber = (latest?.versionNumber ?: 0) + 1,
            createdAt = System.currentTimeMillis()
        )

        val textContent = content.toString(Charsets.UTF_8)
        val chunks = if (isTextMimeType(mimeType) || textContent.isNotEmpty()) {
            chunkTextUseCase(fileVersionId, textContent)
        } else {
            emptyList()
        }
        fileVersionRepository.recordVersion(fileVersion, chunks)
        IndexResult(fileVersion, chunks.size, false)
    }

    suspend operator fun invoke(
        projectId: String,
        laneId: String,
        path: String,
        textContent: String,
        mimeType: String = "text/plain"
    ): Result<IndexResult> = invoke(
        projectId = projectId,
        laneId = laneId,
        rawPath = path,
        content = textContent.toByteArray(Charsets.UTF_8),
        mimeType = mimeType
    )

    private fun sanitizePath(path: String): String = path.trim()
        .replace("\\", "/")
        .split("/")
        .filter { it.isNotEmpty() && it != "." && it != ".." }
        .joinToString("/")

    private fun isTextMimeType(mimeType: String): Boolean =
        mimeType.startsWith("text/") ||
            mimeType.contains("json") ||
            mimeType.contains("xml") ||
            mimeType.contains("javascript") ||
            mimeType.contains("kotlin") ||
            mimeType.contains("markdown")
}
