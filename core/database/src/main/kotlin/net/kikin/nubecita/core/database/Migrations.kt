package net.kikin.nubecita.core.database

import androidx.room.DeleteTable
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration

/**
 * Manual `Migration` instances applied via
 * `Room.databaseBuilder(...).addMigrations(*MANUAL_MIGRATIONS)`. Prefer
 * `@AutoMigration(from = N, to = N+1)` entries on
 * [NubecitaDatabase]; reach for a manual `Migration` only when
 * AutoMigration cannot resolve a column rename or drop unambiguously.
 *
 * Empty through v2 — the v1 → v2 jump (drop the foundation-epic
 * placeholder `bootstrap` table, add `recent_search`) is fully
 * declarative via [BootstrapEntityDrop].
 */
internal val MANUAL_MIGRATIONS: Array<Migration> = emptyArray()

/**
 * `AutoMigrationSpec` for the v1 → v2 jump: drops the `bootstrap`
 * placeholder table introduced in the Room foundation epic
 * (nubecita-50zx) without touching the new `recent_search` table.
 * The empty class is intentional — `@DeleteTable` is the entire
 * migration. Registered on [NubecitaDatabase]'s `autoMigrations` array.
 */
@DeleteTable(tableName = "bootstrap")
internal class BootstrapEntityDrop : AutoMigrationSpec
