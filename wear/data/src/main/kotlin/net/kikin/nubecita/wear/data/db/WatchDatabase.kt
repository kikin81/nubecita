package net.kikin.nubecita.wear.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The watch's offline store. Everything in it is a copy of what the phone
 * synced, so a destructive migration is acceptable: the next sync refills it.
 */
@Database(
    entities = [
        WatchFeedEntity::class,
        WatchPostEntity::class,
        WatchPendingActionEntity::class,
        WatchDmStateEntity::class,
        WatchConvoEntity::class,
        WatchMessageEntity::class,
        WatchPendingDmEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class WatchDatabase : RoomDatabase() {
    abstract fun feedDao(): WatchFeedDao

    abstract fun actionDao(): WatchActionDao

    abstract fun dmDao(): WatchDmDao

    abstract fun dmActionDao(): WatchDmActionDao
}
