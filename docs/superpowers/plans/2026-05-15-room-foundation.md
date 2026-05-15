# Room Database Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land a `:core:database` module + a `nubecita.android.room` convention plugin so feature epics can persist data through a single shared Room database, starting with the Search epic's recent-search persistence (`nubecita-vrba.2`).

**Architecture:** Mirrors *Now in Android*. One Android library module (`:core:database`) owns the `RoomDatabase`, all `@Entity`/DAO classes (initially empty), TypeConverters, and Hilt bindings. A dedicated convention plugin (`nubecita.android.room`) applies the modern `androidx.room` Gradle plugin + KSP, configures schema export, and adds Room dependencies. KSP for Room is isolated to `:core:database` so other modules pay no Room build-time cost. Repositories live in per-domain `:core:<domain>` modules (out of scope for this plan).

**Tech Stack:** Kotlin 2.3.21, AGP 9.2.1, KSP 2.3.8, Room 2.8.x (latest stable at PR time), Hilt 2.59.2, Jetpack Compose BOM 2026.05.00. JVM 17 toolchain. Gradle Version Catalog (`libs`).

**Spec:** [`docs/superpowers/specs/2026-05-15-room-foundation-design.md`](../specs/2026-05-15-room-foundation-design.md)

**bd:** This plan implements two children of `Epic: Room database foundation` (`nubecita-50zx`):
- **Child A — `nubecita-50zx.1`** → Phase 1 (build-logic + version catalog). Single PR.
- **Child B — `nubecita-50zx.2`** → Phase 2 (`:core:database` module). Single PR. Blocked by Child A.

---

## File Structure

**Phase 1 — build-logic + version catalog (Child A):**

- Modify: `gradle/libs.versions.toml` — add Room version, libraries, plugin entries.
- Modify: `build-logic/convention/build.gradle.kts` — add Room Gradle plugin to `compileOnly`; register `nubecita.android.room`.
- Create: `build-logic/convention/src/main/kotlin/net/kikin/nubecita/buildlogic/AndroidRoomConventionPlugin.kt`.
- Modify: `build-logic/README.md` — append `nubecita.android.room` row to the plugin table.

**Phase 2 — `:core:database` module (Child B):**

- Modify: `settings.gradle.kts` — `include(":core:database")`.
- Create: `core/database/build.gradle.kts`.
- Create: `core/database/consumer-rules.pro` (empty).
- Create: `core/database/.gitignore` (`/build`).
- Create: `core/database/README.md` (short — what's here, what isn't).
- Create: `core/database/src/main/kotlin/net/kikin/nubecita/core/database/NubecitaDatabase.kt`.
- Create: `core/database/src/main/kotlin/net/kikin/nubecita/core/database/Migrations.kt`.
- Create: `core/database/src/main/kotlin/net/kikin/nubecita/core/database/util/InstantConverter.kt`.
- Create: `core/database/src/main/kotlin/net/kikin/nubecita/core/database/di/DatabaseModule.kt`.
- Create: `core/database/src/main/kotlin/net/kikin/nubecita/core/database/di/DaosModule.kt`.
- Create: `core/database/src/androidTest/kotlin/net/kikin/nubecita/core/database/DatabaseTest.kt` — abstract harness.
- Create: `core/database/src/androidTest/kotlin/net/kikin/nubecita/core/database/NubecitaDatabaseSmokeTest.kt`.

**Out of scope for this plan (deferred to the search epic):**
- The first real `@Entity` (`RecentSearchEntity`) and DAO.
- The first `1.json` schema export (only generated once an entity exists).
- A second migration / `AutoMigration` entry.

---

## Phase 1 — Build-logic + version catalog (Child A)

### Task 1: Add Room version + libraries + plugin entries to the version catalog

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Add the Room version**

Open `gradle/libs.versions.toml`. In the `[versions]` block, add `room` near the other `androidx*` versions. The resulting block should contain (verify the latest stable at PR time on Maven Central, but `2.8.2` is the floor):

```toml
[versions]
# … existing versions …
room = "2.8.2"
```

- [ ] **Step 2: Add the Room library coordinates**

In the `[libraries]` block, add five entries grouped together:

```toml
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
room-gradlePlugin = { group = "androidx.room", name = "room-gradle-plugin", version.ref = "room" }
```

- [ ] **Step 3: Add the Room plugin id + the Nubecita convention plugin id**

In the `[plugins]` block, add two entries:

```toml
room = { id = "androidx.room", version.ref = "room" }
nubecita-android-room = { id = "nubecita.android.room", version = "unspecified" }
```

The `unspecified` version is the pattern the other `nubecita-android-*` convention plugins use — the `includeBuild("build-logic")` wiring resolves them without a published version.

- [ ] **Step 4: Verify the catalog is well-formed**

Run: `./gradlew help -q`
Expected: succeeds with no "Could not find" errors. Any TOML parse error or unknown reference will fail this command.

- [ ] **Step 5: Verify nothing references the new entries yet**

Run: `grep -RIn "libs\.room\." app core data designsystem build-logic feature 2>/dev/null || echo "no references — expected"`
Expected: prints "no references — expected" (this catalog work is the only change so far; the convention plugin in Task 2 will be the first consumer).

No commit yet — Task 2's work will be in the same PR.

---

### Task 2: Add the `AndroidRoomConventionPlugin` to build-logic

**Files:**
- Modify: `build-logic/convention/build.gradle.kts`
- Create: `build-logic/convention/src/main/kotlin/net/kikin/nubecita/buildlogic/AndroidRoomConventionPlugin.kt`

- [ ] **Step 1: Add the Room Gradle plugin to build-logic's classpath**

Open `build-logic/convention/build.gradle.kts`. In the `dependencies { ... }` block, add one line alongside the existing `compileOnly` entries (keep them alphabetized by current ordering):

```kotlin
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
    compileOnly(libs.screenshot.gradlePlugin)
    compileOnly(libs.sortDependencies.gradlePlugin)
}
```

Rationale: `compileOnly` is enough because the convention plugin only needs the Room plugin's types at compile time (`RoomExtension`); at runtime, consumer modules apply the plugin themselves via `pluginManager.apply("androidx.room")`.

- [ ] **Step 2: Write the convention plugin class**

Create `build-logic/convention/src/main/kotlin/net/kikin/nubecita/buildlogic/AndroidRoomConventionPlugin.kt` with this exact content:

```kotlin
package net.kikin.nubecita.buildlogic

import androidx.room.gradle.RoomExtension
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Applies the Room Gradle plugin and KSP, configures schema export to
 * `$projectDir/schemas`, requests Kotlin code generation from KSP, and
 * wires the Room runtime + ktx libraries plus the Room KSP compiler.
 *
 * Apply alongside [AndroidLibraryConventionPlugin] and
 * [AndroidHiltConventionPlugin]; KSP is idempotent so double-applying
 * via the Hilt convention is safe.
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("androidx.room")
            pluginManager.apply("com.google.devtools.ksp")

            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }
            extensions.configure<KspExtension> {
                arg("room.generateKotlin", "true")
            }

            dependencies {
                "implementation"(libs.findLibrary("room-runtime").get())
                "implementation"(libs.findLibrary("room-ktx").get())
                "ksp"(libs.findLibrary("room-compiler").get())
            }
        }
    }
}
```

- [ ] **Step 3: Register the plugin in the gradlePlugin block**

In `build-logic/convention/build.gradle.kts`, in the `gradlePlugin { plugins { ... } }` block, append a new `register` entry after `androidApplication`:

```kotlin
register("androidRoom") {
    id = "nubecita.android.room"
    implementationClass = "net.kikin.nubecita.buildlogic.AndroidRoomConventionPlugin"
}
```

- [ ] **Step 4: Verify build-logic compiles**

Run: `./gradlew :convention:build -p build-logic -q`
Expected: BUILD SUCCESSFUL. Any unresolved import (`RoomExtension`, `KspExtension`) or missing `libs.room.gradlePlugin` reference will fail here.

- [ ] **Step 5: Verify the plugin is discoverable by the root build**

Run: `./gradlew help -q` from the repo root.
Expected: succeeds. (The convention plugin isn't applied to any module yet, but Gradle resolving the plugin map is enough to catch ID typos.)

---

### Task 3: Update build-logic README + commit Phase 1

**Files:**
- Modify: `build-logic/README.md`

- [ ] **Step 1: Append the new plugin row to the plugin table**

Open `build-logic/README.md`. Find the `## The five plugins` heading and its markdown table. Rename it to `## The plugins` (since this PR makes it six), then add a row after the `nubecita.android.application` row:

```markdown
| `nubecita.android.room` | `androidx.room` Gradle plugin + `com.google.devtools.ksp`, `RoomExtension.schemaDirectory("$projectDir/schemas")`, `ksp { arg("room.generateKotlin", "true") }`, Room runtime + ktx + Room KSP compiler | `:core:database` (this plan introduces it) |
```

- [ ] **Step 2: Stage and commit Phase 1**

Run from the repo root:

```bash
git add gradle/libs.versions.toml \
        build-logic/convention/build.gradle.kts \
        build-logic/convention/src/main/kotlin/net/kikin/nubecita/buildlogic/AndroidRoomConventionPlugin.kt \
        build-logic/README.md
```

Then commit:

```bash
git commit -m "$(cat <<'EOF'
build(build-logic): add nubecita.android.room convention plugin

Adds Room 2.8.x to the version catalog and a class-based convention
plugin that applies the androidx.room Gradle plugin + KSP, configures
schema export to $projectDir/schemas, and wires room-runtime/-ktx +
the Room KSP compiler. No module applies the plugin yet; the :core:database
module that consumes it ships in the next PR.

Refs: nubecita-50zx.1
EOF
)"
```

Pre-commit hooks run spotless, commitlint, and detect-secrets. If spotless rewrites any file, re-run `git add` + `git commit` (do not use `--no-verify`).

- [ ] **Step 3: Open the PR**

Push the branch and open a PR with `Closes: nubecita-50zx.1` in the body. Phase 1 is done.

---

## Phase 2 — `:core:database` module (Child B)

**Prerequisite:** Phase 1 merged.

### Task 4: Wire the `:core:database` module into the build

**Files:**
- Modify: `settings.gradle.kts`
- Create: `core/database/build.gradle.kts`
- Create: `core/database/consumer-rules.pro` (empty)
- Create: `core/database/.gitignore`
- Create: `core/database/README.md`

- [ ] **Step 1: Add `:core:database` to settings.gradle.kts**

Open `settings.gradle.kts`. In the alphabetically-ordered `include(":core:*")` block, add a line in the right spot:

```kotlin
include(":core:common")
include(":core:database")
include(":core:feed-mapping")
```

- [ ] **Step 2: Create the module directory and empty pro-guard file**

Run from the repo root:

```bash
mkdir -p core/database/src/main/kotlin/net/kikin/nubecita/core/database/{di,util}
mkdir -p core/database/src/androidTest/kotlin/net/kikin/nubecita/core/database
mkdir -p core/database/schemas
touch core/database/consumer-rules.pro
```

- [ ] **Step 3: Create `core/database/.gitignore`**

Write `core/database/.gitignore`:

```
/build
```

- [ ] **Step 4: Create `core/database/README.md`**

Write `core/database/README.md` (short — mirrors `:core:auth/README.md` shape):

````markdown
# `:core:database`

The single Room database for Nubecita. Hosts the `RoomDatabase` class, all
`@Entity` types, all DAOs, TypeConverters, and the Hilt modules that provide
them. Repositories that consume DAOs live in per-domain `:core:<domain>`
modules — never inside `:core:database`, and never inside feature `:impl`
modules.

## Conventions

- Reads return `Flow<T>`; writes are `suspend fun`. Multi-statement writes
  use `@Transaction suspend fun`.
- Entities never cross the module boundary. Each entity ships a same-file
  top-level extension `fun FooEntity.asExternalModel(): Foo` returning a
  `:data:models` type; repositories map before exposing flows.
- Schema export is on (`exportSchema = true`). Generated JSON is committed
  under `schemas/`. Every schema bump must commit the new `{N}.json` and
  add the corresponding `@AutoMigration` entry on `@Database`.
- Prefer `@AutoMigration` (with `AutoMigrationSpec` for ambiguous renames).
  Hand-write a `Migration` only when AutoMigration cannot resolve the diff.

## Out of the box

This module ships with `entities = []` so the database wires up cleanly
before any feature owns persistence. The first real entity
(`RecentSearchEntity`) lands as part of the Search epic.
````

- [ ] **Step 5: Create `core/database/build.gradle.kts`**

Write the build script:

```kotlin
plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
    alias(libs.plugins.nubecita.android.room)
}

android {
    namespace = "net.kikin.nubecita.core.database"

    // Pick up the committed schemas directory as androidTest assets so
    // MigrationTestHelper (added when migrations exist) can locate them.
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

dependencies {
    api(project(":data:models"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.room.testing)
}
```

- [ ] **Step 6: Sync and verify the module resolves**

Run: `./gradlew :core:database:tasks -q`
Expected: prints the standard Android library task list. Failure modes to watch for: "Plugin with id 'nubecita.android.room' not found" (Phase 1 not merged or catalog typo); "namespace 'net.kikin.nubecita.core.database' is missing" (build.gradle.kts namespace block wrong).

---

### Task 5: Define `NubecitaDatabase`, `Migrations`, and `InstantConverter`

**Files:**
- Create: `core/database/src/main/kotlin/net/kikin/nubecita/core/database/NubecitaDatabase.kt`
- Create: `core/database/src/main/kotlin/net/kikin/nubecita/core/database/Migrations.kt`
- Create: `core/database/src/main/kotlin/net/kikin/nubecita/core/database/util/InstantConverter.kt`

- [ ] **Step 1: Write `InstantConverter.kt`**

```kotlin
package net.kikin.nubecita.core.database.util

import androidx.room.TypeConverter
import kotlinx.datetime.Instant

/**
 * Persists `kotlinx.datetime.Instant` as the epoch-millisecond `Long`
 * Room natively supports. `null` round-trips through unchanged so DAOs
 * can declare nullable timestamp columns without a separate converter.
 *
 * Registered on [net.kikin.nubecita.core.database.NubecitaDatabase] via
 * `@TypeConverters(InstantConverter::class)`.
 */
internal object InstantConverter {
    @TypeConverter
    fun fromEpochMillis(value: Long?): Instant? = value?.let(Instant::fromEpochMilliseconds)

    @TypeConverter
    fun toEpochMillis(instant: Instant?): Long? = instant?.toEpochMilliseconds()
}
```

- [ ] **Step 2: Write `Migrations.kt` (empty list for v1)**

```kotlin
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
```

- [ ] **Step 3: Write `NubecitaDatabase.kt`**

```kotlin
package net.kikin.nubecita.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.kikin.nubecita.core.database.util.InstantConverter

/**
 * The single Room database for Nubecita. Ships at version 1 with no
 * entities — the first real `@Entity` (`RecentSearchEntity` from the
 * Search epic) will land as a migration to version 2 and populate the
 * `entities` array below.
 *
 * Schema export is on; generated JSON is committed under
 * `core/database/schemas/`. Every schema bump must commit the new
 * `{N}.json` file and add the corresponding `@AutoMigration` entry to
 * the `autoMigrations` array below.
 */
@Database(
    entities = [],
    version = 1,
    exportSchema = true,
    autoMigrations = [],
)
@TypeConverters(InstantConverter::class)
abstract class NubecitaDatabase : RoomDatabase()
```

- [ ] **Step 4: Run a compile check**

Run: `./gradlew :core:database:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL. KSP will warn that the database has no entities — that's expected and acceptable for v1.

- [ ] **Step 5: Run spotless**

Run: `./gradlew :core:database:spotlessApply -q`
Expected: no errors. If spotless rewrites a file, re-stage it before the eventual commit.

---

### Task 6: Wire Hilt `DatabaseModule` + empty `DaosModule`

**Files:**
- Create: `core/database/src/main/kotlin/net/kikin/nubecita/core/database/di/DatabaseModule.kt`
- Create: `core/database/src/main/kotlin/net/kikin/nubecita/core/database/di/DaosModule.kt`

- [ ] **Step 1: Write `DatabaseModule.kt`**

```kotlin
package net.kikin.nubecita.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.kikin.nubecita.core.database.MANUAL_MIGRATIONS
import net.kikin.nubecita.core.database.NubecitaDatabase

/**
 * Provides the singleton [NubecitaDatabase]. No
 * `fallbackToDestructiveMigration` — migration failures should surface
 * as crashes in development so the schema export + migration test
 * harness has a chance to catch them before release.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun providesNubecitaDatabase(
        @ApplicationContext context: Context,
    ): NubecitaDatabase =
        Room.databaseBuilder(
            context,
            NubecitaDatabase::class.java,
            DATABASE_NAME,
        )
            .addMigrations(*MANUAL_MIGRATIONS)
            .build()

    private const val DATABASE_NAME = "nubecita.db"
}
```

- [ ] **Step 2: Write `DaosModule.kt`**

```kotlin
package net.kikin.nubecita.core.database.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Provides each DAO from the singleton [NubecitaDatabase]. DAOs are not
 * separately scoped — Room caches them on the database instance, which
 * is `@Singleton`.
 *
 * Empty at v1; each `@Provides fun providesFooDao(db: NubecitaDatabase): FooDao
 * = db.fooDao()` lands alongside the entity/DAO it exposes.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DaosModule
```

- [ ] **Step 3: Run a compile check + spotless**

Run: `./gradlew :core:database:compileDebugKotlin :core:database:spotlessApply -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Verify Hilt resolves the graph against `:app`**

Run: `./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL. `:app` doesn't depend on `:core:database` yet, so this is a smoke check that nothing in the Hilt graph regressed.

---

### Task 7: Add the `DatabaseTest` harness + a smoke test

**Files:**
- Create: `core/database/src/androidTest/kotlin/net/kikin/nubecita/core/database/DatabaseTest.kt`
- Create: `core/database/src/androidTest/kotlin/net/kikin/nubecita/core/database/NubecitaDatabaseSmokeTest.kt`

- [ ] **Step 1: Write the abstract `DatabaseTest` harness**

```kotlin
package net.kikin.nubecita.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before

/**
 * Shared harness for DAO + database tests. Each concrete test subclass
 * gets a fresh in-memory [NubecitaDatabase] before every `@Test` and
 * closes it in `@After`. No disk, no clear-between-tests dance.
 *
 * Mirrors the Now in Android `DatabaseTest` pattern.
 */
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

- [ ] **Step 2: Write the smoke test**

```kotlin
package net.kikin.nubecita.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sanity check that the Room build pipeline produced a usable database
 * class and that the convention plugin's runtime deps are on the
 * classpath. No DAOs are exercised — those tests will land alongside
 * the first real entity.
 */
@RunWith(AndroidJUnit4::class)
internal class NubecitaDatabaseSmokeTest : DatabaseTest() {
    @Test
    fun database_opens_and_closes() {
        // openHelper is lazily initialized; reading writableDatabase
        // forces the underlying SQLite connection open so a failure to
        // generate the Room implementation class would surface here.
        val sqlite = db.openHelper.writableDatabase
        assertNotNull(sqlite)
        assertTrue(sqlite.isOpen)
    }
}
```

- [ ] **Step 3: Run spotless**

Run: `./gradlew :core:database:spotlessApply -q`
Expected: no errors.

- [ ] **Step 4: Build the androidTest variant to catch compile errors**

Run: `./gradlew :core:database:assembleDebugAndroidTest -q`
Expected: BUILD SUCCESSFUL. This catches missing imports or runner config errors without needing a device.

- [ ] **Step 5: Run the smoke test on a connected device or emulator**

Per the user's standing convention, use the `android-cli` skill to launch an emulator if no device is already connected. Run `adb devices` first; if empty, list emulators with `android emulator list` and start one with `android emulator start <name>` (notify the user during the wait — emulators take ~30-60s to boot).

Once a device is attached:

Run: `./gradlew :core:database:connectedDebugAndroidTest -q`
Expected: 1 test passes (`NubecitaDatabaseSmokeTest.database_opens_and_closes`).

If the test fails:
- "Class not found: net.kikin.nubecita.core.database.NubecitaDatabase_Impl" — KSP didn't generate the impl. Re-run `./gradlew :core:database:clean :core:database:assembleDebug` and inspect the KSP output under `core/database/build/generated/ksp/`.
- "no such table: room_master_table" — the database failed to initialize. Check that `@Database(entities = [], version = 1, exportSchema = true)` is intact and that the convention plugin successfully applied `androidx.room`.

---

### Task 8: Commit Phase 2 and label the PR

**Files:** all of Phase 2.

- [ ] **Step 1: Stage all Phase 2 changes**

Run from the repo root:

```bash
git add settings.gradle.kts \
        core/database/.gitignore \
        core/database/README.md \
        core/database/build.gradle.kts \
        core/database/consumer-rules.pro \
        core/database/src/main/kotlin/net/kikin/nubecita/core/database/NubecitaDatabase.kt \
        core/database/src/main/kotlin/net/kikin/nubecita/core/database/Migrations.kt \
        core/database/src/main/kotlin/net/kikin/nubecita/core/database/util/InstantConverter.kt \
        core/database/src/main/kotlin/net/kikin/nubecita/core/database/di/DatabaseModule.kt \
        core/database/src/main/kotlin/net/kikin/nubecita/core/database/di/DaosModule.kt \
        core/database/src/androidTest/kotlin/net/kikin/nubecita/core/database/DatabaseTest.kt \
        core/database/src/androidTest/kotlin/net/kikin/nubecita/core/database/NubecitaDatabaseSmokeTest.kt
```

- [ ] **Step 2: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(core/database): scaffold Room database module

Adds a :core:database module wired through the nubecita.android.room
convention plugin from the previous PR. NubecitaDatabase ships at
version 1 with entities = [] and exportSchema = true; the first real
@Entity lands with the Search epic's recent-search persistence.

Includes a DatabaseTest abstract harness using in-memory Room and a
smoke test that opens and closes the database. Hilt provides the
singleton database via DatabaseModule; DaosModule is empty until the
first DAO lands.

Refs: nubecita-50zx.2
EOF
)"
```

- [ ] **Step 3: Push, open the PR, and add the `run-instrumented` label**

Per the user's standing convention, any PR that touches `*/src/androidTest/**` needs the `run-instrumented` label so CI runs the connected-test job.

```bash
git push -u origin HEAD
gh pr create --title "feat(core/database): scaffold Room database module" \
             --body "$(cat <<'EOF'
## Summary
- Adds :core:database wired through the nubecita.android.room convention plugin.
- NubecitaDatabase v1 with entities = [] + exportSchema = true.
- DatabaseTest harness + NubecitaDatabaseSmokeTest.

The first real @Entity (RecentSearchEntity) will land as part of the Search epic
(nubecita-vrba.2), at which point the schemas/ directory gets its first 1.json.

Closes: nubecita-50zx.2
EOF
)"
gh pr edit --add-label run-instrumented
```

Phase 2 is done. After both PRs merge, `nubecita-vrba.2` (Room recent-search persistence in the Search epic) becomes unblocked and can land the first real entity.

---

## Self-Review Notes

Run after writing the plan; surface any gaps so the engineer doesn't hit them mid-task.

- **Spec coverage.** Every numbered Decision in the spec maps to a task: D1 (one DB, all entities) — Task 5 (entities array). D2 (modern `androidx.room` plugin) — Task 2 Step 2. D3 (`asExternalModel()` mapping) — captured in README; deferred to feature consumers. D4 (Flow reads, suspend writes) — captured in README; first DAO will enforce. D5 (no `fallbackToDestructiveMigration`) — Task 6 Step 1. D6 (AutoMigration preferred) — Migrations.kt KDoc + README. D7 (KSP isolation) — only `:core:database` applies the convention. D8 (Hilt provides DB + DAOs) — Tasks 6. D9 (per-domain repos elsewhere) — README + spec; nothing to implement in this plan.
- **Placeholder scan.** bd IDs (`nubecita-50zx.1`, `nubecita-50zx.2`) filled in. No "TBD"/"TODO" in the plan.
- **Type consistency.** `NubecitaDatabase`, `MANUAL_MIGRATIONS`, `InstantConverter`, `DatabaseModule`, `DaosModule`, `DatabaseTest`, `NubecitaDatabaseSmokeTest` — all spellings and packages identical across Tasks 4–8.
- **Out-of-scope reminder.** No real entity, no DAO, no schema JSON, no migration test — those land with `nubecita-vrba.2`. The plan flags this in three places (Goal, File Structure, Task 7 Step 5) so an engineer running the plan doesn't try to validate schema export.
- **CI label.** Task 8 Step 3 covers the `run-instrumented` label requirement (memory: `feedback_run_instrumented_label_on_androidtest_prs.md`).
