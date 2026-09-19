package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.ClientInstallationEntity
import com.example.data.entity.ClientProjectGrantEntity
import com.example.data.entity.ProjectEntity
import com.example.data.entity.MemoryLaneEntity
import com.example.data.entity.SessionEntity
import com.example.data.entity.SessionInboxItemEntity
import com.example.data.repository.ProjectRepositoryImpl
import com.example.data.repository.RoomOwnerMemoryRepository
import com.example.data.repository.RoomProjectProvisioningRepository
import com.example.domain.model.AgentConnectionState
import com.example.domain.model.GraphNodeType
import com.example.domain.model.Project
import com.example.domain.model.SessionStatus
import com.example.domain.usecase.GetGraphProjectionUseCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StabilityProjectionTest {
    private lateinit var db: AppDatabase
    private lateinit var provisioning: RoomProjectProvisioningRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        provisioning = RoomProjectProvisioningRepository(db)
    }

    @After
    fun close() = db.close()

    @Test
    fun createProject_provisionsOwnerLaneAndGrantsAtomically() = runBlocking {
        val project = project("p-new", "new-project")
        val created = provisioning.createProject(project).getOrThrow()

        assertEquals(project.id, created.project.id)
        assertEquals("private", created.ownerLane.laneKind.value)
        assertNotNull(db.clientIdentityDao().getActiveProjectGrant(RoomProjectProvisioningRepository.OWNER_ID, project.id))
        val laneGrant = db.clientIdentityDao().getActiveLaneGrant(
            RoomProjectProvisioningRepository.OWNER_ID,
            created.ownerLane.id
        )
        assertTrue(laneGrant?.canRead == true)
        assertTrue(laneGrant?.canWrite == true)

        val duplicate = provisioning.createProject(project("p-other", "new-project"))
        assertTrue(duplicate.isFailure)
        assertEquals(1, db.projectDao().count())
    }

    @Test
    fun repairOwnerAccess_isIdempotentAndPreservesProject() = runBlocking {
        db.projectDao().insert(ProjectEntity.fromDomain(project("p-repair", "repair")))

        val first = provisioning.repairOwnerAccess().getOrThrow()
        val lane = db.clientIdentityDao().getActiveOwnerLane("p-repair")
        val second = provisioning.repairOwnerAccess().getOrThrow()

        assertEquals(1, first.projectsRepaired)
        assertEquals(0, second.projectsRepaired)
        assertNotNull(db.projectDao().getById("p-repair"))
        assertNotNull(lane)
        assertNotNull(db.clientIdentityDao().getActiveLaneGrant(RoomProjectProvisioningRepository.OWNER_ID, lane!!.id))
    }

    @Test
    fun ownerProjections_keepSameProviderInstallationsSeparateAndRetainRevokedHistory() = runBlocking {
        val project = provisioning.createProject(project("p-agents", "agents")).getOrThrow().project
        val dao = db.clientIdentityDao()
        val now = 10_000L
        val first = ClientInstallationEntity("agent-1", "Codex phone", now, null)
        val second = ClientInstallationEntity("agent-2", "Codex laptop", now + 1, now + 100)
        dao.insertInstallation(first)
        dao.insertInstallation(second)
        listOf(first, second).forEach { installation ->
            dao.insertProjectGrant(
                ClientProjectGrantEntity(
                    "grant-${installation.id}", installation.id, project.id,
                    "project:discover,approved:read,inbox:append,session:start", now, null
                )
            )
            dao.insertLane(
                MemoryLaneEntity(
                    "lane-${installation.id}", project.id, "agent-${installation.id}",
                    "private", "${installation.installationName} lane", false, now
                )
            )
            dao.insertSession(
                SessionEntity(
                    "session-${installation.id}", project.id, installation.id,
                    "lane-${installation.id}", "codex", "gpt-5", SessionStatus.ACTIVE.value,
                    now + if (installation.id == "agent-1") 1 else 2, null, 1
                )
            )
            dao.insertInboxItem(
                SessionInboxItemEntity(
                    "version-${installation.id}", "item-${installation.id}", null,
                    "session-${installation.id}", 1, "plan", "Plan ${installation.id}",
                    "{\"body\":\"memory\"}", null, "hash-${installation.id}", now + 3
                )
            )
        }

        val ownerMemory = RoomOwnerMemoryRepository(db)
        val summaries = ownerMemory.getAgentSummaries(project.id)
        val revoked = summaries.single { it.installationId == "agent-2" }
        val detail = ownerMemory.getMemoryDetail(project.id, "version-agent-2")

        assertEquals(2, summaries.size)
        assertNotEquals(summaries[0].installationId, summaries[1].installationId)
        assertTrue(summaries.all { it.latestReportedProvider == "codex" })
        assertEquals(AgentConnectionState.REVOKED, revoked.connectionState)
        assertEquals("hash-agent-2", detail?.contentHash)
        assertEquals(2, ownerMemory.getProjectMemory(project.id).size)
        assertTrue(ownerMemory.getAgentSummaries("missing-project").isEmpty())
    }

    @Test
    fun graphProjection_isBoundedAndSourceAddressable() = runBlocking {
        val project = provisioning.createProject(project("p-graph", "graph")).getOrThrow().project
        val projection = GetGraphProjectionUseCase(
            ProjectRepositoryImpl(db.projectDao()),
            RoomOwnerMemoryRepository(db)
        )(project.id).getOrThrow()

        assertEquals(1, projection.nodes.size)
        assertEquals(GraphNodeType.PROJECT, projection.nodes.single().type)
        assertTrue(projection.nodes.single().sourceUri.startsWith("memory://project/"))
        assertFalse(projection.isTruncated)
    }

    private fun project(id: String, slug: String) = Project(
        id = id,
        slug = slug,
        name = slug.replace('-', ' '),
        rootUri = "content://projects/$slug",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L
    )
}
