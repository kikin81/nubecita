package net.kikin.nubecita.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.kikin.nubecita.core.database.dao.ActorDao
import net.kikin.nubecita.core.database.dao.CachedPostDao
import net.kikin.nubecita.core.database.dao.FeedItemDao
import net.kikin.nubecita.core.database.dao.FeedPartitionStateDao
import net.kikin.nubecita.core.database.dao.FeedPostDao
import net.kikin.nubecita.core.database.dao.FeedRemoteKeyDao
import net.kikin.nubecita.core.database.dao.RecentSearchDao
import net.kikin.nubecita.core.database.dao.SavedFeedDao
import net.kikin.nubecita.core.database.model.ActorEntity
import net.kikin.nubecita.core.database.model.CachedPostEntity
import net.kikin.nubecita.core.database.model.FeedItemEntity
import net.kikin.nubecita.core.database.model.FeedItemMemberEntity
import net.kikin.nubecita.core.database.model.FeedPartitionStateEntity
import net.kikin.nubecita.core.database.model.FeedPostEntity
import net.kikin.nubecita.core.database.model.FeedRemoteKeyEntity
import net.kikin.nubecita.core.database.model.RecentSearchEntity
import net.kikin.nubecita.core.database.model.SavedFeedEntity
import net.kikin.nubecita.core.database.util.InstantConverter

/**
 * The single Room database for Nubecita. v2 introduces the first real
 * entity ([RecentSearchEntity]) and drops the v1 `BootstrapEntity`
 * placeholder via the [BootstrapEntityDrop] `AutoMigrationSpec`. v5 adds the
 * offline feed cache ([FeedPostEntity] + [FeedRemoteKeyEntity]) — two new
 * tables, additive, via `@AutoMigration(4 → 5)`. v7 widens the cached unit
 * from a bare `postView` to a whole feed **entry** (`feedViewPost`, carrying
 * the per-entry repost and reply context) and renames `post_blob` to
 * `entry_blob`. That diff is not additive and the dropped `reason`/`reply`
 * data cannot be recovered locally, so v6 → v7 is a **manual** migration that
 * drops the cached feed rows and lets the partition refill — a cold partition
 * is a state [FeedPostDao] and the `RemoteMediator` already handle. v8 adds the
 * reader's scroll anchor to [FeedPartitionStateEntity] — three nullable
 * columns, so `@AutoMigration(7 → 8)` expresses it, and a partition with no
 * anchor yet reads exactly like one the reader has never scrolled.
 *
 * Schema export is on; generated JSON is committed under
 * `core/database/schemas/`. Every schema bump must commit the new
 * `{N}.json` file and either add an `@AutoMigration` entry to the
 * `autoMigrations` array below or register a manual `Migration` in
 * [MANUAL_MIGRATIONS] when AutoMigration cannot express the diff.
 */
@Database(
    entities = [
        RecentSearchEntity::class,
        ActorEntity::class,
        FeedPostEntity::class,
        CachedPostEntity::class,
        FeedItemEntity::class,
        FeedItemMemberEntity::class,
        FeedPartitionStateEntity::class,
        FeedRemoteKeyEntity::class,
        SavedFeedEntity::class,
    ],
    version = 9,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = BootstrapEntityDrop::class),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
    ],
)
@TypeConverters(InstantConverter::class)
abstract class NubecitaDatabase : RoomDatabase() {
    abstract fun recentSearchDao(): RecentSearchDao

    abstract fun actorDao(): ActorDao

    abstract fun feedPostDao(): FeedPostDao

    abstract fun cachedPostDao(): CachedPostDao

    abstract fun feedItemDao(): FeedItemDao

    abstract fun feedPartitionStateDao(): FeedPartitionStateDao

    abstract fun feedRemoteKeyDao(): FeedRemoteKeyDao

    abstract fun savedFeedDao(): SavedFeedDao
}
