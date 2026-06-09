package net.kikin.nubecita.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates the v4 → v5 `@AutoMigration` that adds the offline feed cache
 * (`feed_post` + `feed_remote_keys`): the new tables are created and the
 * existing v4 rows (recent searches, actors) survive untouched.
 *
 * Reads the committed schema JSON from `core/database/schemas/` (added as
 * androidTest assets by the module's `build.gradle.kts`).
 */
@RunWith(AndroidJUnit4::class)
internal class MigrationTest {
    private val dbName = "migration-test-db"

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            NubecitaDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrate4To5_createsFeedTables_andPreservesExistingData() {
        // Create the v4 database and seed one row in each existing table.
        helper.createDatabase(dbName, 4).use { db ->
            db.execSQL(
                "INSERT INTO recent_search (`query`, recorded_at) VALUES ('kotlin', 1000)",
            )
            db.execSQL(
                "INSERT INTO actors (did, handle, display_name, avatar_url, last_seen_at, can_message) " +
                    "VALUES ('did:a', 'alice.bsky.social', 'Alice', NULL, 2000, 1)",
            )
        }

        // Run the auto-migration to v5 (validates the migrated schema against 5.json).
        val db =
            helper.runMigrationsAndValidate(
                dbName,
                5,
                true,
            )

        // Existing v4 rows survived.
        db.query("SELECT `query` FROM recent_search").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("kotlin", c.getString(0))
        }
        db.query("SELECT did, handle FROM actors").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("did:a", c.getString(0))
            assertEquals("alice.bsky.social", c.getString(1))
        }

        // New v5 tables exist and are usable.
        db.execSQL(
            "INSERT INTO feed_post " +
                "(account_did, feed_type, feed_uri, position, uri, cid, author_did, indexed_at, text, post_blob) " +
                "VALUES ('did:a', 'FOLLOWING', '', 0, 'at://post/1', 'cid1', 'did:b', 3000, 'hello', NULL)",
        )
        db.execSQL(
            "INSERT INTO feed_remote_keys (account_did, feed_type, feed_uri, next_cursor) " +
                "VALUES ('did:a', 'FOLLOWING', '', 'cursor-1')",
        )
        db.query("SELECT COUNT(*) FROM feed_post").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        db.query("SELECT next_cursor FROM feed_remote_keys").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("cursor-1", c.getString(0))
        }
        db.close()
    }
}
