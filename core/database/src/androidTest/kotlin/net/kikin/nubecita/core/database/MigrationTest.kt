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

    @Test
    fun migrate5To6_createsSavedFeedsTable_andPreservesExistingData() {
        // Create the v5 database and seed one row in each existing table.
        helper.createDatabase(dbName, 5).use { db ->
            db.execSQL(
                "INSERT INTO recent_search (`query`, recorded_at) VALUES ('bluesky', 9000)",
            )
            db.execSQL(
                "INSERT INTO actors (did, handle, display_name, avatar_url, last_seen_at, can_message) " +
                    "VALUES ('did:b', 'bob.bsky.social', 'Bob', NULL, 5000, 0)",
            )
            db.execSQL(
                "INSERT INTO feed_post " +
                    "(account_did, feed_type, feed_uri, position, uri, cid, author_did, indexed_at, text, post_blob) " +
                    "VALUES ('did:b', 'FOLLOWING', '', 0, 'at://post/x', 'cidx', 'did:c', 6000, 'hi', NULL)",
            )
        }

        // Run the auto-migration to v6 (validates the migrated schema against 6.json).
        val db =
            helper.runMigrationsAndValidate(
                dbName,
                6,
                true,
            )

        // Existing v5 rows survived.
        db.query("SELECT `query` FROM recent_search").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("bluesky", c.getString(0))
        }
        db.query("SELECT did FROM actors").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("did:b", c.getString(0))
        }
        db.query("SELECT COUNT(*) FROM feed_post").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }

        // New v6 table exists and is usable.
        db.execSQL(
            "INSERT INTO saved_feeds (uri, display_name, creator_handle, avatar_url, pinned, position) " +
                "VALUES ('at://feed/discover', 'Discover', NULL, NULL, 1, 0)",
        )
        db.query("SELECT display_name, pinned FROM saved_feeds").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Discover", c.getString(0))
            assertEquals(1, c.getInt(1))
        }

        db.close()
    }

    @Test
    fun migrate6To7_dropsCachedFeed_andPreservesEverythingElse() {
        // Seed v6 with a row in every table, including the feed cache that this
        // migration deliberately discards.
        helper.createDatabase(dbName, 6).use { db ->
            db.execSQL("INSERT INTO recent_search (`query`, recorded_at) VALUES ('atproto', 7000)")
            db.execSQL(
                "INSERT INTO actors (did, handle, display_name, avatar_url, last_seen_at, can_message) " +
                    "VALUES ('did:c', 'carol.bsky.social', 'Carol', NULL, 7100, 1)",
            )
            db.execSQL(
                "INSERT INTO saved_feeds (uri, display_name, creator_handle, avatar_url, pinned, position) " +
                    "VALUES ('at://feed/following', 'Following', NULL, NULL, 1, 0)",
            )
            db.execSQL(
                "INSERT INTO feed_post " +
                    "(account_did, feed_type, feed_uri, position, uri, cid, author_did, indexed_at, text, post_blob) " +
                    "VALUES ('did:c', 'FOLLOWING', '', 0, 'at://post/old', 'cidold', 'did:d', 7200, 'stale', '{}')",
            )
            db.execSQL(
                "INSERT INTO feed_remote_keys (account_did, feed_type, feed_uri, next_cursor) " +
                    "VALUES ('did:c', 'FOLLOWING', '', 'cursor-stale')",
            )
        }

        // Validates the migrated schema against 7.json.
        val db = helper.runMigrationsAndValidate(dbName, 7, true, Migration6To7)

        // Non-feed data is untouched.
        db.query("SELECT `query` FROM recent_search").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("atproto", c.getString(0))
        }
        db.query("SELECT did FROM actors").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("did:c", c.getString(0))
        }
        db.query("SELECT display_name FROM saved_feeds").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Following", c.getString(0))
        }

        // The cached feed is gone: the old blobs hold a bare postView whose
        // per-entry reason/reply were never persisted and cannot be recovered
        // locally, so carrying them over would leave a cache that looks
        // populated and silently cannot build a cluster or a chain.
        db.query("SELECT COUNT(*) FROM feed_post").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }

        // The stale cursor goes with it — it points into a partition that no
        // longer has rows, so APPEND would resume from a gap.
        db.query("SELECT COUNT(*) FROM feed_remote_keys").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }

        // The widened shape is usable: the three entry columns and the renamed
        // blob all accept writes, and the recreated indices are in place.
        db.execSQL(
            "INSERT INTO feed_post " +
                "(account_did, feed_type, feed_uri, position, uri, cid, author_did, indexed_at, text, " +
                "reposted_by_did, reply_root_uri, reply_parent_uri, entry_blob) " +
                "VALUES ('did:c', 'FOLLOWING', '', -1, 'at://post/new', 'cidnew', 'did:e', 7300, 'fresh', " +
                "'did:reposter', 'at://post/root', 'at://post/parent', '{\"post\":{}}')",
        )
        db
            .query(
                "SELECT reposted_by_did, reply_root_uri, reply_parent_uri, entry_blob, position FROM feed_post",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("did:reposter", c.getString(0))
                assertEquals("at://post/root", c.getString(1))
                assertEquals("at://post/parent", c.getString(2))
                assertEquals("{\"post\":{}}", c.getString(3))
                // A prepend writes above existing rows, so negatives must persist.
                assertEquals(-1, c.getInt(4))
            }
        db.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND tbl_name = 'feed_post'").use { c ->
            assertTrue(c.moveToFirst())
            // Both named indices were recreated after the table was dropped.
            assertTrue(c.getInt(0) >= 2)
        }

        db.close()
    }
}
