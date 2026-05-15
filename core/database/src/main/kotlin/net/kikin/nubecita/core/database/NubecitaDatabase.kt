package net.kikin.nubecita.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.kikin.nubecita.core.database.util.InstantConverter

/**
 * The single Room database for Nubecita. Ships at version 1 with a
 * single placeholder entity ([BootstrapEntity]) — Room 2.8.x rejects
 * `entities = []` as a hard error, so the bootstrap table exists only
 * to let the schema-export pipeline run end-to-end before any feature
 * owns persistence. The first real entity (`RecentSearchEntity` from
 * the Search epic) will land as a migration to version 2 and drop
 * [BootstrapEntity] via `@DeleteTable`.
 *
 * Schema export is on; generated JSON is committed under
 * `core/database/schemas/`. Every schema bump must commit the new
 * `{N}.json` file and add the corresponding `@AutoMigration` entry to
 * the `autoMigrations` array below.
 */
@Database(
    entities = [BootstrapEntity::class],
    version = 1,
    exportSchema = true,
    autoMigrations = [],
)
@TypeConverters(InstantConverter::class)
abstract class NubecitaDatabase : RoomDatabase()
