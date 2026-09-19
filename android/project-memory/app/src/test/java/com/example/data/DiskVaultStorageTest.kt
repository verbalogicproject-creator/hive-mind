package com.example.data

import com.example.data.vault.DiskVaultStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiskVaultStorageTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun storeAndGet_retrievesIdenticalContentBySha256() = runBlocking {
        val vaultDir = tempFolder.newFolder("vault_test")
        val storage = DiskVaultStorage(vaultDir)

        val sampleText = "fn main() { println!(\"Vault Test\"); }"
        val sampleBytes = sampleText.toByteArray(Charsets.UTF_8)

        val blob = storage.store(sampleBytes, "text/plain")
        assertEquals(64, blob.sha256.length)
        assertEquals(sampleBytes.size.toLong(), blob.sizeBytes)

        assertTrue(storage.has(blob.sha256))
        val retrieved = storage.get(blob.sha256)
        assertArrayEquals(sampleBytes, retrieved)
        assertEquals(1, storage.countBlobs())
        assertEquals(sampleBytes.size.toLong(), storage.totalSizeBytes())

        // Re-storing identical content deduplicates without error
        val blob2 = storage.store(sampleBytes, "text/plain")
        assertEquals(blob.sha256, blob2.sha256)
        assertEquals(1, storage.countBlobs())
    }

    @Test
    fun get_nonExistentHash_returnsNull() = runBlocking {
        val vaultDir = tempFolder.newFolder("vault_empty")
        val storage = DiskVaultStorage(vaultDir)

        assertFalse(storage.has("0000000000000000000000000000000000000000000000000000000000000000"))
        assertNull(storage.get("0000000000000000000000000000000000000000000000000000000000000000"))
    }
}
