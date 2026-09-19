package com.example.domain.usecase

import com.example.domain.model.FileChunk
import java.util.UUID

class ChunkTextUseCase(
    private val calculateSha256UseCase: CalculateSha256UseCase = CalculateSha256UseCase()
) {
    /**
     * Splits text into deterministic chunks aiming for maxChunkChars with newline boundary preference.
     */
    operator fun invoke(
        fileVersionId: String,
        text: String,
        targetChunkChars: Int = 1000
    ): List<FileChunk> {
        if (text.isBlank()) {
            val emptyHash = calculateSha256UseCase("")
            return listOf(
                FileChunk(
                    id = UUID.randomUUID().toString(),
                    fileVersionId = fileVersionId,
                    chunkIndex = 0,
                    content = "",
                    tokenCountEstimate = 0,
                    sha256 = emptyHash
                )
            )
        }

        val lines = text.lines()
        val chunks = mutableListOf<FileChunk>()
        val currentChunk = StringBuilder()
        var chunkIndex = 0

        for (line in lines) {
            if (currentChunk.isNotEmpty() && (currentChunk.length + line.length + 1 > targetChunkChars)) {
                val chunkContent = currentChunk.toString()
                chunks.add(
                    FileChunk(
                        id = UUID.randomUUID().toString(),
                        fileVersionId = fileVersionId,
                        chunkIndex = chunkIndex++,
                        content = chunkContent,
                        tokenCountEstimate = estimateTokens(chunkContent),
                        sha256 = calculateSha256UseCase(chunkContent)
                    )
                )
                currentChunk.clear()
            }

            if (currentChunk.isNotEmpty()) {
                currentChunk.append("\n")
            }
            currentChunk.append(line)
        }

        if (currentChunk.isNotEmpty()) {
            val chunkContent = currentChunk.toString()
            chunks.add(
                FileChunk(
                    id = UUID.randomUUID().toString(),
                    fileVersionId = fileVersionId,
                    chunkIndex = chunkIndex,
                    content = chunkContent,
                    tokenCountEstimate = estimateTokens(chunkContent),
                    sha256 = calculateSha256UseCase(chunkContent)
                )
            )
        }

        return chunks
    }

    private fun estimateTokens(text: String): Int {
        // Standard heuristic: 1 token ~ 4 characters (min 1 for non-empty)
        return if (text.isEmpty()) 0 else (text.length / 4).coerceAtLeast(1)
    }
}
