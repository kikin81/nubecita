package net.kikin.nubecita.wear.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The watch's offline store. Everything in it is a copy of what the phone
 * synced, so a destructive migration is acceptable: the next sync refills it.
 */
@Database(
    entities = [WatchFeedEntity::class, WatchPostEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class WatchDatabase : RoomDatabase() {
    abstract fun feedDao(): WatchFeedDao
}
