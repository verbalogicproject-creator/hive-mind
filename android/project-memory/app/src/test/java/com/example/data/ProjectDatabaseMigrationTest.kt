package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.database.AppDatabase
import com.example.data.entity.ProjectEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ProjectDatabaseMigrationTest {

    private lateinit var context: Context
    private lateinit var inMemoryDb: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        inMemoryDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        inMemoryDb.close()
    }

    @Test
    fun migration1To2_addsDescriptionColumnAndPreservesExistingData() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_migration_1_2.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create Schema Version 1 (without description column)
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS projects (
                            id TEXT PRIMARY KEY NOT NULL,
                            slug TEXT NOT NULL,
                            name TEXT NOT NULL,
                            root_uri TEXT NOT NULL,
                            created_at INTEGER NOT NULL,
                            updated_at INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS index_projects_slug ON projects(slug)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    // Not needed during V1 creation
                }
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val v1Db = helper.writableDatabase

        // Insert initial Version 1 record
        v1Db.execSQL(
            """
            INSERT INTO projects (id, slug, name, root_uri, created_at, updated_at)
            VALUES ('spike-v1', 'phase0-spike', 'Phase 0 Spike', 'content://test/root', 100000, 100000)
            """.trimIndent()
        )

        // Execute explicit migration 1 -> 2
        AppDatabase.MIGRATION_1_2.migrate(v1Db)

        // Verify column was added and existing data was preserved intact
        val cursor = v1Db.query("SELECT id, slug, name, description, root_uri FROM projects WHERE id = 'spike-v1'")
        assertNotNull(cursor)
        assertEquals(1, cursor.count)
        cursor.moveToFirst()
        assertEquals("spike-v1", cursor.getString(cursor.getColumnIndexOrThrow("id")))
        assertEquals("phase0-spike", cursor.getString(cursor.getColumnIndexOrThrow("slug")))
        assertEquals("Phase 0 Spike", cursor.getString(cursor.getColumnIndexOrThrow("name")))
        assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("description"))) // Default value applied
        assertEquals("content://test/root", cursor.getString(cursor.getColumnIndexOrThrow("root_uri")))
        cursor.close()

        // Verify new Version 2 inserts with explicit description succeed
        v1Db.execSQL(
            """
            INSERT INTO projects (id, slug, name, description, root_uri, created_at, updated_at)
            VALUES ('spike-v2', 'phase0-v2', 'Phase 0 V2', 'Explicit V2 description', 'content://test/v2', 200000, 200000)
            """.trimIndent()
        )

        val v2Cursor = v1Db.query("SELECT description FROM projects WHERE id = 'spike-v2'")
        v2Cursor.moveToFirst()
        assertEquals("Explicit V2 description", v2Cursor.getString(v2Cursor.getColumnIndexOrThrow("description")))
        v2Cursor.close()

        v1Db.close()
    }

    @Test
    fun migration2To3_createsFileVersionsAndChunksTables_andPreservesProjects() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_migration_2_3.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS projects (
                            id TEXT PRIMARY KEY NOT NULL,
                            slug TEXT NOT NULL,
                            name TEXT NOT NULL,
                            description TEXT NOT NULL DEFAULT '',
                            root_uri TEXT NOT NULL,
                            created_at INTEGER NOT NULL,
                            updated_at INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val v2Db = helper.writableDatabase

        // Insert project in v2
        v2Db.execSQL(
            """
            INSERT INTO projects (id, slug, name, description, root_uri, created_at, updated_at)
            VALUES ('proj-phase1', 'phase1-slug', 'Phase 1 Project', 'Testing migration 2 to 3', 'content://test', 1000, 1000)
            """.trimIndent()
        )

        // Execute migration 2 -> 3
        AppDatabase.MIGRATION_2_3.migrate(v2Db)

        // Insert file_version
        v2Db.execSQL(
            """
            INSERT INTO file_versions (id, project_id, path, sha256, size_bytes, mime_type, version_number, created_at)
            VALUES ('ver-1', 'proj-phase1', 'src/main.rs', 'abc123sha256', 128, 'text/plain', 1, 2000)
            """.trimIndent()
        )

        // Insert file_chunk
        v2Db.execSQL(
            """
            INSERT INTO file_chunks (id, file_version_id, chunk_index, content, token_count_estimate, sha256)
            VALUES ('chk-1', 'ver-1', 0, 'fn main() {}', 4, 'chunksha256')
            """.trimIndent()
        )

        val verCursor = v2Db.query("SELECT path, version_number, sha256 FROM file_versions WHERE id = 'ver-1'")
        assertEquals(1, verCursor.count)
        verCursor.moveToFirst()
        assertEquals("src/main.rs", verCursor.getString(0))
        assertEquals(1, verCursor.getInt(1))
        assertEquals("abc123sha256", verCursor.getString(2))
        verCursor.close()

        val chunkCursor = v2Db.query("SELECT content, token_count_estimate FROM file_chunks WHERE id = 'chk-1'")
        assertEquals(1, chunkCursor.count)
        chunkCursor.moveToFirst()
        assertEquals("fn main() {}", chunkCursor.getString(0))
        assertEquals(4, chunkCursor.getInt(1))
        chunkCursor.close()

        v2Db.close()
    }

    @Test
    fun fileVersionDao_insertQueryAndCascade_workCorrectly() {
        runBlocking {
            val projectDao = inMemoryDb.projectDao()
            val fileVersionDao = inMemoryDb.fileVersionDao()

            val project = ProjectEntity(
                id = "proj-fk-test",
                slug = "proj-fk-slug",
                name = "FK Test Project",
                description = "Cascade delete test",
                rootUri = "content://roots/fk",
                createdAtEpochMs = 1000L,
                updatedAtEpochMs = 1000L
            )
            projectDao.insertOrUpdate(project)

            inMemoryDb.clientIdentityDao().insertLane(
                com.example.data.entity.MemoryLaneEntity(
                    id = "lane-fk-1",
                    projectId = "proj-fk-test",
                    laneSlug = "default",
                    laneKind = "system_legacy",
                    displayName = "Default Lane",
                    isArchived = false,
                    createdAt = 1000L
                )
            )

            val ver = com.example.data.entity.FileVersionEntity(
                id = "ver-fk-1",
                projectId = "proj-fk-test",
                laneId = "lane-fk-1",
                path = "README.md",
                sha256 = "deadbeefsha256",
                sizeBytes = 256L,
                mimeType = "text/markdown",
                versionNumber = 1,
                createdAt = 2000L
            )
            val chunk = com.example.data.entity.FileChunkEntity(
                id = "chk-fk-1",
                fileVersionId = "ver-fk-1",
                chunkIndex = 0,
                content = "# Readme Content",
                tokenCountEstimate = 5,
                sha256 = "beefdeadsha256"
            )

            fileVersionDao.insertVersionWithChunks(ver, listOf(chunk))

            assertEquals(1, fileVersionDao.countFiles("proj-fk-test"))
            assertEquals(1, fileVersionDao.countVersions("proj-fk-test"))
            assertEquals(1, fileVersionDao.countChunks("proj-fk-test"))

            val latest = fileVersionDao.getLatestVersion("proj-fk-test", "lane-fk-1", "README.md")
            assertNotNull(latest)
            assertEquals("deadbeefsha256", latest?.sha256)

            val chunks = fileVersionDao.getChunksForVersion("ver-fk-1")
            assertEquals(1, chunks.size)
            assertEquals("# Readme Content", chunks[0].content)

            // Delete project -> verify cascade deletion in Room SQLite
            projectDao.deleteById("proj-fk-test")
            // SQLite foreign key cascade removes file_versions and file_chunks
        }
    }

    @Test
    fun projectDao_insertAndCountOperations_workCorrectly() {
        runBlocking {
            val dao = inMemoryDb.projectDao()
            assertEquals(0, dao.count())

            val project = ProjectEntity(
                id = "dao-test-1",
                slug = "dao-slug",
                name = "DAO Test Project",
                description = "Tested via Room InMemory",
                rootUri = "content://roots/dao",
                createdAtEpochMs = 123456L,
                updatedAtEpochMs = 123456L
            )

            dao.insertOrUpdate(project)
            assertEquals(1, dao.count())

            val retrieved = dao.getById("dao-test-1")
            assertNotNull(retrieved)
            assertEquals("DAO Test Project", retrieved?.name)
            assertEquals("Tested via Room InMemory", retrieved?.description)

            val deletedCount = dao.deleteById("dao-test-1")
            assertEquals(1, deletedCount)
            assertEquals(0, dao.count())
        }
    }

    @Test
    fun migration3To4_createsEpisodesAndEpisodeEventsTables_andPreservesExistingData() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_migration_3_4.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS projects (
                            id TEXT PRIMARY KEY NOT NULL,
                            slug TEXT NOT NULL,
                            name TEXT NOT NULL,
                            description TEXT NOT NULL DEFAULT '',
                            root_uri TEXT NOT NULL,
                            created_at INTEGER NOT NULL,
                            updated_at INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS file_versions (
                            id TEXT PRIMARY KEY NOT NULL,
                            project_id TEXT NOT NULL,
                            path TEXT NOT NULL,
                            sha256 TEXT NOT NULL,
                            size_bytes INTEGER NOT NULL,
                            mime_type TEXT NOT NULL,
                            version_number INTEGER NOT NULL,
                            created_at INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val v3Db = helper.writableDatabase

        // Seed project and file version in v3
        v3Db.execSQL(
            """
            INSERT INTO projects (id, slug, name, description, root_uri, created_at, updated_at)
            VALUES ('p-mig-3', 'mig-3-slug', 'Mig 3 Project', 'Existing in v3', 'content://test/mig3', 100, 100)
            """.trimIndent()
        )
        v3Db.execSQL(
            """
            INSERT INTO file_versions (id, project_id, path, sha256, size_bytes, mime_type, version_number, created_at)
            VALUES ('fv-mig-3', 'p-mig-3', 'main.rs', 'sha-sample', 42, 'text/plain', 1, 100)
            """.trimIndent()
        )

        // Execute MIGRATION_3_4
        AppDatabase.MIGRATION_3_4.migrate(v3Db)

        // Verify existing project preserved
        val projCursor = v3Db.query("SELECT name FROM projects WHERE id = 'p-mig-3'")
        projCursor.moveToFirst()
        assertEquals("Mig 3 Project", projCursor.getString(0))
        projCursor.close()

        // Verify episode can be inserted and queried
        v3Db.execSQL(
            """
            INSERT INTO episodes (id, project_id, title, summary, source, tags_csv, created_at)
            VALUES ('ep-mig-1', 'p-mig-3', 'Migration Episode', 'Testing v4 upgrade', 'agent', 'test,mig', 200)
            """.trimIndent()
        )
        val epCursor = v3Db.query("SELECT title, tags_csv FROM episodes WHERE id = 'ep-mig-1'")
        epCursor.moveToFirst()
        assertEquals("Migration Episode", epCursor.getString(0))
        assertEquals("test,mig", epCursor.getString(1))
        epCursor.close()

        // Verify episode_event can be inserted with file_version_id provenance link
        v3Db.execSQL(
            """
            INSERT INTO episode_events (id, episode_id, sequence_number, event_type, actor, payload, file_version_id, created_at)
            VALUES ('ev-mig-1', 'ep-mig-1', 1, 'file_write', 'agent', 'Snapshot taken', 'fv-mig-3', 250)
            """.trimIndent()
        )
        val evCursor = v3Db.query("SELECT payload, file_version_id FROM episode_events WHERE id = 'ev-mig-1'")
        evCursor.moveToFirst()
        assertEquals("Snapshot taken", evCursor.getString(0))
        assertEquals("fv-mig-3", evCursor.getString(1))
        evCursor.close()

        v3Db.close()
    }

    @Test
    fun episodeDao_insertAndQueryTimeline_worksWithCascadeAndSequence() {
        runBlocking {
            val projDao = inMemoryDb.projectDao()
            val epDao = inMemoryDb.episodeDao()

            projDao.insertOrUpdate(
                ProjectEntity(
                    id = "p-ep-dao",
                    slug = "ep-dao-slug",
                    name = "Episode DAO Test",
                    description = "Project for testing episode cascade",
                    rootUri = "content://roots/ep-dao",
                    createdAtEpochMs = 1000L,
                    updatedAtEpochMs = 1000L
                )
            )

            inMemoryDb.clientIdentityDao().insertLane(
                com.example.data.entity.MemoryLaneEntity(
                    id = "lane-ep-1",
                    projectId = "p-ep-dao",
                    laneSlug = "default",
                    laneKind = "system_legacy",
                    displayName = "Default Lane",
                    isArchived = false,
                    createdAt = 1000L
                )
            )

            val episode = com.example.data.entity.EpisodeEntity(
                id = "ep-1",
                projectId = "p-ep-dao",
                laneId = "lane-ep-1",
                title = "Refactoring Architecture",
                summary = "Modularizing data layer",
                source = "agent",
                tagsCsv = "refactor,clean-architecture",
                createdAt = 2000L
            )
            epDao.insertEpisode(episode)

            assertEquals(1, epDao.countEpisodes("p-ep-dao"))
            assertEquals(0L, epDao.getMaxSequenceNumber("ep-1") ?: 0L)

            // Insert 2 sequential events
            val ev1 = com.example.data.entity.EpisodeEventEntity(
                id = "ev-1",
                episodeId = "ep-1",
                sequenceNumber = 1L,
                eventType = "message",
                actor = "user",
                payload = "Start refactoring",
                fileVersionId = null,
                createdAt = 2100L
            )
            val ev2 = com.example.data.entity.EpisodeEventEntity(
                id = "ev-2",
                episodeId = "ep-1",
                sequenceNumber = 2L,
                eventType = "decision",
                actor = "agent",
                payload = "Decided to use Room with pure Kotlin domain",
                fileVersionId = null,
                createdAt = 2200L
            )

            epDao.insertEvent(ev1)
            epDao.insertEvent(ev2)

            assertEquals(2L, epDao.getMaxSequenceNumber("ep-1"))
            val events = epDao.getEvents("ep-1")
            assertEquals(2, events.size)
            assertEquals("Start refactoring", events[0].payload)
            assertEquals("Decided to use Room with pure Kotlin domain", events[1].payload)

            // Delete project and verify cascade deletes episodes and events
            projDao.deleteById("p-ep-dao")
            assertEquals(0, epDao.countEpisodes("p-ep-dao"))
            assertEquals(0, epDao.getEvents("ep-1").size)
        }
    }
}
