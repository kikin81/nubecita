package net.kikin.nubecita.core.database

import androidx.room.migration.Migration

/**
 * Manual `Migration` instances applied via
 * `Room.databaseBuilder(...).addMigrations(*MANUAL_MIGRATIONS)`. Prefer
 * `@AutoMigration(from = N, to = N+1)` entries on
 * [NubecitaDatabase]; reach for a manual `Migration` only when
 * AutoMigration cannot resolve a column rename or drop unambiguously.
 *
 * Empty at v1 — the database ships with only the [BootstrapEntity]
 * placeholder, so there is nothing worth migrating yet. The v1 → v2
 * jump (when the Search epic's first real entity lands) will drop
 * [BootstrapEntity] and add `RecentSearchEntity` via `@AutoMigration`
 * entries on [NubecitaDatabase] (no manual `Migration` instances
 * required).
 */
internal val MANUAL_MIGRATIONS: Array<Migration> = emptyArray()
