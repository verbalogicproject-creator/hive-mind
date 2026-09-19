package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.ClientIdentityDao
import com.example.data.dao.EpisodeDao
import com.example.data.dao.FileVersionDao
import com.example.data.dao.ProjectDao
import com.example.data.entity.ClientCredentialEntity
import com.example.data.entity.ClientInstallationEntity
import com.example.data.entity.ClientLaneGrantEntity
import com.example.data.entity.ClientProjectGrantEntity
import com.example.data.entity.EpisodeEntity
import com.example.data.entity.EpisodeEventEntity
import com.example.data.entity.FileChunkEntity
import com.example.data.entity.FileVersionEntity
import com.example.data.entity.IdempotencyRecordEntity
import com.example.data.entity.MemoryLaneEntity
import com.example.data.entity.PairingInvitationEntity
import com.example.data.entity.ProjectEntity
import com.example.data.entity.SessionEntity
import com.example.data.entity.SessionInboxItemEntity

@Database(
    entities = [
        ProjectEntity::class,
        FileVersionEntity::class,
        FileChunkEntity::class,
        EpisodeEntity::class,
        EpisodeEventEntity::class,
        ClientInstallationEntity::class,
        ClientCredentialEntity::class,
        PairingInvitationEntity::class,
        MemoryLaneEntity::class,
        ClientProjectGrantEntity::class,
        ClientLaneGrantEntity::class,
        SessionEntity::class,
        SessionInboxItemEntity::class,
        IdempotencyRecordEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun fileVersionDao(): FileVersionDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun clientIdentityDao(): ClientIdentityDao

    companion object {
        const val DATABASE_NAME = "project_memory.db"
        private const val LOCAL_OWNER_ID = "local-device-owner"

        private val ENSURE_LOCAL_OWNER_CALLBACK = object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                val now = System.currentTimeMillis()
                db.beginTransaction()
                try {
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO `client_installations`
                            (`id`, `installation_name`, `created_at`, `revoked_at`)
                        VALUES (?, 'Local Device Owner', ?, NULL)
                        """.trimIndent(),
                        arrayOf<Any?>(LOCAL_OWNER_ID, now)
                    )
                    db.execSQL(
                        """
                        INSERT INTO `memory_lanes`
                            (`id`, `project_id`, `lane_slug`, `lane_kind`, `display_name`, `is_archived`, `created_at`)
                        SELECT 'lane-owner-' || lower(hex(randomblob(16))), p.`id`, 'local-owner',
                               'private', 'On-device owner memory', 0, ?
                        FROM `projects` p
                        WHERE NOT EXISTS (
                            SELECT 1 FROM `memory_lanes` l
                            WHERE l.`project_id` = p.`id`
                              AND l.`lane_kind` = 'private'
                              AND l.`lane_slug` LIKE 'local-owner%'
                              AND l.`is_archived` = 0
                        )
                        """.trimIndent(),
                        arrayOf<Any?>(now)
                    )
                    db.execSQL(
                        """
                        INSERT INTO `client_project_grants`
                            (`id`, `client_installation_id`, `project_id`, `scopes_csv`, `created_at`, `revoked_at`)
                        SELECT 'grant-owner-' || lower(hex(randomblob(16))), ?, p.`id`,
                               'project:discover,approved:read,inbox:append,session:start', ?, NULL
                        FROM `projects` p
                        WHERE NOT EXISTS (
                            SELECT 1 FROM `client_project_grants` g
                            WHERE g.`client_installation_id` = ?
                              AND g.`project_id` = p.`id`
                              AND g.`revoked_at` IS NULL
                        )
                        """.trimIndent(),
                        arrayOf<Any?>(LOCAL_OWNER_ID, now, LOCAL_OWNER_ID)
                    )
                    db.execSQL(
                        """
                        INSERT INTO `client_lane_grants`
                            (`id`, `client_installation_id`, `lane_id`, `can_read`, `can_write`, `created_at`, `revoked_at`)
                        SELECT 'lane-grant-owner-' || lower(hex(randomblob(16))), ?, l.`id`, 1, 1, ?, NULL
                        FROM `memory_lanes` l
                        WHERE l.`lane_kind` = 'private'
                          AND l.`lane_slug` LIKE 'local-owner%'
                          AND l.`is_archived` = 0
                          AND NOT EXISTS (
                              SELECT 1 FROM `client_lane_grants` g
                              WHERE g.`client_installation_id` = ?
                                AND g.`lane_id` = l.`id`
                                AND g.`revoked_at` IS NULL
                          )
                        """.trimIndent(),
                        arrayOf<Any?>(LOCAL_OWNER_ID, now, LOCAL_OWNER_ID)
                    )
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN description TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `file_versions` (
                        `id` TEXT NOT NULL,
                        `project_id` TEXT NOT NULL,
                        `path` TEXT NOT NULL,
                        `sha256` TEXT NOT NULL,
                        `size_bytes` INTEGER NOT NULL,
                        `mime_type` TEXT NOT NULL,
                        `version_number` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_file_versions_project_path` ON `file_versions` (`project_id`, `path`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_file_versions_sha256` ON `file_versions` (`sha256`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `file_chunks` (
                        `id` TEXT NOT NULL,
                        `file_version_id` TEXT NOT NULL,
                        `chunk_index` INTEGER NOT NULL,
                        `content` TEXT NOT NULL,
                        `token_count_estimate` INTEGER NOT NULL,
                        `sha256` TEXT NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`file_version_id`) REFERENCES `file_versions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_file_chunks_file_version_id` ON `file_chunks` (`file_version_id`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `episodes` (
                        `id` TEXT NOT NULL,
                        `project_id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `tags_csv` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_project_created_at` ON `episodes` (`project_id`, `created_at`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `episode_events` (
                        `id` TEXT NOT NULL,
                        `episode_id` TEXT NOT NULL,
                        `sequence_number` INTEGER NOT NULL,
                        `event_type` TEXT NOT NULL,
                        `actor` TEXT NOT NULL,
                        `payload` TEXT NOT NULL,
                        `file_version_id` TEXT,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`episode_id`) REFERENCES `episodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`file_version_id`) REFERENCES `file_versions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_episode_events_seq` ON `episode_events` (`episode_id`, `sequence_number`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_episode_events_file_version` ON `episode_events` (`file_version_id`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Client Installations
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `client_installations` (
                        `id` TEXT NOT NULL,
                        `installation_name` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `revoked_at` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_client_installations_revoked` ON `client_installations` (`revoked_at`)")

                // 2. Client Credentials
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `client_credentials` (
                        `id` TEXT NOT NULL,
                        `client_installation_id` TEXT NOT NULL,
                        `credential_type` TEXT NOT NULL,
                        `token_hash` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `expires_at` INTEGER,
                        `revoked_at` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`client_installation_id`) REFERENCES `client_installations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_client_credentials_token_hash` ON `client_credentials` (`token_hash`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_client_credentials_installation` ON `client_credentials` (`client_installation_id`)")

                // 3. Pairing Invitations
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pairing_invitations` (
                        `id` TEXT NOT NULL,
                        `token_hash` TEXT NOT NULL,
                        `target_project_id` TEXT NOT NULL,
                        `target_lane_kind` TEXT NOT NULL,
                        `intended_client_name` TEXT NOT NULL,
                        `scopes_csv` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `expires_at` INTEGER NOT NULL,
                        `consumed_at` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`target_project_id`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_pairing_invitations_token_hash` ON `pairing_invitations` (`token_hash`)")

                // 4. Memory Lanes
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `memory_lanes` (
                        `id` TEXT NOT NULL,
                        `project_id` TEXT NOT NULL,
                        `lane_slug` TEXT NOT NULL,
                        `lane_kind` TEXT NOT NULL,
                        `display_name` TEXT NOT NULL,
                        `is_archived` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_memory_lanes_project_slug` ON `memory_lanes` (`project_id`, `lane_slug`)")

                // 5. Client Project Grants
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `client_project_grants` (
                        `id` TEXT NOT NULL,
                        `client_installation_id` TEXT NOT NULL,
                        `project_id` TEXT NOT NULL,
                        `scopes_csv` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `revoked_at` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`client_installation_id`) REFERENCES `client_installations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_client_project_grants_lookup` ON `client_project_grants` (`client_installation_id`, `project_id`)")

                // 6. Client Lane Grants
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `client_lane_grants` (
                        `id` TEXT NOT NULL,
                        `client_installation_id` TEXT NOT NULL,
                        `lane_id` TEXT NOT NULL,
                        `can_read` INTEGER NOT NULL,
                        `can_write` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `revoked_at` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`client_installation_id`) REFERENCES `client_installations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`lane_id`) REFERENCES `memory_lanes`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_client_lane_grants_lookup` ON `client_lane_grants` (`client_installation_id`, `lane_id`)")

                // 7. Insert local device owner installation
                val now = System.currentTimeMillis()
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `client_installations` (`id`, `installation_name`, `created_at`, `revoked_at`)
                    VALUES ('local-device-owner', 'Local Device Owner', $now, NULL)
                    """.trimIndent()
                )

                // 8. Seed system_legacy lanes & owner grants for all existing projects
                val cursor = db.query("SELECT id FROM projects")
                val projectIds = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    projectIds.add(cursor.getString(0))
                }
                cursor.close()

                for (pId in projectIds) {
                    val legacyLaneId = "lane-legacy-$pId"
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO `memory_lanes` (`id`, `project_id`, `lane_slug`, `lane_kind`, `display_name`, `is_archived`, `created_at`)
                        VALUES ('$legacyLaneId', '$pId', 'legacy-import', 'system_legacy', 'System Legacy Import', 0, $now)
                        """.trimIndent()
                    )
                    // Grant owner full scopes on project
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO `client_project_grants` (`id`, `client_installation_id`, `project_id`, `scopes_csv`, `created_at`, `revoked_at`)
                        VALUES ('grant-owner-$pId', 'local-device-owner', '$pId', 'project:discover,approved:read,inbox:append,session:start', $now, NULL)
                        """.trimIndent()
                    )
                    // Grant owner read/write on legacy lane
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO `client_lane_grants` (`id`, `client_installation_id`, `lane_id`, `can_read`, `can_write`, `created_at`, `revoked_at`)
                        VALUES ('grant-lane-owner-$pId', 'local-device-owner', '$legacyLaneId', 1, 1, $now, NULL)
                        """.trimIndent()
                    )
                }

                // 9. Rebuild the complete v4 provenance graph before dropping any parent.
                //
                // `file_chunks` references `file_versions` with ON DELETE CASCADE and
                // `episode_events` references both `episodes` and `file_versions`. Rebuilding
                // only the two parent tables would therefore delete or detach v4 child rows.
                // Copy all four tables first, drop children before parents, then rename the
                // replacements so the upgrade is lossless with foreign_keys enabled.
                db.execSQL(
                    """
                    CREATE TABLE `file_versions_new` (
                        `id` TEXT NOT NULL,
                        `project_id` TEXT NOT NULL,
                        `lane_id` TEXT NOT NULL,
                        `path` TEXT NOT NULL,
                        `sha256` TEXT NOT NULL,
                        `size_bytes` INTEGER NOT NULL,
                        `mime_type` TEXT NOT NULL,
                        `version_number` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`lane_id`) REFERENCES `memory_lanes`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `file_versions_new` (`id`, `project_id`, `lane_id`, `path`, `sha256`, `size_bytes`, `mime_type`, `version_number`, `created_at`)
                    SELECT `id`, `project_id`, 'lane-legacy-' || `project_id`, `path`, `sha256`, `size_bytes`, `mime_type`, `version_number`, `created_at`
                    FROM `file_versions`
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE `episodes_new` (
                        `id` TEXT NOT NULL,
                        `project_id` TEXT NOT NULL,
                        `lane_id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `tags_csv` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`lane_id`) REFERENCES `memory_lanes`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `episodes_new` (`id`, `project_id`, `lane_id`, `title`, `summary`, `source`, `tags_csv`, `created_at`)
                    SELECT `id`, `project_id`, 'lane-legacy-' || `project_id`, `title`, `summary`, `source`, `tags_csv`, `created_at`
                    FROM `episodes`
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE `file_chunks_new` (
                        `id` TEXT NOT NULL,
                        `file_version_id` TEXT NOT NULL,
                        `chunk_index` INTEGER NOT NULL,
                        `content` TEXT NOT NULL,
                        `token_count_estimate` INTEGER NOT NULL,
                        `sha256` TEXT NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`file_version_id`) REFERENCES `file_versions_new`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `file_chunks_new` (`id`, `file_version_id`, `chunk_index`, `content`, `token_count_estimate`, `sha256`)
                    SELECT `id`, `file_version_id`, `chunk_index`, `content`, `token_count_estimate`, `sha256`
                    FROM `file_chunks`
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE `episode_events_new` (
                        `id` TEXT NOT NULL,
                        `episode_id` TEXT NOT NULL,
                        `sequence_number` INTEGER NOT NULL,
                        `event_type` TEXT NOT NULL,
                        `actor` TEXT NOT NULL,
                        `payload` TEXT NOT NULL,
                        `file_version_id` TEXT,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`episode_id`) REFERENCES `episodes_new`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`file_version_id`) REFERENCES `file_versions_new`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `episode_events_new` (`id`, `episode_id`, `sequence_number`, `event_type`, `actor`, `payload`, `file_version_id`, `created_at`)
                    SELECT `id`, `episode_id`, `sequence_number`, `event_type`, `actor`, `payload`, `file_version_id`, `created_at`
                    FROM `episode_events`
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE `episode_events`")
                db.execSQL("DROP TABLE `file_chunks`")
                db.execSQL("DROP TABLE `episodes`")
                db.execSQL("DROP TABLE `file_versions`")

                db.execSQL("ALTER TABLE `file_versions_new` RENAME TO `file_versions`")
                db.execSQL("ALTER TABLE `episodes_new` RENAME TO `episodes`")
                db.execSQL("ALTER TABLE `file_chunks_new` RENAME TO `file_chunks`")
                db.execSQL("ALTER TABLE `episode_events_new` RENAME TO `episode_events`")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_file_versions_project_path` ON `file_versions` (`project_id`, `path`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_file_versions_sha256` ON `file_versions` (`sha256`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_file_versions_lane_id` ON `file_versions` (`lane_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_file_chunks_file_version_id` ON `file_chunks` (`file_version_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_project_created_at` ON `episodes` (`project_id`, `created_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_lane_id` ON `episodes` (`lane_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_episode_events_seq` ON `episode_events` (`episode_id`, `sequence_number`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_episode_events_file_version` ON `episode_events` (`file_version_id`)")

                // 10. Sessions
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sessions` (
                        `id` TEXT NOT NULL,
                        `project_id` TEXT NOT NULL,
                        `client_installation_id` TEXT NOT NULL,
                        `lane_id` TEXT NOT NULL,
                        `reported_provider` TEXT,
                        `reported_model` TEXT,
                        `status` TEXT NOT NULL,
                        `started_at` INTEGER NOT NULL,
                        `closed_at` INTEGER,
                        `revision` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`client_installation_id`) REFERENCES `client_installations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`lane_id`) REFERENCES `memory_lanes`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_project_client` ON `sessions` (`project_id`, `client_installation_id`)")

                // 12. Session Inbox Items (Immutable, Version-Addressable, content_hash)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `session_inbox_items` (
                        `version_id` TEXT NOT NULL,
                        `item_id` TEXT NOT NULL,
                        `supersedes_version_id` TEXT,
                        `session_id` TEXT NOT NULL,
                        `sequence_number` INTEGER NOT NULL,
                        `item_type` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `payload_json` TEXT NOT NULL,
                        `source_file_version_id` TEXT,
                        `content_hash` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`version_id`),
                        FOREIGN KEY(`session_id`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`source_file_version_id`) REFERENCES `file_versions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`supersedes_version_id`) REFERENCES `session_inbox_items`(`version_id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_session_inbox_seq` ON `session_inbox_items` (`session_id`, `sequence_number`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_inbox_hash` ON `session_inbox_items` (`content_hash`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_inbox_item_id` ON `session_inbox_items` (`item_id`)")

                // 13. Idempotency Records
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `idempotency_records` (
                        `id` TEXT NOT NULL,
                        `client_installation_id` TEXT NOT NULL,
                        `project_id` TEXT NOT NULL,
                        `operation` TEXT NOT NULL,
                        `idempotency_key` TEXT NOT NULL,
                        `request_hash` TEXT NOT NULL,
                        `result_json` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`client_installation_id`) REFERENCES `client_installations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_idempotency_scope` ON `idempotency_records` (
                        `client_installation_id`, `project_id`, `operation`, `idempotency_key`
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .addCallback(ENSURE_LOCAL_OWNER_CALLBACK)
                .build()
        }
    }
}
