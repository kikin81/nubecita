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
 * Validates each schema bump against its committed JSON, with real rows in the
 * old database: a migration that drops or fails to create something is a crash
 * on upgrade for every installed copy, and nothing else in the suite runs the
 * upgrade path at all.
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

        // The new envelope shape is usable: the per-entry columns accept writes,
        // the post columns are gone, and the recreated index is in place.
        db.execSQL(
            "INSERT INTO feed_post " +
                "(account_did, feed_type, feed_uri, position, uri, " +
                "reposted_by_did, reposted_by_label, reply_root_uri, reply_parent_uri) " +
                "VALUES ('did:c', 'FOLLOWING', '', -1, 'at://post/new', " +
                "'did:reposter', 'Reposter', 'at://post/root', 'at://post/parent')",
        )
        db
            .query(
                "SELECT reposted_by_did, reposted_by_label, reply_root_uri, reply_parent_uri, position FROM feed_post",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("did:reposter", c.getString(0))
                assertEquals("Reposter", c.getString(1))
                assertEquals("at://post/root", c.getString(2))
                assertEquals("at://post/parent", c.getString(3))
                // A prepend writes above existing rows, so negatives must persist.
                assertEquals(-1, c.getInt(4))
            }
        // v7 also adds the by-URI post store, which both the partition rows and
        // the grouped projection reference instead of carrying their own copy.
        db.execSQL(
            "INSERT INTO cached_post (account_did, uri, cid, author_did, indexed_at, text, post_blob, unavailable) " +
                "VALUES ('did:c', 'at://post/new', 'cidnew', 'did:e', 7300, 'fresh', '{}', NULL)",
        )
        db.execSQL(
            "INSERT INTO cached_post (account_did, uri, cid, author_did, indexed_at, text, post_blob, unavailable) " +
                "VALUES ('did:c', 'at://post/gone', NULL, NULL, NULL, NULL, NULL, 'blocked')",
        )
        db.query("SELECT uri, unavailable FROM cached_post ORDER BY uri").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("at://post/gone", c.getString(0))
            assertEquals("blocked", c.getString(1))
            assertTrue(c.moveToNext())
            assertEquals("at://post/new", c.getString(0))
            assertEquals(null, c.getString(1))
        }
        db.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND tbl_name = 'cached_post'").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getInt(0) >= 1)
        }

        // v7 also adds the persisted projection, its membership rows, and the
        // three prepend cursors.
        db.execSQL(
            "INSERT INTO feed_item " +
                "(account_did, feed_type, feed_uri, position, row_type, variant, item_key, leaf_uri, " +
                "suppressed_reply_count, seam_exhausted) " +
                "VALUES ('did:c', 'FOLLOWING', '', -2, 'item', 'cluster', 'cluster:at://post/root', " +
                "'at://post/leaf', 3, NULL)",
        )
        db.execSQL(
            "INSERT INTO feed_item " +
                "(account_did, feed_type, feed_uri, position, row_type, variant, item_key, leaf_uri, " +
                "suppressed_reply_count, seam_exhausted) " +
                "VALUES ('did:c', 'FOLLOWING', '', -1, 'seam', NULL, 'seam:-1', NULL, 0, 0)",
        )
        db.execSQL(
            "INSERT INTO feed_item_member (account_did, feed_type, feed_uri, position, ordinal, uri) " +
                "VALUES ('did:c', 'FOLLOWING', '', -2, 0, 'at://post/root')",
        )
        db
            .query("SELECT position, row_type, item_key, suppressed_reply_count, seam_exhausted FROM feed_item ORDER BY position")
            .use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(-2, c.getInt(0))
                assertEquals("item", c.getString(1))
                assertEquals(3, c.getInt(3))
                assertTrue(c.moveToNext())
                // The seam is a row, not an inferred gap.
                assertEquals("seam", c.getString(1))
                assertEquals(0, c.getInt(4))
            }
        db.query("SELECT ordinal, uri FROM feed_item_member").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("at://post/root", c.getString(1))
        }
        db.execSQL(
            "INSERT INTO feed_remote_keys " +
                "(account_did, feed_type, feed_uri, next_cursor, boundary_since, fill_cursor, pending_boundary) " +
                "VALUES ('did:c', 'FOLLOWING', '', 'append', 'boundary', 'fill', 'pending')",
        )
        db
            .query("SELECT next_cursor, boundary_since, fill_cursor, pending_boundary FROM feed_remote_keys")
            .use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("append", c.getString(0))
                assertEquals("boundary", c.getString(1))
                assertEquals("fill", c.getString(2))
                assertEquals("pending", c.getString(3))
            }

        db.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND tbl_name = 'feed_post'").use { c ->
            assertTrue(c.moveToFirst())
            // Both named indices were recreated after the table was dropped.
            assertTrue(c.getInt(0) >= 2)
        }

        db.close()
    }

    @Test
    fun migrate7To8_addsTheScrollAnchor_andPreservesTheCache() {
        // The anchor is three nullable columns on a table that already exists,
        // so AutoMigration expresses it — but a reader's cached partition must
        // come through untouched, since the whole point of the anchor is to
        // return them to rows that are still there.
        helper.createDatabase(dbName, 7).use { db ->
            db.execSQL("INSERT INTO recent_search (`query`, recorded_at) VALUES ('nubecita', 8000)")
            db.execSQL(
                "INSERT INTO cached_post (account_did, uri, cid, author_did, indexed_at, text, post_blob, unavailable) " +
                    "VALUES ('did:d', 'at://post/kept', 'cidk', 'did:e', 8100, 'kept', '{}', NULL)",
            )
            db.execSQL(
                "INSERT INTO feed_post (account_did, feed_type, feed_uri, position, uri) " +
                    "VALUES ('did:d', 'FOLLOWING', '', 0, 'at://post/kept')",
            )
            db.execSQL(
                "INSERT INTO feed_item " +
                    "(account_did, feed_type, feed_uri, position, row_type, variant, item_key, leaf_uri, " +
                    "suppressed_reply_count, seam_exhausted) " +
                    "VALUES ('did:d', 'FOLLOWING', '', 0, 'item', 'single', 'single:at://post/kept', " +
                    "'at://post/kept', 0, NULL)",
            )
            db.execSQL(
                "INSERT INTO feed_partition_state (account_did, feed_type, feed_uri, prefs_fingerprint) " +
                    "VALUES ('did:d', 'FOLLOWING', '', 'replies=false')",
            )
        }

        // Validates the migrated schema against 8.json.
        val db = helper.runMigrationsAndValidate(dbName, 8, true)

        // The cache and the fingerprint survive.
        db.query("SELECT COUNT(*) FROM feed_post").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        db.query("SELECT item_key FROM feed_item").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("single:at://post/kept", c.getString(0))
        }
        // An existing row reads as "never scrolled" rather than as a broken
        // anchor, which is what makes the columns nullable rather than defaulted.
        db
            .query("SELECT prefs_fingerprint, anchor_item_key, anchor_position, anchor_offset FROM feed_partition_state")
            .use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("replies=false", c.getString(0))
                assertTrue(c.isNull(1))
                assertTrue(c.isNull(2))
                assertTrue(c.isNull(3))
            }

        // And the new columns accept the reader's place, negative offset included.
        db.execSQL(
            "UPDATE feed_partition_state SET anchor_item_key = 'single:at://post/kept', " +
                "anchor_position = 0, anchor_offset = -24 " +
                "WHERE account_did = 'did:d' AND feed_type = 'FOLLOWING' AND feed_uri = ''",
        )
        db.query("SELECT anchor_item_key, anchor_position, anchor_offset FROM feed_partition_state").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("single:at://post/kept", c.getString(0))
            assertEquals(0, c.getInt(1))
            assertEquals(-24, c.getInt(2))
        }

        db.close()
    }

    @Test
    fun migrate8To9_addsThePillDismissal_andKeepsTheAnchor() {
        // One more nullable column on the same row. The anchor written under
        // v8 has to come through untouched: the pill's waterline falls back to
        // it, so losing it would both lose the reader's place and make every
        // partition look like it had new posts.
        helper.createDatabase(dbName, 8).use { db ->
            db.execSQL(
                "INSERT INTO feed_partition_state " +
                    "(account_did, feed_type, feed_uri, prefs_fingerprint, anchor_item_key, anchor_position, anchor_offset) " +
                    "VALUES ('did:plc:e', 'FOLLOWING', '', 'replies=false', 'single:at://post/k', 12, -8)",
            )
        }

        val db = helper.runMigrationsAndValidate(dbName, 9, true)

        db
            .query(
                "SELECT anchor_item_key, anchor_position, anchor_offset, pill_dismissed_position FROM feed_partition_state",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("single:at://post/k", c.getString(0))
                assertEquals(12, c.getInt(1))
                assertEquals(-8, c.getInt(2))
                // Never dismissed reads as null, not as 0 — which would mean
                // "acknowledged everything above the top of the feed".
                assertTrue(c.isNull(3))
            }

        db.execSQL(
            "UPDATE feed_partition_state SET pill_dismissed_position = -250 " +
                "WHERE account_did = 'did:plc:e'",
        )
        db.query("SELECT pill_dismissed_position FROM feed_partition_state").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(-250, c.getInt(0))
        }

        db.close()
    }
}
