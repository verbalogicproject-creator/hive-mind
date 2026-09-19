package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.database.AppDatabase
import com.example.data.entity.ClientCredentialEntity
import com.example.data.entity.ClientInstallationEntity
import com.example.data.entity.MemoryLaneEntity
import com.example.data.repository.RoomClientIdentityRepository
import com.example.domain.model.CredentialType
import com.example.domain.model.LaneKind
import com.example.domain.model.ProjectScope
import com.example.domain.security.CallerContext
import com.example.domain.security.TokenCryptoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ClientIdentityAndV5MigrationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: RoomClientIdentityRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomClientIdentityRepository(db)
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    // 1. MIGRATION TEST: v4 to v5 migration preserves records and rebuilds with mandatory lane_id
    @Test
    fun test1_migration4To5_rebuildsTablesAndSeedsOwner() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_migration_4_5.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(sDb: SupportSQLiteDatabase) {
                    sDb.execSQL("""
                        CREATE TABLE IF NOT EXISTS projects (
                            id TEXT PRIMARY KEY NOT NULL,
                            slug TEXT NOT NULL,
                            name TEXT NOT NULL,
                            description TEXT NOT NULL DEFAULT '',
                            root_uri TEXT NOT NULL,
                            created_at INTEGER NOT NULL,
                            updated_at INTEGER NOT NULL
                        )
                    """.trimIndent())
                    sDb.execSQL("""
                        CREATE TABLE IF NOT EXISTS file_versions (
                            id TEXT PRIMARY KEY NOT NULL,
                            project_id TEXT NOT NULL,
                            path TEXT NOT NULL,
                            sha256 TEXT NOT NULL,
                            size_bytes INTEGER NOT NULL,
                            mime_type TEXT NOT NULL,
                            version_number INTEGER NOT NULL,
                            created_at INTEGER NOT NULL,
                            FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE
                        )
                    """.trimIndent())
                    sDb.execSQL("""
                        CREATE TABLE IF NOT EXISTS file_chunks (
                            id TEXT PRIMARY KEY NOT NULL,
                            file_version_id TEXT NOT NULL,
                            chunk_index INTEGER NOT NULL,
                            content TEXT NOT NULL,
                            token_count_estimate INTEGER NOT NULL,
                            sha256 TEXT NOT NULL,
                            FOREIGN KEY(file_version_id) REFERENCES file_versions(id) ON DELETE CASCADE
                        )
                    """.trimIndent())
                    sDb.execSQL("""
                        CREATE TABLE IF NOT EXISTS episodes (
                            id TEXT PRIMARY KEY NOT NULL,
                            project_id TEXT NOT NULL,
                            title TEXT NOT NULL,
                            summary TEXT NOT NULL DEFAULT '',
                            source TEXT NOT NULL,
                            tags_csv TEXT NOT NULL DEFAULT '',
                            created_at INTEGER NOT NULL,
                            FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE
                        )
                    """.trimIndent())
                    sDb.execSQL("""
                        CREATE TABLE IF NOT EXISTS episode_events (
                            id TEXT PRIMARY KEY NOT NULL,
                            episode_id TEXT NOT NULL,
                            sequence_number INTEGER NOT NULL,
                            event_type TEXT NOT NULL,
                            actor TEXT NOT NULL,
                            payload TEXT NOT NULL,
                            file_version_id TEXT,
                            created_at INTEGER NOT NULL,
                            FOREIGN KEY(episode_id) REFERENCES episodes(id) ON DELETE CASCADE,
                            FOREIGN KEY(file_version_id) REFERENCES file_versions(id) ON DELETE SET NULL
                        )
                    """.trimIndent())
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val v4Db = helper.writableDatabase

        // Insert a complete v4 provenance graph. The child rows are the regression
        // boundary: dropping a v4 parent before rebuilding its children loses data.
        v4Db.execSQL("INSERT INTO projects (id, slug, name, description, root_uri, created_at, updated_at) VALUES ('p-mig', 'mig-proj', 'Mig Proj', 'desc', 'root', 100, 100)")
        v4Db.execSQL("INSERT INTO file_versions (id, project_id, path, sha256, size_bytes, mime_type, version_number, created_at) VALUES ('fv-1', 'p-mig', 'main.kt', 'hash1', 20, 'text/plain', 1, 150)")
        v4Db.execSQL("INSERT INTO file_chunks (id, file_version_id, chunk_index, content, token_count_estimate, sha256) VALUES ('chunk-1', 'fv-1', 0, 'preserve me', 3, 'chunk-hash-1')")
        v4Db.execSQL("INSERT INTO episodes (id, project_id, title, summary, source, tags_csv, created_at) VALUES ('ep-1', 'p-mig', 'Ep 1', 'desc', 'agent', 'tag', 200)")
        v4Db.execSQL("INSERT INTO episode_events (id, episode_id, sequence_number, event_type, actor, payload, file_version_id, created_at) VALUES ('event-1', 'ep-1', 1, 'note', 'agent', 'preserve event', 'fv-1', 250)")

        // Run Migration 4 -> 5
        AppDatabase.MIGRATION_4_5.migrate(v4Db)

        // Verify seeded local-device-owner
        val ownerCursor = v4Db.query("SELECT id, installation_name FROM client_installations WHERE id = 'local-device-owner'")
        assertEquals(1, ownerCursor.count)
        ownerCursor.moveToFirst()
        assertEquals("Local Device Owner", ownerCursor.getString(1))
        ownerCursor.close()

        // Verify synthetic system legacy lane was created for p-mig
        val laneCursor = v4Db.query("SELECT id, lane_kind FROM memory_lanes WHERE project_id = 'p-mig' AND lane_kind = 'system_legacy'")
        assertEquals(1, laneCursor.count)
        laneCursor.moveToFirst()
        val legacyLaneId = laneCursor.getString(0)
        assertEquals("lane-legacy-p-mig", legacyLaneId)
        laneCursor.close()

        // Verify file_versions and episodes got legacyLaneId populated
        val fvCursor = v4Db.query("SELECT lane_id FROM file_versions WHERE id = 'fv-1'")
        fvCursor.moveToFirst()
        assertEquals(legacyLaneId, fvCursor.getString(0))
        fvCursor.close()

        val epCursor = v4Db.query("SELECT lane_id FROM episodes WHERE id = 'ep-1'")
        epCursor.moveToFirst()
        assertEquals(legacyLaneId, epCursor.getString(0))
        epCursor.close()

        val chunkCursor = v4Db.query("SELECT file_version_id, content FROM file_chunks WHERE id = 'chunk-1'")
        assertEquals(1, chunkCursor.count)
        chunkCursor.moveToFirst()
        assertEquals("fv-1", chunkCursor.getString(0))
        assertEquals("preserve me", chunkCursor.getString(1))
        chunkCursor.close()

        val eventCursor = v4Db.query("SELECT episode_id, file_version_id, payload FROM episode_events WHERE id = 'event-1'")
        assertEquals(1, eventCursor.count)
        eventCursor.moveToFirst()
        assertEquals("ep-1", eventCursor.getString(0))
        assertEquals("fv-1", eventCursor.getString(1))
        assertEquals("preserve event", eventCursor.getString(2))
        eventCursor.close()

        val foreignKeyCursor = v4Db.query("PRAGMA foreign_key_check")
        assertEquals(0, foreignKeyCursor.count)
        foreignKeyCursor.close()

        v4Db.close()
    }

    // 2. PAIRING LIFECYCLE: create and redeem pairing invitation
    @Test
    fun test2_pairingLifecycle_createsInstallationAndAccessCredential() = runBlocking {
        db.compileStatement("INSERT INTO projects (id, slug, name, description, root_uri, created_at, updated_at) VALUES ('proj-alpha', 'alpha', 'Alpha', '', 'root', 100, 100)").execute()

        val (invitation, rawPairingToken) = repository.createPairingInvitation(
            targetProjectId = "proj-alpha",
            targetLaneKind = LaneKind.PRIVATE,
            intendedClientName = "Codex CLI",
            scopes = listOf(ProjectScope.APPROVED_READ.value, ProjectScope.INBOX_APPEND.value)
        )

        assertNotNull(invitation.id)
        assertTrue(rawPairingToken.startsWith("pair_"))

        // Redeem invitation
        val result = repository.redeemPairingInvitation(
            pairingToken = rawPairingToken,
            installationName = "Codex CLI on Termux"
        )

        assertTrue(result.isSuccess)
        val (installation, rawAccessToken) = result.getOrThrow()
        assertEquals("Codex CLI on Termux", installation.installationName)
        assertTrue(rawAccessToken.startsWith("pm_"))

        // Authenticate using newly minted raw access token
        val caller = repository.authenticateBearerToken(rawAccessToken)
        assertNotNull(caller)
        assertEquals(installation.id, caller?.clientInstallationId)
        assertFalse(caller!!.isSystemOwner)
    }

    // 3. ONE-TIME USE: pairing invitation cannot be redeemed twice
    @Test
    fun test3_pairingInvitation_cannotBeRedeemedTwice() = runBlocking {
        db.compileStatement("INSERT INTO projects (id, slug, name, description, root_uri, created_at, updated_at) VALUES ('proj-beta', 'beta', 'Beta', '', 'root', 100, 100)").execute()

        val (_, rawToken) = repository.createPairingInvitation(
            targetProjectId = "proj-beta",
            targetLaneKind = LaneKind.PRIVATE,
            intendedClientName = "Single Use Agent",
            scopes = listOf(ProjectScope.PROJECT_DISCOVER.value)
        )

        val firstRedeem = repository.redeemPairingInvitation(rawToken, "Agent 1")
        assertTrue(firstRedeem.isSuccess)

        val secondRedeem = repository.redeemPairingInvitation(rawToken, "Agent 2")
        assertTrue(secondRedeem.isFailure)
        assertTrue(secondRedeem.exceptionOrNull()?.message?.contains("already consumed") == true)
    }

    // 4. EXPIRY: expired pairing invitation cannot be redeemed
    @Test
    fun test4_expiredPairingInvitation_fails() = runBlocking {
        db.compileStatement("INSERT INTO projects (id, slug, name, description, root_uri, created_at, updated_at) VALUES ('proj-gamma', 'gamma', 'Gamma', '', 'root', 100, 100)").execute()

        val (_, rawToken) = repository.createPairingInvitation(
            targetProjectId = "proj-gamma",
            targetLaneKind = LaneKind.PRIVATE,
            intendedClientName = "Expired Agent",
            scopes = listOf(ProjectScope.PROJECT_DISCOVER.value),
            ttlMs = -5000 // Negative TTL -> already expired
        )

        val result = repository.redeemPairingInvitation(rawToken, "Late Agent")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("expired") == true)
    }

    // 5. PAIRING TOKEN CANNOT AUTHENTICATE MCP ACCESS
    @Test
    fun test5_pairingToken_cannotAuthorizeMcpTools() = runBlocking {
        db.compileStatement("INSERT INTO projects (id, slug, name, description, root_uri, created_at, updated_at) VALUES ('proj-delta', 'delta', 'Delta', '', 'root', 100, 100)").execute()

        val (_, rawPairingToken) = repository.createPairingInvitation(
            targetProjectId = "proj-delta",
            targetLaneKind = LaneKind.PRIVATE,
            intendedClientName = "Pairing Only",
            scopes = listOf(ProjectScope.APPROVED_READ.value)
        )

        val caller = repository.authenticateBearerToken(rawPairingToken)
        assertNull(caller) // Must be rejected
    }

    // 6. REVOCATION & ROTATION
    @Test
    fun test6_revokedInstallationAndRotation() = runBlocking {
        db.compileStatement("INSERT INTO projects (id, slug, name, description, root_uri, created_at, updated_at) VALUES ('proj-rev', 'rev', 'Rev', '', 'root', 100, 100)").execute()

        val (_, pairToken) = repository.createPairingInvitation("proj-rev", LaneKind.PRIVATE, "Rotatable", listOf(ProjectScope.APPROVED_READ.value))
        val (installation, initialAccessToken) = repository.redeemPairingInvitation(pairToken, "Rotatable Client").getOrThrow()

        // 1. Valid before rotation
        val caller1 = repository.authenticateBearerToken(initialAccessToken)
        assertNotNull(caller1)

        // 2. Rotate credential
        val newAccessToken = repository.rotateAccessCredential(installation.id).getOrThrow()
        val callerOld = repository.authenticateBearerToken(initialAccessToken)
        assertNull(callerOld) // Old token invalidated

        val callerNew = repository.authenticateBearerToken(newAccessToken)
        assertNotNull(callerNew) // New token works

        // 3. Revoke installation entirely
        repository.revokeInstallation(installation.id)
        val callerRevoked = repository.authenticateBearerToken(newAccessToken)
        assertNull(callerRevoked)
    }

    // 7. PROJECT & LANE SCOPE ENFORCEMENT
    @Test
    fun test7_projectAndLaneScopeEnforcement() = runBlocking {
        db.compileStatement("INSERT INTO projects (id, slug, name, description, root_uri, created_at, updated_at) VALUES ('p-scope', 'p-scope', 'Scope Test', '', 'root', 100, 100)").execute()
        val laneA = repository.createMemoryLane("p-scope", "lane-a", LaneKind.PRIVATE, "Lane A").getOrThrow()
        val laneB = repository.createMemoryLane("p-scope", "lane-b", LaneKind.PRIVATE, "Lane B").getOrThrow()

        val clientId = "client-scoped"
        db.clientIdentityDao().insertInstallation(ClientInstallationEntity(clientId, "Scoped Client", 100, null))

        // Grant only project approved:read, and read-only to laneA
        repository.grantProjectScope(clientId, "p-scope", listOf(ProjectScope.APPROVED_READ.value))
        repository.grantLaneAccess(clientId, laneA.id, canRead = true, canWrite = false)

        assertTrue(repository.hasProjectScope(clientId, "p-scope", ProjectScope.APPROVED_READ.value))
        assertFalse(repository.hasProjectScope(clientId, "p-scope", ProjectScope.INBOX_APPEND.value))

        assertTrue(repository.hasLaneAccess(clientId, laneA.id, writeRequired = false))
        assertFalse(repository.hasLaneAccess(clientId, laneA.id, writeRequired = true))

        assertFalse(repository.hasLaneAccess(clientId, laneB.id, writeRequired = false))
    }

    // 8. SYSTEM_LEGACY LANES ARE LOCAL-DEVICE-OWNER ONLY
    @Test
    fun test8_systemLegacyLanes_restrictedToOwner() = runBlocking {
        db.compileStatement("INSERT INTO projects (id, slug, name, description, root_uri, created_at, updated_at) VALUES ('p-sys', 'p-sys', 'System Test', '', 'root', 100, 100)").execute()
        val legacyLane = repository.createMemoryLane("p-sys", "lane-legacy-p-sys", LaneKind.SYSTEM_LEGACY, "Legacy Lane").getOrThrow()

        val clientId = "client-external"
        db.clientIdentityDao().insertInstallation(ClientInstallationEntity(clientId, "External", 100, null))
        val caller = CallerContext(clientId, "External", "c-1", CredentialType.ACCESS)

        // Attempt to start session in legacy lane as external client
        val res = repository.startSession(caller, "p-sys", legacyLane.id, null, null)
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull()?.message?.contains("SYSTEM_LEGACY lanes are reserved for local device owner") == true)
    }

    @Test
    fun connectionContext_returnsOnlyActiveCallerScopedProjectsAndLanes() = runBlocking {
        db.compileStatement("INSERT INTO projects (id, slug, name, description, root_uri, created_at, updated_at) VALUES ('p-visible', 'visible', 'Visible', '', 'root', 100, 100)").execute()
        db.compileStatement("INSERT INTO projects (id, slug, name, description, root_uri, created_at, updated_at) VALUES ('p-hidden', 'hidden', 'Hidden', '', 'root', 100, 100)").execute()
        val visibleLane = repository.createMemoryLane("p-visible", "private-visible", LaneKind.PRIVATE, "Visible lane").getOrThrow()
        val hiddenLane = repository.createMemoryLane("p-hidden", "private-hidden", LaneKind.PRIVATE, "Hidden lane").getOrThrow()

        db.clientIdentityDao().insertInstallation(ClientInstallationEntity("caller-visible", "Visible client", 100, null))
        db.clientIdentityDao().insertInstallation(ClientInstallationEntity("caller-hidden", "Hidden client", 100, null))
        repository.grantProjectScope(
            "caller-visible",
            "p-visible",
            listOf(ProjectScope.APPROVED_READ.value, ProjectScope.SESSION_START.value)
        )
        repository.grantLaneAccess("caller-visible", visibleLane.id, canRead = true, canWrite = true)
        repository.grantProjectScope("caller-hidden", "p-hidden", listOf(ProjectScope.APPROVED_READ.value))
        repository.grantLaneAccess("caller-hidden", hiddenLane.id, canRead = true, canWrite = false)

        val context = repository.getConnectionContext(
            CallerContext("caller-visible", "Visible client", "credential-visible", CredentialType.ACCESS)
        ).getOrThrow()

        assertEquals("caller-visible", context.clientInstallationId)
        assertEquals(listOf("p-visible"), context.projects.map { it.projectId })
        assertEquals(listOf(visibleLane.id), context.projects.single().lanes.map { it.laneId })
        assertTrue(context.projects.single().lanes.single().canWrite)
        assertFalse(context.projects.any { it.projectId == "p-hidden" })
    }

    // 9. SESSION LIFECYCLE & OPTIMISTIC CONCURRENCY ON CLOSE
    @Test
    fun test9_sessionLifecycleAndOptimisticClose() = runBlocking {
        db.compileStatement("INSERT INTO projects (id, slug, name, description, root_uri, created_at, updated_at) VALUES ('p-sess', 'p-sess', 'Sess Test', '', 'root', 100, 100)").execute()
        val lane = repository.createMemoryLane("p-sess", "lane-sess", LaneKind.PRIVATE, "Session Lane").getOrThrow()
        val caller = CallerContext.LOCAL_OWNER

        val sessionResult = repository.startSession(caller, "p-sess", lane.id, "google", "gemini-1.5-pro")
        assertTrue(sessionResult.isSuccess)
        val session = sessionResult.getOrThrow()
        assertEquals(1L, session.revision)

        // Wrong expected revision should fail
        val wrongRevClose = repository.closeSession(caller, session.id, expectedRevision = 99L)
        assertTrue(wrongRevClose.isFailure)

        // Correct expected revision should succeed
        val correctClose = repository.closeSession(caller, session.id, expectedRevision = 1L)
        assertTrue(correctClose.isSuccess)

        // Verify session row updated
        val updated = db.clientIdentityDao().getSessionById(session.id)
        assertNotNull(updated)
        assertEquals(2L, updated?.revision)
        assertNotNull(updated?.closedAt)
    }

    // 10. INBOX ITEM PERSISTENCE & VERSION ADDRESSABILITY
    @Test
    fun test10_inboxItemAppendAndVersionAddressability() = runBlocking {
        db.compileStatement("INSERT INTO projects (id, slug, name, description, root_uri, created_at, updated_at) VALUES ('p-inbox', 'p-inbox', 'Inbox Test', '', 'root', 100, 100)").execute()
        val lane = repository.createMemoryLane("p-inbox", "lane-inbox", LaneKind.PRIVATE, "Inbox Lane").getOrThrow()
        val session = repository.startSession(CallerContext.LOCAL_OWNER, "p-inbox", lane.id, null, null).getOrThrow()

        val item1 = repository.appendInboxItem(
            caller = CallerContext.LOCAL_OWNER,
            projectId = "p-inbox",
            sessionId = session.id,
            itemId = "chk-1",
            itemType = "checkpoint",
            title = "Initial Checkpoint",
            payloadJson = """{"status":"ready"}""",
            idempotencyKey = "idem-chk-1"
        ).getOrThrow()

        assertEquals("chk-1", item1.itemId)
        assertEquals(1L, item1.sequenceNumber)
        assertNotNull(item1.contentHash)

        // Append version 2 of the same item
        val item2 = repository.appendInboxItem(
            caller = CallerContext.LOCAL_OWNER,
            projectId = "p-inbox",
            sessionId = session.id,
            itemId = "chk-1",
            itemType = "checkpoint",
            title = "Updated Checkpoint",
            payloadJson = """{"status":"in_progress"}""",
            idempotencyKey = "idem-chk-2",
            supersedesVersionId = item1.versionId
        ).getOrThrow()

        assertEquals("chk-1", item2.itemId)
        assertEquals(2L, item2.sequenceNumber)
        assertEquals(item1.versionId, item2.supersedesVersionId)

        val items = repository.getInboxItems(CallerContext.LOCAL_OWNER, session.id).getOrThrow()
        assertEquals(2, items.size)
        assertEquals("Initial Checkpoint", items[0].title)
        assertEquals("Updated Checkpoint", items[1].title)
    }

    // 11. IDEMPOTENCY: Same idempotency key returns identical stored result
    @Test
    fun test11_idempotency_returnsIdenticalResult() = runBlocking {
        db.compileStatement("INSERT INTO projects (id, slug, name, description, root_uri, created_at, updated_at) VALUES ('p-idem', 'p-idem', 'Idem Test', '', 'root', 100, 100)").execute()
        val lane = repository.createMemoryLane("p-idem", "lane-idem", LaneKind.PRIVATE, "Idem Lane").getOrThrow()
        val session = repository.startSession(CallerContext.LOCAL_OWNER, "p-idem", lane.id, null, null).getOrThrow()

        val call1 = repository.appendInboxItem(
            caller = CallerContext.LOCAL_OWNER,
            projectId = "p-idem",
            sessionId = session.id,
            itemId = "idem-target",
            itemType = "note",
            title = "Idempotent Note",
            payloadJson = """{"note":"hello"}""",
            idempotencyKey = "key-alpha-999"
        ).getOrThrow()

        val call2 = repository.appendInboxItem(
            caller = CallerContext.LOCAL_OWNER,
            projectId = "p-idem",
            sessionId = session.id,
            itemId = "idem-target",
            itemType = "note",
            title = "Idempotent Note",
            payloadJson = """{"note":"hello"}""",
            idempotencyKey = "key-alpha-999"
        ).getOrThrow()

        assertEquals(call1.versionId, call2.versionId)
        assertEquals(call1.sequenceNumber, call2.sequenceNumber)
        assertEquals(call1.contentHash, call2.contentHash)

        val items = repository.getInboxItems(CallerContext.LOCAL_OWNER, session.id).getOrThrow()
        assertEquals(1, items.size) // No duplicate row created
    }

    // 12. IDEMPOTENCY MISMATCH: Same key with different payload is rejected
    @Test
    fun test12_idempotencyMismatch_fails() = runBlocking {
        db.compileStatement("INSERT INTO projects (id, slug, name, description, root_uri, created_at, updated_at) VALUES ('p-mismatch', 'p-mismatch', 'Mismatch Test', '', 'root', 100, 100)").execute()
        val lane = repository.createMemoryLane("p-mismatch", "lane-mismatch", LaneKind.PRIVATE, "Mismatch Lane").getOrThrow()
        val session = repository.startSession(CallerContext.LOCAL_OWNER, "p-mismatch", lane.id, null, null).getOrThrow()

        repository.appendInboxItem(
            caller = CallerContext.LOCAL_OWNER,
            projectId = "p-mismatch",
            sessionId = session.id,
            itemId = "target",
            itemType = "note",
            title = "Original Title",
            payloadJson = """{"v":1}""",
            idempotencyKey = "fixed-key-1"
        )

        val mismatchResult = repository.appendInboxItem(
            caller = CallerContext.LOCAL_OWNER,
            projectId = "p-mismatch",
            sessionId = session.id,
            itemId = "target",
            itemType = "note",
            title = "Conflicting Title", // Different title -> different requestHash
            payloadJson = """{"v":2}""",
            idempotencyKey = "fixed-key-1"
        )

        assertTrue(mismatchResult.isFailure)
        assertTrue(mismatchResult.exceptionOrNull()?.message?.contains("Idempotency conflict") == true)
    }

    // 13. CONCURRENCY: Multiple coroutines concurrently appending to session inbox
    @Test
    fun test13_concurrentInboxAppends_preserveStrictSequence() = runBlocking {
        db.compileStatement("INSERT INTO projects (id, slug, name, description, root_uri, created_at, updated_at) VALUES ('p-conc', 'p-conc', 'Conc Test', '', 'root', 100, 100)").execute()
        val lane = repository.createMemoryLane("p-conc", "lane-conc", LaneKind.PRIVATE, "Conc Lane").getOrThrow()
        val session = repository.startSession(CallerContext.LOCAL_OWNER, "p-conc", lane.id, null, null).getOrThrow()

        val numberOfConcurrentAppends = 20
        val jobs = (1..numberOfConcurrentAppends).map { index ->
            async(Dispatchers.IO) {
                repository.appendInboxItem(
                    caller = CallerContext.LOCAL_OWNER,
                    projectId = "p-conc",
                    sessionId = session.id,
                    itemId = "item-$index",
                    itemType = "log",
                    title = "Log #$index",
                    payloadJson = """{"index":$index}""",
                    idempotencyKey = "key-conc-$index"
                )
            }
        }

        val results = jobs.awaitAll()
        results.forEach { assertTrue(it.isSuccess) }

        val items = repository.getInboxItems(CallerContext.LOCAL_OWNER, session.id).getOrThrow()
        assertEquals(numberOfConcurrentAppends, items.size)

        // Verify sequence numbers are strictly 1..20 with zero duplicates or gaps
        val seqs = items.map { it.sequenceNumber }.sorted()
        assertEquals((1L..numberOfConcurrentAppends.toLong()).toList(), seqs)
    }
}
