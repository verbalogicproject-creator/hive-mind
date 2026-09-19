package com.example.domain

import com.example.data.vault.DiskVaultStorage
import com.example.domain.model.FileChunk
import com.example.domain.model.FileVersion
import com.example.domain.repository.FileVersionRepository
import com.example.domain.repository.IndexTargetValidator
import com.example.domain.repository.ValidatedIndexTarget
import com.example.domain.usecase.IndexFileContentUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

open class FakeFileVersionRepository : FileVersionRepository {
    private val versions = MutableStateFlow<List<FileVersion>>(emptyList())
    private val chunksMap = mutableMapOf<String, List<FileChunk>>()

    override fun observeFileVersions(projectId: String): Flow<List<FileVersion>> =
        versions.map { list -> list.filter { it.projectId == projectId } }

    override suspend fun getFileVersion(id: String): FileVersion? = versions.value.find { it.id == id }

    override suspend fun getLatestVersion(projectId: String, laneId: String, path: String): FileVersion? =
        versions.value
            .filter { it.projectId == projectId && it.laneId == laneId && it.path == path }
            .maxByOrNull { it.versionNumber }

    override suspend fun recordVersion(version: FileVersion, chunks: List<FileChunk>) {
        versions.value = versions.value + version
        chunksMap[version.id] = chunks
    }

    override suspend fun getChunksForVersion(fileVersionId: String): List<FileChunk> =
        chunksMap[fileVersionId] ?: emptyList()

    override suspend fun countFiles(projectId: String): Int =
        versions.value.filter { it.projectId == projectId }.map { it.path }.distinct().size

    override suspend fun countVersions(projectId: String): Int = versions.value.count { it.projectId == projectId }

    override suspend fun countChunks(projectId: String): Int {
        val versionIds = versions.value.filter { it.projectId == projectId }.map { it.id }.toSet()
        return chunksMap.filterKeys { it in versionIds }.values.sumOf { it.size }
    }

    override suspend fun countAllVersions(): Int = versions.value.size
    override suspend fun countAllChunks(): Int = chunksMap.values.sumOf { it.size }
}

class IndexFileContentUseCaseTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val validTarget = IndexTargetValidator { projectId, laneId ->
        require(projectId == "proj-1") { "Project does not exist" }
        require(laneId == "lane-1") { "Lane does not exist" }
        ValidatedIndexTarget(projectId, laneId)
    }

    private fun useCase(vault: DiskVaultStorage, repo: FileVersionRepository = FakeFileVersionRepository()) =
        IndexFileContentUseCase(vault, repo, validTarget)

    @Test
    fun indexV1V2AndDedup_preserveExplicitLane() = runBlocking {
        val vault = DiskVaultStorage(tempFolder.newFolder("vault_versions"))
        val repo = FakeFileVersionRepository()
        val index = useCase(vault, repo)

        val v1 = index("proj-1", "lane-1", "src/lib.rs", "version one").getOrThrow()
        val duplicate = index("proj-1", "lane-1", "src/lib.rs", "version one").getOrThrow()
        val v2 = index("proj-1", "lane-1", "src/lib.rs", "version two").getOrThrow()

        assertEquals(1, v1.fileVersion.versionNumber)
        assertEquals("lane-1", v1.fileVersion.laneId)
        assertTrue(duplicate.isDeduplicated)
        assertEquals(v1.fileVersion.id, duplicate.fileVersion.id)
        assertEquals(2, v2.fileVersion.versionNumber)
        assertFalse(v2.isDeduplicated)
        assertEquals(2, repo.countVersions("proj-1"))
        assertTrue(vault.has(v2.fileVersion.sha256))
    }

    @Test
    fun invalidLane_failsBeforeWritingVaultBlob() = runBlocking {
        val vault = DiskVaultStorage(tempFolder.newFolder("vault_invalid"))
        val result = useCase(vault)("proj-1", "wrong-lane", "notes.md", "do not store")

        assertTrue(result.isFailure)
        assertEquals(0, vault.countBlobs())
    }

    @Test
    fun roomFailure_isReturnedInsteadOfThrown() = runBlocking {
        val vault = DiskVaultStorage(tempFolder.newFolder("vault_room_failure"))
        val repository = object : FakeFileVersionRepository() {
            override suspend fun recordVersion(version: FileVersion, chunks: List<FileChunk>) {
                error("Room write failed")
            }
        }

        val result = useCase(vault, repository)("proj-1", "lane-1", "notes.md", "safe failure")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Room write failed"))
    }

    @Test
    fun pathTraversal_isSanitized() = runBlocking {
        val vault = DiskVaultStorage(tempFolder.newFolder("vault_path"))
        val result = useCase(vault)("proj-1", "lane-1", "../../secret/keys.txt", "secret").getOrThrow()
        assertEquals("secret/keys.txt", result.fileVersion.path)
    }
}
