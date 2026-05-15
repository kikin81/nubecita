package net.kikin.nubecita.core.database

import androidx.room.migration.Migration

/**
 * Manual `Migration` instances applied via
 * `Room.databaseBuilder(...).addMigrations(*MANUAL_MIGRATIONS)`. Prefer
 * `@AutoMigration(from = N, to = N+1)` entries on
 * [NubecitaDatabase]; reach for a manual `Migration` only when
 * AutoMigration cannot resolve a column rename or drop unambiguously.
 *
 * Empty at v1 — the database ships with no entities, so there is
 * nothing to migrate yet.
 */
internal val MANUAL_MIGRATIONS: Array<Migration> = emptyArray()
