package com.example.domain

import com.example.domain.usecase.CalculateSha256UseCase
import com.example.domain.usecase.ChunkTextUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HashAndChunkUseCaseTest {

    private val calculateSha256UseCase = CalculateSha256UseCase()
    private val chunkTextUseCase = ChunkTextUseCase(calculateSha256UseCase)

    @Test
    fun calculateSha256_producesDeterministicHexDigest() {
        val emptyHash = calculateSha256UseCase("")
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", emptyHash)

        val helloHash1 = calculateSha256UseCase("hello world")
        val helloHash2 = calculateSha256UseCase("hello world")
        assertEquals(helloHash1, helloHash2)
        assertEquals(64, helloHash1.length)

        val diffHash = calculateSha256UseCase("hello world!")
        assertNotEquals(helloHash1, diffHash)
    }

    @Test
    fun chunkTextUseCase_emptyText_producesSingleEmptyChunk() {
        val chunks = chunkTextUseCase("v-1", "")
        assertEquals(1, chunks.size)
        assertEquals(0, chunks[0].chunkIndex)
        assertEquals("", chunks[0].content)
        assertEquals(0, chunks[0].tokenCountEstimate)
    }

    @Test
    fun chunkTextUseCase_smallText_producesSingleChunk() {
        val text = "fn main() {\n    println!(\"Antigravity Phase 1\");\n}"
        val chunks = chunkTextUseCase("v-1", text, targetChunkChars = 500)
        assertEquals(1, chunks.size)
        assertEquals(text, chunks[0].content)
        assertTrue(chunks[0].tokenCountEstimate > 0)
        assertEquals(calculateSha256UseCase(text), chunks[0].sha256)
    }

    @Test
    fun chunkTextUseCase_largeMultiLineText_splitsCleanlyOnBoundaries() {
        val longCode = buildString {
            for (i in 1..50) {
                appendLine("line_$i: let val_$i = $i * 10;")
            }
        }
        val chunks = chunkTextUseCase("v-large", longCode, targetChunkChars = 200)
        assertTrue("Should produce multiple chunks", chunks.size > 1)
        // Verify index ordering
        chunks.forEachIndexed { index, chunk ->
            assertEquals(index, chunk.chunkIndex)
            assertEquals("v-large", chunk.fileVersionId)
            assertTrue(chunk.content.isNotEmpty())
            assertEquals(calculateSha256UseCase(chunk.content), chunk.sha256)
        }
    }
}
