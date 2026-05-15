# Room database foundation — design

**Date:** 2026-05-15
**Scope:** A standalone foundation epic that introduces Room into Nubecita's multi-module graph so feature epics (starting with the Search epic `nubecita-vrba` and later notification cache, draft persistence, profile cache, etc.) can persist data through a single shared database.
**Status:** Draft for user review.

## Why a foundation epic

The Search epic (`nubecita-vrba`) will be the first feature to need persistence (recent searches). Wiring Room ad-hoc inside `:feature:search:impl` would lock us into a per-feature database pattern, which Google's *Now in Android* (Nia) reference architecture and the official Android Architecture guide both warn against: multiple databases fragment migrations, prevent cross-domain relational projections, and inflate process memory.

A single `:core:database` module — assembled once, consumed by per-domain repository modules — keeps schema and migrations cohesive while letting feature work proceed independently.

## Non-goals

- **No domain entities or DAOs in this foundation.** `NubecitaDatabase` ships at `version = 1` with `entities = []`. The first real entity (`RecentSearchEntity`) lands as part of `nubecita-vrba.2`.
- **No `:core:data` umbrella module.** Nubecita already uses per-domain core modules (`:core:auth`, `:core:posts`, `:core:posting`, …); the Room foundation matches that convention.
- **No remote sync.** AT Protocol has no "recent searches" record; everything in `NubecitaDatabase` is local-only until and unless a future epic introduces a sync layer.
- **No `:core:database:api`/`:impl` split.** YAGNI — we don't need to swap storage engines.

## Decisions

### D1. One database, all entities in `:core:database`

Room's `@Database(entities = [...])` requires every entity to be visible at the database class declaration. Splitting entities across `:core:<domain>` modules would force `:core:database` to depend on every domain module, creating cycles (since `:core:<domain>` will depend on `:core:database` for the DAO). Mirroring Nia, **all `@Entity` classes and DAOs live in `:core:database`.** Repositories that consume those DAOs live in per-domain `:core:<domain>` modules.

### D2. Modern `androidx.room` Gradle plugin

We adopt the Room Gradle plugin (2.6.0+) introduced in 2024 — not the legacy KSP `room.schemaLocation` argument. The plugin configures the schema directory as a build input, plays nicely with KSP2, and is what current Android docs and Nia recommend. Schema export is `true`.

### D3. Mapping via `asExternalModel()` extensions

Entities never leak past `:core:database`. Each entity declares a top-level `asExternalModel()` extension in the same file that returns the matching `:data:models` domain type. Repositories in `:core:<domain>` consume DAOs and `.map(Entity::asExternalModel)` before exposing flows to ViewModels.

Rationale: same-file extension is the Nia convention, keeps the mapping trivially discoverable, and avoids a `Mapper` class layer that would otherwise need Hilt wiring.

### D4. DAO contract

- Reads return `Flow<T>` (or `Flow<List<T>>`) — never blocking or `LiveData`.
- Writes are `suspend fun`. Multi-statement writes are `@Transaction suspend fun`.
- Room 3.0 enforces this; we adopt it from day one.

### D5. No `fallbackToDestructiveMigration`

We want migration failures to surface as build/test failures, not silent data wipes. Every schema bump ships a corresponding `AutoMigration` (or manual `Migration` when an ambiguous rename/drop forces it).

### D6. AutoMigration preferred; manual Migration only when needed

Default to `@AutoMigration(from = N, to = N+1)`. Reach for `AutoMigrationSpec` (`@RenameColumn`, `@DeleteColumn`, …) for ambiguous changes. Hand-write a `Migration` only when AutoMigration cannot resolve the diff.

### D7. KSP isolation to `:core:database`

The Room KSP processor is applied only by the `nubecita.android.room` convention plugin, which only `:core:database` consumes. Other modules pay no Room KSP build-time cost. Hilt's separate KSP processor continues to apply per-module via `nubecita.android.hilt`. `pluginManager.apply("com.google.devtools.ksp")` is idempotent, so `:core:database` applying both `nubecita.android.hilt` and `nubecita.android.room` is safe — Gradle resolves the second `apply` as a no-op.

### D8. Hilt provides the database and DAOs

`:core:database/di/DatabaseModule.kt` provides the singleton `NubecitaDatabase`. `:core:database/di/DaosModule.kt` provides each DAO via `db.fooDao()`. Both modules are `internal object`, `@InstallIn(SingletonComponent::class)`. Per-domain repositories inject DAOs directly — no extra Hilt indirection.

### D9. Per-domain repos in `:core:<domain>` modules

Search's recent-search repository will live in (a new) `:core:search`, not in `:core:database` and not in `:feature:search:impl`. Dependency direction: `feature:*:impl → core:<domain> → core:database → data:models`. Feature modules never depend on `:core:database` directly.

## Module: `:core:database`

```
core/database/
├── build.gradle.kts                 # applies nubecita.android.library + .hilt + .room; api(projects.data.models)
├── schemas/
│   └── net.kikin.nubecita.core.database.NubecitaDatabase/
│       └── 1.json                   # committed
└── src/
    ├── main/kotlin/net/kikin/nubecita/core/database/
    │   ├── NubecitaDatabase.kt      # abstract RoomDatabase, @Database(entities = [], version = 1, exportSchema = true)
    │   ├── Migrations.kt            # AutoMigration / AutoMigrationSpec / manual Migration list
    │   ├── dao/                     # (empty in foundation; populated per-feature)
    │   ├── model/                   # (empty in foundation; populated per-feature)
    │   ├── util/
    │   │   └── InstantConverter.kt  # kotlinx.datetime.Instant <-> Long
    │   └── di/
    │       ├── DatabaseModule.kt    # @Provides NubecitaDatabase
    │       └── DaosModule.kt        # @Provides per-DAO (empty in foundation)
    └── androidTest/kotlin/net/kikin/nubecita/core/database/
        ├── DatabaseTest.kt          # abstract harness: Room.inMemoryDatabaseBuilder
        └── NubecitaDatabaseSmokeTest.kt  # opens / closes the DB; sanity that the build wires up
```

`build.gradle.kts` shape:

```kotlin
plugins {
  alias(libs.plugins.nubecita.android.library)
  alias(libs.plugins.nubecita.android.hilt)
  alias(libs.plugins.nubecita.android.room)
}

android { namespace = "net.kikin.nubecita.core.database" }

dependencies {
  api(projects.data.models)
  implementation(libs.kotlinx.datetime)
  androidTestImplementation(libs.room.testing)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.ext.junit)
}
```

## Convention plugin: `nubecita.android.room`

New file: `build-logic/convention/src/main/kotlin/net/kikin/nubecita/convention/AndroidRoomConventionPlugin.kt`

```kotlin
class AndroidRoomConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) = with(target) {
    pluginManager.apply("androidx.room")
    pluginManager.apply("com.google.devtools.ksp")

    extensions.configure<RoomExtension> {
      schemaDirectory("$projectDir/schemas")
    }
    extensions.configure<KspExtension> {
      arg("room.generateKotlin", "true")
    }

    dependencies {
      add("implementation", libs.findLibrary("room-runtime").get())
      add("implementation", libs.findLibrary("room-ktx").get())
      add("ksp", libs.findLibrary("room-compiler").get())
    }
  }
}
```

Registered in `build-logic/convention/build.gradle.kts` alongside the existing convention plugins. Classpath needs `room-gradlePlugin` added under `dependencies { compileOnly(...) }`.

The plugin is applied only by `:core:database`. If a second module ever needs Room (e.g. a future `:core:database-encrypted` test variant), it applies the same plugin — no per-module Room boilerplate.

## Version catalog additions

`gradle/libs.versions.toml`:

```toml
[versions]
room = "2.8.2"     # or the latest stable at PR time; verify Kotlin 2.3.21 + KSP 2.3.8 + AGP 9.2.1 compatibility

[libraries]
room-runtime  = { module = "androidx.room:room-runtime",  version.ref = "room" }
room-ktx      = { module = "androidx.room:room-ktx",      version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
room-testing  = { module = "androidx.room:room-testing",  version.ref = "room" }
room-gradlePlugin = { group = "androidx.room", name = "room-gradle-plugin", version.ref = "room" }

[plugins]
room = { id = "androidx.room", version.ref = "room" }
nubecita-android-room = { id = "nubecita.android.room", version = "unspecified" }
```

## Hilt wiring

```kotlin
// :core:database/di/DatabaseModule.kt
@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
  @Provides
  @Singleton
  fun providesNubecitaDatabase(@ApplicationContext context: Context): NubecitaDatabase =
    Room.databaseBuilder(
      context,
      NubecitaDatabase::class.java,
      "nubecita.db",
    ).build()
}

// :core:database/di/DaosModule.kt
@Module
@InstallIn(SingletonComponent::class)
internal object DaosModule {
  // @Provides fun providesRecentSearchDao(db: NubecitaDatabase): RecentSearchDao = db.recentSearchDao()
  // Filled in as each DAO lands.
}
```

DAOs are not separately scoped — Room caches them on the database instance, which is `@Singleton`.

## Schemas + migrations

- `@Database(entities = [], version = 1, exportSchema = true, autoMigrations = [])`.
- Schemas committed under `core/database/schemas/net.kikin.nubecita.core.database.NubecitaDatabase/{N}.json`.
- The `androidTest` source set picks up `schemas/` as an asset directory so `MigrationTestHelper` can locate the JSONs:
  ```kotlin
  android {
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
  }
  ```
- Convention: prefer `@AutoMigration`; manual `Migration` only when AutoMigration can't resolve a column rename or drop.
- Every schema bump must commit the new `{N+1}.json` and add the corresponding AutoMigration entry; CI failure if schema export is missing.

## Testing

### DAO tests (`:core:database/src/androidTest/`)

```kotlin
internal abstract class DatabaseTest {
  protected lateinit var db: NubecitaDatabase

  @Before
  fun setupDatabase() {
    db = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      NubecitaDatabase::class.java,
    ).build()
  }

  @After
  fun teardownDatabase() {
    db.close()
  }
}
```

Concrete DAO tests extend `DatabaseTest()` and use `runTest { ... }` + `Flow.first()`.

### Migration tests

Use `androidx.room.testing.MigrationTestHelper` against the committed schemas. Each new migration lands with a paired test that opens the prior schema, applies the migration, and asserts row preservation. Pattern lifted from the official Room docs.

### Repository tests (per-domain `:core:<domain>/src/test/`)

Repository tests stay on plain JVM with **hand-written DAO fakes** (`FakeRecentSearchDao : RecentSearchDao` backed by `MutableStateFlow`). No Robolectric, no in-memory Room. The DAO interface is the seam.

## Dependency direction

```
feature:*:impl  ──►  core:<domain>  ──►  core:database  ──►  data:models
                                              │
                                              └──►  (Room, KSP)  (isolated here)
```

Feature modules never import `androidx.room.*`. `:core:database` never imports Compose, Navigation, or feature code.

## Risk + rollback

- **Risk: Room Gradle plugin compatibility.** Room 2.6.0+ requires AGP 8.0+; Nubecita is on AGP 9.2.1, so we're well clear. Verify against Kotlin 2.3.21 + KSP 2.3.8 at PR time.
- **Risk: schema-export plumbing.** If `RoomExtension { schemaDirectory(...) }` isn't picked up, generated JSON lands in a default location and CI quickly diverges. The smoke test in `androidTest` plus a CI check that `core/database/schemas/` is non-empty (once the first entity lands) is enough.
- **Rollback:** the foundation has no runtime consumers until `nubecita-vrba.2` adds the first entity. If something blocks, the convention plugin and `:core:database` module can be reverted in one PR without disturbing any feature work.

## Bd structure

A new epic (e.g. `Epic: Room database foundation`) with two children:

1. **build-logic + version catalog: `nubecita.android.room` convention plugin + Room libs/plugins**
   Scope: add Room version, libraries, and plugin entries to `gradle/libs.versions.toml`; create `AndroidRoomConventionPlugin` in `build-logic/convention/`; register it in the plugin map. No `:core:database` module yet. PR is pure infrastructure — green build, no runtime change.

2. **`:core:database` module: `NubecitaDatabase` v1 + Hilt + test harness**
   Scope: add `core/database/` module to `settings.gradle.kts`; ship `NubecitaDatabase` at version 1 with `entities = []`; provide it via `DatabaseModule`; ship empty `DaosModule`; add `InstantConverter`; ship the `DatabaseTest` abstract harness + a smoke test that opens & closes the DB; commit the initial `1.json` schema. Blocked by #1.

After #2 merges, the search epic's **nubecita-vrba.2** (Room recent-search persistence) becomes the first real consumer: it adds `RecentSearchEntity`, `RecentSearchDao`, an entry in `@Database(entities = [...])`, the `providesRecentSearchDao` line in `DaosModule`, the `2.json` schema, and the `AutoMigration(from = 1, to = 2)` entry.

## Open questions

None outstanding. Ready for review.
