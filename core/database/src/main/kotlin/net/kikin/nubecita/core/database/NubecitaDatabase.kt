package net.kikin.nubecita.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.kikin.nubecita.core.database.dao.RecentSearchDao
import net.kikin.nubecita.core.database.model.RecentSearchEntity
import net.kikin.nubecita.core.database.util.InstantConverter

/**
 * The single Room database for Nubecita. v2 introduces the first real
 * entity ([RecentSearchEntity]) and drops the v1 `BootstrapEntity`
 * placeholder via the [BootstrapEntityDrop] `AutoMigrationSpec`.
 *
 * Schema export is on; generated JSON is committed under
 * `core/database/schemas/`. Every schema bump must commit the new
 * `{N}.json` file and either add an `@AutoMigration` entry to the
 * `autoMigrations` array below or register a manual `Migration` in
 * [MANUAL_MIGRATIONS] when AutoMigration cannot express the diff.
 */
@Database(
    entities = [RecentSearchEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = BootstrapEntityDrop::class),
    ],
)
@TypeConverters(InstantConverter::class)
abstract class NubecitaDatabase : RoomDatabase() {
    abstract fun recentSearchDao(): RecentSearchDao
}
