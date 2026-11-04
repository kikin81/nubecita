package net.kikin.nubecita.core.database

import androidx.room.DeleteTable
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Manual `Migration` instances applied via
 * `Room.databaseBuilder(...).addMigrations(*MANUAL_MIGRATIONS)`. Prefer
 * `@AutoMigration(from = N, to = N+1)` entries on
 * [NubecitaDatabase]; reach for a manual `Migration` only when
 * AutoMigration cannot resolve a column rename or drop unambiguously.
 */
internal val MANUAL_MIGRATIONS: Array<Migration> = arrayOf(Migration6To7)

/**
 * v6 → v7 splits the cached feed into two grains. `feed_post` becomes an entry
 * **envelope** — a position, a reference to the post it shows, and the
 * per-entry context (`reposted_by_did`, `reposted_by_label`, `reply_root_uri`,
 * `reply_parent_uri`) that belongs to the timeline rather than to the post —
 * and `cached_post` becomes the one store for post content, keyed by
 * `(account_did, uri)`. The post columns and the blob leave `feed_post`
 * entirely, the by-author index moves to `cached_post` where the author is, and
 * the read path joins the two.
 *
 * This is hand-written rather than an `@AutoMigration` for two reasons, and
 * the second is the one that matters:
 *
 * 1. AutoMigration cannot infer a column *rename* — it would read `post_blob`
 *    dropped and `entry_blob` added, and refuse the diff without a
 *    `@RenameColumn` spec.
 * 2. Carrying the old rows across would be **wrong even if it were possible**.
 *    Every existing `post_blob` holds a serialized `postView`, so the
 *    per-entry `reason` and `reply` fields the new shape exists to persist were
 *    never written to disk and cannot be recovered locally — there is no
 *    network in a migration. Renaming the column would leave rows that
 *    deserialize as a `feedViewPost` with no entry context: a cache that looks
 *    populated and silently cannot build a `ReplyCluster` or a chain.
 *
 * So the cached feed is discarded and allowed to refill. A cold partition is a
 * state the stack already handles — `FeedRemoteMediator.initialize()` returns
 * `LAUNCH_INITIAL_REFRESH` when the partition has no rows — and the cost is one
 * cold start after upgrade, which is what every launch costs today.
 *
 * v7 also adds the persisted grouping projection (`feed_item` plus its
 * `feed_item_member` rows) and three prepend cursors on `feed_remote_keys`,
 * and `cached_post`, the by-URI post store: one row per post per
 * account, which both the partition rows and the grouped projection reference
 * instead of carrying their own serialized copy. It is new, so there is nothing
 * to migrate into it — and nothing to migrate *from*, since the v6 blobs are
 * being discarded anyway.
 *
 * `feed_remote_keys` keeps its schema (unchanged at v7) but its **rows** go
 * too: a `next_cursor` describes a pagination position inside a partition that
 * no longer has any rows, so keeping it would point APPEND at a gap.
 *
 * Nothing outside the feed cache is touched — `recent_search`, `actors` and
 * `saved_feeds` are byte-identical between v6 and v7 and must survive.
 *
 * The `CREATE` statements are copied verbatim from the committed `7.json`
 * (`${TABLE_NAME}` resolved) so the migrated schema matches what Room
 * validates on open; drift here surfaces as an
 * `IllegalStateException: Migration didn't properly handle` at runtime.
 */
internal object Migration6To7 : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        // Dropping the table takes its indices with it, so both are recreated.
        connection.execSQL("DROP TABLE IF EXISTS `feed_post`")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `feed_post` (" +
                "`account_did` TEXT NOT NULL, `feed_type` TEXT NOT NULL, `feed_uri` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, `uri` TEXT NOT NULL, " +
                "`reposted_by_did` TEXT, `reposted_by_label` TEXT, " +
                "`reply_root_uri` TEXT, `reply_parent_uri` TEXT, " +
                "PRIMARY KEY(`account_did`, `feed_type`, `feed_uri`, `position`))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_feed_post_uri` ON `feed_post` (`uri`)")
        connection.execSQL("DELETE FROM `feed_remote_keys`")
        // The by-URI post store. New at v7, so there is nothing to carry over.
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `cached_post` (" +
                "`account_did` TEXT NOT NULL, `uri` TEXT NOT NULL, `cid` TEXT, `author_did` TEXT, " +
                "`indexed_at` INTEGER, `text` TEXT, `post_blob` TEXT, `unavailable` TEXT, " +
                "PRIMARY KEY(`account_did`, `uri`))",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cached_post_author_did` ON `cached_post` (`author_did`)",
        )
        // The persisted grouping projection and its membership rows. Both new
        // at v7, so there is nothing to carry over.
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `feed_item` (" +
                "`account_did` TEXT NOT NULL, `feed_type` TEXT NOT NULL, `feed_uri` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, `row_type` TEXT NOT NULL, `variant` TEXT, " +
                "`item_key` TEXT NOT NULL, `leaf_uri` TEXT, " +
                "`suppressed_reply_count` INTEGER NOT NULL, `thread_root_uri` TEXT, " +
                "`has_ellipsis` INTEGER, `seam_exhausted` INTEGER, " +
                "PRIMARY KEY(`account_did`, `feed_type`, `feed_uri`, `position`))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_feed_item_item_key` ON `feed_item` (`item_key`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_feed_item_leaf_uri` ON `feed_item` (`leaf_uri`)")
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_feed_item_thread_root_uri` ON `feed_item` (`thread_root_uri`)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `feed_item_member` (" +
                "`account_did` TEXT NOT NULL, `feed_type` TEXT NOT NULL, `feed_uri` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, `ordinal` INTEGER NOT NULL, `uri` TEXT NOT NULL, " +
                "PRIMARY KEY(`account_did`, `feed_type`, `feed_uri`, `position`, `ordinal`))",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_feed_item_member_uri` ON `feed_item_member` (`uri`)",
        )
        // The prepend cursors. ALTER rather than a recreate: the table keeps its
        // shape and its rows were already emptied above, so there is nothing to
        // preserve and nothing to rewrite.
        // Per-partition reader state. New at v7, and the future home of the
        // scroll anchor as well as this fingerprint.
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `feed_partition_state` (" +
                "`account_did` TEXT NOT NULL, `feed_type` TEXT NOT NULL, `feed_uri` TEXT NOT NULL, " +
                "`prefs_fingerprint` TEXT, " +
                "PRIMARY KEY(`account_did`, `feed_type`, `feed_uri`))",
        )
        connection.execSQL("ALTER TABLE `feed_remote_keys` ADD COLUMN `boundary_since` TEXT")
        connection.execSQL("ALTER TABLE `feed_remote_keys` ADD COLUMN `fill_cursor` TEXT")
        connection.execSQL("ALTER TABLE `feed_remote_keys` ADD COLUMN `pending_boundary` TEXT")
    }
}

/**
 * `AutoMigrationSpec` for the v1 → v2 jump: drops the `bootstrap`
 * placeholder table introduced in the Room foundation epic
 * (nubecita-50zx) without touching the new `recent_search` table.
 * The empty class is intentional — `@DeleteTable` is the entire
 * migration. Registered on [NubecitaDatabase]'s `autoMigrations` array.
 */
@DeleteTable(tableName = "bootstrap")
internal class BootstrapEntityDrop : AutoMigrationSpec
