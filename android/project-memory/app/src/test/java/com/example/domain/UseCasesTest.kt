package com.example.domain

import com.example.domain.model.Project
import com.example.domain.model.ServerConfig
import com.example.domain.repository.ProjectRepository
import com.example.domain.usecase.GetSystemStatusUseCase
import com.example.domain.usecase.ReadStaticResourceUseCase
import com.example.domain.usecase.SaveProjectUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeProjectRepository : ProjectRepository {
    private val _projects = kotlinx.coroutines.flow.MutableStateFlow<List<Project>>(emptyList())

    override fun observeProjects(): Flow<List<Project>> = _projects
    override suspend fun getProject(id: String): Project? = _projects.value.find { it.id == id }
    override suspend fun getProjectBySlug(slug: String): Project? = _projects.value.find { it.slug == slug }
    override suspend fun insertOrUpdate(project: Project) {
        val current = _projects.value.toMutableList()
        current.removeAll { it.id == project.id }
        current.add(project)
        _projects.value = current
    }
    override suspend fun countProjects(): Int = _projects.value.size
    val currentCount: Int get() = _projects.value.size
    override suspend fun deleteProject(id: String): Boolean {
        val current = _projects.value.toMutableList()
        val removed = current.removeIf { it.id == id }
        _projects.value = current
        return removed
    }
}

class UseCasesTest {

    private val repository = FakeProjectRepository()
    private val saveProjectUseCase = SaveProjectUseCase(repository)
    private val getSystemStatusUseCase = GetSystemStatusUseCase(repository)
    private val readStaticResourceUseCase = ReadStaticResourceUseCase()

    @Test
    fun saveProject_sanitizesSlugAndStoresInRepository() = runBlocking {
        val project = Project(
            id = "test-1",
            slug = "My Project Spike",
            name = "My Project Spike",
            description = "Spike project",
            rootUri = "content://roots/test"
        )
        val result = saveProjectUseCase(project)
        assertTrue(result.isSuccess)
        val saved = result.getOrThrow()
        assertEquals("my-project-spike", saved.slug)
        assertEquals(1, repository.countProjects())
    }

    @Test
    fun getSystemStatus_reflectsRunningStateAndProjectCount() = runBlocking {
        repository.insertOrUpdate(
            Project(
                id = "p1",
                slug = "p1",
                name = "P1",
                rootUri = "content://root"
            )
        )

        val status = getSystemStatusUseCase(
            isRunning = true,
            config = ServerConfig(host = "127.0.0.1", port = 8080, isReadOnly = true),
            uptimeMs = 5000L
        )

        assertEquals("RUNNING", status.status)
        assertEquals(1, status.activeProjectsCount)
        assertEquals(8080, status.port)
        assertEquals("127.0.0.1", status.host)
        assertTrue(status.isReadOnly)
        assertEquals("Loopback-Authenticated", status.securityMode)
        assertEquals(5000L, status.uptimeMs)
    }

    @Test
    fun readStaticResource_returnsValidManifest() {
        val manifestResult = readStaticResourceUseCase("project://manifest")
        assertTrue(manifestResult.isSuccess)
        val manifest = manifestResult.getOrThrow()
        assertTrue(manifest.contains("Project Memory Provider"))
        assertTrue(manifest.contains("system_status"))
        assertTrue(manifest.contains("offline_local_storage"))
    }
}
