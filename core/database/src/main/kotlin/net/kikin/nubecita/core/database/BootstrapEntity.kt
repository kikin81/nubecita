package net.kikin.nubecita.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Placeholder entity required to ship [NubecitaDatabase] at version 1.
 * Room's KSP processor rejects `@Database(entities = [])` as a hard
 * error, so a single no-op table lets the schema export pipeline run
 * end-to-end (verifying the convention plugin, KSP wiring, and schema
 * JSON generation are correct) before any feature owns persistence.
 *
 * Dropped in the version 2 migration that lands the first real
 * `@Entity` (`RecentSearchEntity` from the Search epic). The drop is
 * an `@AutoMigration` step with a `@DeleteTable` `AutoMigrationSpec`;
 * the entity has no rows in production so no data is lost.
 *
 * Not exposed via any DAO. No `asExternalModel()` extension — this is
 * not a domain entity.
 */
@Entity(tableName = "bootstrap")
internal data class BootstrapEntity(
    @PrimaryKey val id: Int = 0,
)
