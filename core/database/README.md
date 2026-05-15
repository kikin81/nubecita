# `:core:database`

The single Room database for Nubecita. Hosts the `RoomDatabase` class, all
`@Entity` types, all DAOs, TypeConverters, and the Hilt modules that provide
them. Repositories that consume DAOs live in per-domain `:core:<domain>`
modules — never inside `:core:database`, and never inside feature `:impl`
modules.

## Conventions

- Reads return `Flow<T>`; writes are `suspend fun`. Multi-statement writes
  use `@Transaction suspend fun`.
- Entities are not exposed past the repository layer. Each entity ships
  a same-file top-level extension `fun FooEntity.asExternalModel(): Foo`
  returning a `:data:models` type; per-domain `:core:<domain>` repositories
  import the entity to call the mapper but expose only mapped flows to
  ViewModels / UI. Feature modules never depend on `:core:database` directly.
- Schema export is on (`exportSchema = true`). Generated JSON is committed
  under `schemas/`. Every schema bump must commit the new `{N}.json` and
  either add an `@AutoMigration` entry on `@Database` or register a manual
  `Migration` in `Migrations.kt` (see the next bullet for when to use which).
- Prefer `@AutoMigration` (with `AutoMigrationSpec` for ambiguous renames).
  Hand-write a `Migration` only when AutoMigration cannot resolve the diff.

## Out of the box

This module ships with a single placeholder entity (`BootstrapEntity`)
so the database wires up cleanly before any feature owns persistence.
Room 2.8.x rejects `@Database(entities = [])` as a hard error; the
bootstrap table is the smallest legal shape. The first real entity
(`RecentSearchEntity` from the Search epic) lands at version 2 and
drops `BootstrapEntity` via an `@AutoMigration` + `@DeleteTable` spec.
