# `:core:actors` Extraction + Cache — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consolidate the two actor-search code paths (search People tab + composer @-mention) into a new `:core:actors` capability with a DID-keyed Room cache populated by write-through; migrate both existing consumers with no behavior change.

**Architecture:** New `:core:actors` module exposes a single `ActorRepository` facade (typeahead + paginated search + cached read). Network mappers (`ProfileView`/`ProfileViewBasic` → `ActorUi`) live in `:core:actors`; the Room entity + DAO + entity↔`ActorUi` mappers live in `:core:database` (the single Room owner). Write-through upserts every search result into the `actors` table, always overwriting (block/unfollow must not surface stale rows). Consumers keep their own error UX.

**Tech Stack:** Kotlin, Hilt (KSP), Room (`@AutoMigration`, `kotlinx.datetime.Instant` via `InstantConverter`), atproto-kotlin `ActorService`, JUnit5 + Turbine + MockK, Ktor `MockEngine` for repo tests.

**Spec:** `docs/superpowers/specs/2026-05-29-core-actors-extraction-and-cache-design.md` · **bd:** `nubecita-26a6`

---

## File structure

**Create**
- `core/actors/build.gradle.kts` — module config
- `core/actors/src/main/kotlin/net/kikin/nubecita/core/actors/ActorRepository.kt` — public interface + `ActorSearchPage`
- `core/actors/src/main/kotlin/net/kikin/nubecita/core/actors/internal/DefaultActorRepository.kt` — impl + network mappers
- `core/actors/src/main/kotlin/net/kikin/nubecita/core/actors/internal/ActorsModule.kt` — Hilt `@Binds`
- `core/actors/src/test/kotlin/net/kikin/nubecita/core/actors/internal/DefaultActorRepositoryTest.kt`
- `core/database/src/main/kotlin/net/kikin/nubecita/core/database/model/ActorEntity.kt` — entity + `asExternalModel()` + `ActorUi.toCacheEntity()`
- `core/database/src/main/kotlin/net/kikin/nubecita/core/database/dao/ActorDao.kt`
- `core/database/src/androidTest/kotlin/net/kikin/nubecita/core/database/ActorDaoTest.kt`
- `core/database/schemas/net.kikin.nubecita.core.database.NubecitaDatabase/3.json` — generated, committed

**Modify**
- `settings.gradle.kts` — `include(":core:actors")`
- `core/database/src/main/kotlin/net/kikin/nubecita/core/database/NubecitaDatabase.kt` — v3 + entity + autoMigration + dao accessor
- `core/database/src/main/kotlin/net/kikin/nubecita/core/database/di/DaosModule.kt` — provide `ActorDao`
- `core/database/build.gradle.kts` — ensure `:data:models` dep (for `ActorUi`)
- `feature/search/impl/build.gradle.kts` — swap `:core:actors` in
- `feature/search/impl/.../SearchActorsViewModel.kt` — inject `ActorRepository`
- `feature/search/impl/.../SearchActorsContract.kt` (+ any `SearchActorsPage` references) — use `ActorSearchPage`
- `feature/composer/impl/build.gradle.kts` — swap `:core:actors` in
- `feature/composer/impl/.../ComposerViewModel.kt` — inject `ActorRepository`
- `feature/composer/impl/build.gradle.kts` / consumers — drop `:core:posting` if now unused for this (keep if used elsewhere)
- test fakes (search ×2, composer)

**Delete**
- `feature/search/impl/.../data/SearchActorsRepository.kt`, `data/DefaultSearchActorsRepository.kt`, `di/SearchActorsRepositoryModule.kt`
- `core/posting/.../ActorTypeaheadRepository.kt`, `internal/DefaultActorTypeaheadRepository.kt`, the `@Binds` in `PostingModule.kt`, and posting's typeahead fake

---

## Task 1: Scaffold `:core:actors` with the public API surface

**Files:**
- Modify: `settings.gradle.kts`
- Create: `core/actors/build.gradle.kts`
- Create: `core/actors/src/main/kotlin/net/kikin/nubecita/core/actors/ActorRepository.kt`

- [ ] **Step 1: Register the module**

In `settings.gradle.kts`, add the include in alphabetical position (between `:core:` entries — after the `include(":core:")` block start, before `:core:auth`):

```kotlin
include(":core:actors")
include(":core:auth")
```

- [ ] **Step 2: Create the module build file**

`core/actors/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
}

android {
    namespace = "net.kikin.nubecita.core.actors"
}

dependencies {
    api(project(":data:models"))

    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
```

> Note: confirm the exact accessor names against `gradle/libs.versions.toml` (e.g. `libs.kotlinx.datetime`, `libs.turbine`, `libs.ktor.client.mock`). They are already used by `:core:posting` / `:core:database` — copy the accessor spelling from there if an accessor is unresolved.

- [ ] **Step 3: Create the public API**

`core/actors/src/main/kotlin/net/kikin/nubecita/core/actors/ActorRepository.kt`:

```kotlin
package net.kikin.nubecita.core.actors

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import net.kikin.nubecita.data.models.ActorUi

/**
 * Single seam for actor discovery + display. Network search (typeahead +
 * paginated) plus a DID-keyed local cache populated by write-through.
 *
 * Network methods return [Result]; CancellationException always propagates.
 * No error UX is baked in — each consumer maps failures itself (search's
 * People tab uses a typed error sum; the composer dropdown hides on any
 * failure). Empty matches are [Result.success] with an empty list.
 *
 * Every successful search upserts its actors into the cache (always
 * overwriting), so [getActor] reflects the freshest sighting — a blocked
 * or unfollowed actor is replaced by the live response, never resurrected
 * from a stale query-result cache.
 */
interface ActorRepository {
    /** Fast as-you-type suggestions — `app.bsky.actor.searchActorsTypeahead`. Single-shot. */
    suspend fun searchTypeahead(query: String, limit: Int = 8): Result<List<ActorUi>>

    /** Full paginated search — `app.bsky.actor.searchActors`. */
    suspend fun searchActors(query: String, cursor: String? = null, limit: Int = 25): Result<ActorSearchPage>

    /** Observe a single cached actor by DID; emits null when not cached. */
    fun getActor(did: String): Flow<ActorUi?>
}

/** One page of [ActorRepository.searchActors] results. */
data class ActorSearchPage(
    val items: ImmutableList<ActorUi>,
    val nextCursor: String?,
)
```

- [ ] **Step 4: Verify it configures + compiles**

Run: `./gradlew :core:actors:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (no impl yet — interface only).

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts core/actors/build.gradle.kts core/actors/src/main/kotlin/net/kikin/nubecita/core/actors/ActorRepository.kt
git commit -m "feat(core/actors): scaffold module + ActorRepository surface

Refs: nubecita-26a6"
```

---

## Task 2: Add the DID-keyed actor cache to `:core:database`

**Files:**
- Modify: `core/database/build.gradle.kts` (ensure `:data:models` dep)
- Create: `core/database/src/main/kotlin/net/kikin/nubecita/core/database/model/ActorEntity.kt`
- Create: `core/database/src/main/kotlin/net/kikin/nubecita/core/database/dao/ActorDao.kt`
- Modify: `core/database/src/main/kotlin/net/kikin/nubecita/core/database/NubecitaDatabase.kt`
- Modify: `core/database/src/main/kotlin/net/kikin/nubecita/core/database/di/DaosModule.kt`
- Test: `core/database/src/androidTest/kotlin/net/kikin/nubecita/core/database/ActorDaoTest.kt`
- Generated: `core/database/schemas/.../3.json`

- [ ] **Step 1: Ensure `:data:models` is a dependency**

Open `core/database/build.gradle.kts`. If `project(":data:models")` is not already in `dependencies`, add it (alphabetical) — `asExternalModel()` returns `ActorUi`:

```kotlin
    implementation(project(":data:models"))
```

- [ ] **Step 2: Create the entity + co-located mappers**

`core/database/src/main/kotlin/net/kikin/nubecita/core/database/model/ActorEntity.kt`:

```kotlin
package net.kikin.nubecita.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import net.kikin.nubecita.data.models.ActorUi

/**
 * DID-keyed cache of an actor's display data. Populated by `:core:actors`
 * write-through on every successful search; always overwritten so a
 * blocked/unfollowed actor's row reflects the latest live response.
 *
 * [lastSeenAt] orders "recent" surfaces (chats recipient picker, PR2) and
 * leaves room for a future eviction cap; it is NOT used for invalidation.
 */
@Entity(tableName = "actors")
data class ActorEntity(
    @PrimaryKey val did: String,
    val handle: String,
    @ColumnInfo(name = "display_name") val displayName: String?,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String?,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Instant,
)

/** Entity → boundary model. Drops the cache-only [lastSeenAt]. */
fun ActorEntity.asExternalModel(): ActorUi =
    ActorUi(did = did, handle = handle, displayName = displayName, avatarUrl = avatarUrl)

/** Boundary model → entity for write-through; caller supplies the sighting time. */
fun ActorUi.toCacheEntity(lastSeenAt: Instant): ActorEntity =
    ActorEntity(did = did, handle = handle, displayName = displayName, avatarUrl = avatarUrl, lastSeenAt = lastSeenAt)
```

- [ ] **Step 3: Create the DAO**

`core/database/src/main/kotlin/net/kikin/nubecita/core/database/dao/ActorDao.kt`:

```kotlin
package net.kikin.nubecita.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import net.kikin.nubecita.core.database.model.ActorEntity

/**
 * DAO for the `actors` cache. [upsert] is the write-through path
 * (`@Upsert` updates in place rather than delete+reinsert). [getActor]
 * observes a single row by DID.
 */
@Dao
interface ActorDao {
    @Upsert
    suspend fun upsert(actors: List<ActorEntity>)

    @Query("SELECT * FROM actors WHERE did = :did")
    fun getActor(did: String): Flow<ActorEntity?>
}
```

- [ ] **Step 4: Wire the entity + migration into the database**

Edit `NubecitaDatabase.kt`. Add the import, add `ActorEntity::class` to `entities`, bump `version` to `3`, add the additive auto-migration (keep the existing 1→2 spec entry), and add the DAO accessor:

```kotlin
import net.kikin.nubecita.core.database.dao.ActorDao
import net.kikin.nubecita.core.database.model.ActorEntity
// ...
@Database(
    entities = [RecentSearchEntity::class, ActorEntity::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = BootstrapEntityDrop::class),
        AutoMigration(from = 2, to = 3),
    ],
)
@TypeConverters(InstantConverter::class)
abstract class NubecitaDatabase : RoomDatabase() {
    abstract fun recentSearchDao(): RecentSearchDao

    abstract fun actorDao(): ActorDao
}
```

- [ ] **Step 5: Provide the DAO via Hilt**

In `DaosModule.kt`, add the import and provider:

```kotlin
import net.kikin.nubecita.core.database.dao.ActorDao
// ...
    @Provides
    fun providesActorDao(database: NubecitaDatabase): ActorDao = database.actorDao()
```

- [ ] **Step 6: Generate + verify the schema; this also produces 3.json**

Run: `./gradlew :core:database:assembleDebug`
Expected: BUILD SUCCESSFUL, and a new file `core/database/schemas/net.kikin.nubecita.core.database.NubecitaDatabase/3.json` appears (new `actors` table). If Room errors that the auto-migration can't be generated, the table is purely additive — re-check that no column was renamed; additive tables need no spec.

- [ ] **Step 7: Write the DAO test (instrumented — Room needs a real SQLite)**

`core/database/src/androidTest/kotlin/net/kikin/nubecita/core/database/ActorDaoTest.kt`:

```kotlin
package net.kikin.nubecita.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import net.kikin.nubecita.core.database.dao.ActorDao
import net.kikin.nubecita.core.database.model.ActorEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActorDaoTest {
    private lateinit var db: NubecitaDatabase
    private lateinit var dao: ActorDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NubecitaDatabase::class.java,
        ).build()
        dao = db.actorDao()
    }

    @After
    fun tearDown() = db.close()

    private fun actor(did: String, handle: String, name: String?, seen: Long) =
        ActorEntity(did, handle, name, avatarUrl = null, lastSeenAt = Instant.fromEpochMilliseconds(seen))

    @Test
    fun getActor_emitsNull_whenAbsent() = runTest {
        dao.getActor("did:absent").test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun upsert_thenGetActor_emitsRow() = runTest {
        dao.upsert(listOf(actor("did:a", "alice.bsky.social", "Alice", 1_000)))
        dao.getActor("did:a").test {
            assertEquals("alice.bsky.social", awaitItem()?.handle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun upsert_overwritesExistingDid() = runTest {
        dao.upsert(listOf(actor("did:a", "old.handle", "Old", 1_000)))
        dao.upsert(listOf(actor("did:a", "new.handle", "New", 2_000)))
        dao.getActor("did:a").test {
            val row = awaitItem()
            assertEquals("new.handle", row?.handle)
            assertEquals("New", row?.displayName)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 8: Run the DAO test**

Run: `./gradlew :core:database:connectedDebugAndroidTest --tests "*ActorDaoTest"` (needs a connected device/emulator).
Expected: 3 tests PASS.

> If a connected device isn't available in this environment, mark the test written and defer execution to the `run-instrumented` CI label / local device; do not skip writing it.

- [ ] **Step 9: Commit**

```bash
git add core/database/
git commit -m "feat(core/database): add DID-keyed actors cache table (v2→v3)

ActorEntity + ActorDao + asExternalModel/toCacheEntity mappers, additive
@AutoMigration(2→3), committed 3.json, DAO provider + ActorDaoTest.

Refs: nubecita-26a6"
```

---

## Task 3: Implement `DefaultActorRepository` (network + write-through) + Hilt binding

**Files:**
- Create: `core/actors/src/main/kotlin/net/kikin/nubecita/core/actors/internal/DefaultActorRepository.kt`
- Create: `core/actors/src/main/kotlin/net/kikin/nubecita/core/actors/internal/ActorsModule.kt`
- Test: `core/actors/src/test/kotlin/net/kikin/nubecita/core/actors/internal/DefaultActorRepositoryTest.kt`

- [ ] **Step 1: Write the failing repository test**

Mirror the existing `DefaultSearchActorsRepositoryTest` / `DefaultActorTypeaheadRepositoryTest` setup (they build an `XrpcClientProvider` over a Ktor `MockEngine`). Copy that test's `MockEngine` JSON-fixture scaffolding into this file, then assert:

`core/actors/src/test/kotlin/net/kikin/nubecita/core/actors/internal/DefaultActorRepositoryTest.kt` (key cases — fill the MockEngine harness from the existing repo tests):

```kotlin
// @ExtendWith(MainDispatcherExtension::class) per :core:testing convention
// dispatcher = UnconfinedTestDispatcher(); dao = mockk(relaxed = true)

@Test fun searchTypeahead_success_mapsAndWritesThrough() = runTest {
    // MockEngine returns 2 ProfileViewBasic actors
    val result = repo.searchTypeahead("al")
    assertTrue(result.isSuccess)
    assertEquals(listOf("did:a", "did:b"), result.getOrThrow().map { it.did })
    coVerify { dao.upsert(match { it.size == 2 && it.all { e -> e.lastSeenAt == fixedNow } }) }
}

@Test fun searchActors_success_pagesAndWritesThrough() = runTest {
    val page = repo.searchActors("al", cursor = null).getOrThrow()
    assertEquals("cursor-2", page.nextCursor)
    coVerify { dao.upsert(any()) }
}

@Test fun search_emptyMatches_isSuccessEmpty_noThrow() = runTest {
    assertEquals(emptyList(), repo.searchTypeahead("zzz").getOrThrow())
}

@Test fun search_networkError_isFailure() = runTest {
    // MockEngine throws / 500
    assertTrue(repo.searchActors("x").isFailure)
}

@Test fun search_blankDisplayName_normalizesToNull() = runTest {
    assertNull(repo.searchTypeahead("al").getOrThrow().first().displayName)
}

@Test fun cacheWriteFailure_doesNotFailSearch() = runTest {
    coEvery { dao.upsert(any()) } throws RuntimeException("disk full")
    assertTrue(repo.searchTypeahead("al").isSuccess) // write-through is best-effort
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./gradlew :core:actors:testDebugUnitTest`
Expected: FAIL (no `DefaultActorRepository`).

- [ ] **Step 3: Implement the repository**

`core/actors/src/main/kotlin/net/kikin/nubecita/core/actors/internal/DefaultActorRepository.kt`:

```kotlin
package net.kikin.nubecita.core.actors.internal

import io.github.kikin81.atproto.app.bsky.actor.ActorService
import io.github.kikin81.atproto.app.bsky.actor.ProfileView
import io.github.kikin81.atproto.app.bsky.actor.ProfileViewBasic
import io.github.kikin81.atproto.app.bsky.actor.SearchActorsRequest
import io.github.kikin81.atproto.app.bsky.actor.SearchActorsTypeaheadRequest
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import net.kikin.nubecita.core.actors.ActorRepository
import net.kikin.nubecita.core.actors.ActorSearchPage
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.database.dao.ActorDao
import net.kikin.nubecita.core.database.model.asExternalModel
import net.kikin.nubecita.core.database.model.toCacheEntity
import net.kikin.nubecita.data.models.ActorUi
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultActorRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        private val actorDao: ActorDao,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ActorRepository {
        override suspend fun searchTypeahead(query: String, limit: Int): Result<List<ActorUi>> {
            require(limit in 1..100) { "limit must be in 1..100, got $limit" }
            return withContext(dispatcher) {
                try {
                    val actors = ActorService(xrpcClientProvider.authenticated())
                        .searchActorsTypeahead(SearchActorsTypeaheadRequest(q = query, limit = limit.toLong()))
                        .actors.map { it.toActorUi() }
                    writeThrough(actors)
                    Result.success(actors)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    Timber.tag(TAG).d(t, "searchTypeahead(q=%s) failed", query)
                    Result.failure(t)
                }
            }
        }

        override suspend fun searchActors(query: String, cursor: String?, limit: Int): Result<ActorSearchPage> {
            require(limit in 1..100) { "limit must be in 1..100, got $limit" }
            return withContext(dispatcher) {
                try {
                    val response = ActorService(xrpcClientProvider.authenticated())
                        .searchActors(SearchActorsRequest(q = query, cursor = cursor, limit = limit.toLong()))
                    val items = response.actors.map { it.toActorUi() }
                    writeThrough(items)
                    Result.success(ActorSearchPage(items.toImmutableList(), response.cursor))
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    Timber.tag(TAG).e(t, "searchActors(q=%s, cursor=%s) failed", query, cursor)
                    Result.failure(t)
                }
            }
        }

        override fun getActor(did: String): Flow<ActorUi?> =
            actorDao.getActor(did).map { it?.asExternalModel() }

        /** Best-effort cache population. A cache write failure must never fail the search. */
        private suspend fun writeThrough(actors: List<ActorUi>) {
            if (actors.isEmpty()) return
            try {
                val now = Clock.System.now()
                actorDao.upsert(actors.map { it.toCacheEntity(lastSeenAt = now) })
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                Timber.tag(TAG).w(t, "actor cache write-through failed (%d rows)", actors.size)
            }
        }

        private companion object {
            const val TAG = "ActorRepo"
        }
    }

private fun ProfileView.toActorUi(): ActorUi =
    ActorUi(did = did.raw, handle = handle.raw, displayName = displayName?.takeIf { it.isNotBlank() }, avatarUrl = avatar?.raw)

private fun ProfileViewBasic.toActorUi(): ActorUi =
    ActorUi(did = did.raw, handle = handle.raw, displayName = displayName?.takeIf { it.isNotBlank() }, avatarUrl = avatar?.raw)
```

> Spec deviation (intentional): `lastSeenAt` is stamped with `kotlinx.datetime.Clock.System.now()` directly rather than an injected `Clock`. It's a cache hint needing no deterministic test (tests assert `upsert` was called, not the exact instant), and it avoids depending on a `Clock` Hilt binding that isn't established for `kotlinx.datetime.Clock`. Swap to an injected clock later if a test needs to pin it.

- [ ] **Step 4: Add the Hilt binding**

`core/actors/src/main/kotlin/net/kikin/nubecita/core/actors/internal/ActorsModule.kt`:

```kotlin
package net.kikin.nubecita.core.actors.internal

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.actors.ActorRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface ActorsModule {
    @Binds
    @Singleton
    fun bindActorRepository(impl: DefaultActorRepository): ActorRepository
}
```

- [ ] **Step 5: Run the repo tests to green**

Run: `./gradlew :core:actors:testDebugUnitTest`
Expected: all PASS.

- [ ] **Step 6: Commit**

```bash
git add core/actors/
git commit -m "feat(core/actors): DefaultActorRepository with write-through + mappers

Refs: nubecita-26a6"
```

---

## Task 4: Migrate the search People tab onto `ActorRepository`

**Files:**
- Modify: `feature/search/impl/build.gradle.kts`
- Modify: `feature/search/impl/.../SearchActorsViewModel.kt`
- Modify: `feature/search/impl/.../SearchActorsContract.kt` and any file referencing `SearchActorsPage`
- Delete: `data/SearchActorsRepository.kt`, `data/DefaultSearchActorsRepository.kt`, `di/SearchActorsRepositoryModule.kt`
- Modify: `feature/search/impl/src/test/.../data/FakeSearchActorsRepository.kt`, `feature/search/impl/src/androidTest/.../testing/FakeSearchActorsRepository.kt`

- [ ] **Step 1: Add the `:core:actors` dependency**

In `feature/search/impl/build.gradle.kts`, add (alphabetically) `implementation(project(":core:actors"))`. Leave other deps; the `ktor.client.mock` testImplementation that backed the old repo test can stay or be removed if no longer referenced.

- [ ] **Step 2: Repoint the ViewModel**

In `SearchActorsViewModel.kt`: change the import `net.kikin.nubecita.feature.search.impl.data.SearchActorsRepository` → `net.kikin.nubecita.core.actors.ActorRepository`, and the constructor param `private val repository: SearchActorsRepository` → `private val repository: ActorRepository`. The call sites `repository.searchActors(query = …, cursor = …)` are unchanged (same signature). Update the `SearchActorsPage` type reference (it now comes from `:core:actors` as `ActorSearchPage`): change imports/usages of `SearchActorsPage` → `ActorSearchPage` (fields `items`/`nextCursor` unchanged).

- [ ] **Step 3: Delete the old repo + DI**

```bash
git rm feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/SearchActorsRepository.kt \
       feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/DefaultSearchActorsRepository.kt \
       feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/di/SearchActorsRepositoryModule.kt
```

- [ ] **Step 4: Repoint the fakes**

In both `FakeSearchActorsRepository.kt` files: implement `ActorRepository` instead of `SearchActorsRepository`. Add the two extra methods so it satisfies the interface — `searchTypeahead` can `error("unused in search tests")` and `getActor` can return `flowOf(null)`; keep the existing `searchActors` fake behavior, returning `ActorSearchPage`. Update imports.

- [ ] **Step 5: Build + test the module**

Run: `./gradlew :feature:search:impl:testDebugUnitTest`
Expected: PASS (search behavior unchanged).

- [ ] **Step 6: Commit**

```bash
git add feature/search/impl/
git commit -m "refactor(search): consume :core:actors ActorRepository for People tab

Refs: nubecita-26a6"
```

---

## Task 5: Migrate the composer @-mention typeahead + delete the `:core:posting` repo

**Files:**
- Modify: `feature/composer/impl/build.gradle.kts`
- Modify: `feature/composer/impl/.../ComposerViewModel.kt`
- Modify: `core/posting/.../internal/PostingModule.kt` (drop the `@Binds`)
- Delete: `core/posting/.../ActorTypeaheadRepository.kt`, `core/posting/.../internal/DefaultActorTypeaheadRepository.kt`, posting's typeahead fake + its test (or move the test into `:core:actors`)
- Modify: composer typeahead test + its fake

- [ ] **Step 1: Add `:core:actors` to the composer**

In `feature/composer/impl/build.gradle.kts`, add `implementation(project(":core:actors"))`. Check whether `:core:posting` is still used by the composer for anything else (it almost certainly is — `PostingRepository`, `FacetExtractor`, etc.); **keep `:core:posting`**, only the typeahead type moves.

- [ ] **Step 2: Repoint the ViewModel**

In `ComposerViewModel.kt`: change import `net.kikin.nubecita.core.posting.ActorTypeaheadRepository` → `net.kikin.nubecita.core.actors.ActorRepository`; change the constructor param `private val actorTypeaheadRepository: ActorTypeaheadRepository` → `private val actorRepository: ActorRepository`; change the call `actorTypeaheadRepository.searchTypeahead(query)` → `actorRepository.searchTypeahead(query)` (same `Result<List<ActorUi>>` shape — `.fold(...)` unchanged). Rename the property references accordingly.

- [ ] **Step 3: Drop the posting binding + delete the repo**

In `PostingModule.kt`, remove the `bindActorTypeaheadRepository` `@Binds` line and the now-unused `ActorTypeaheadRepository` import. Then:

```bash
git rm core/posting/src/main/kotlin/net/kikin/nubecita/core/posting/ActorTypeaheadRepository.kt \
       core/posting/src/main/kotlin/net/kikin/nubecita/core/posting/internal/DefaultActorTypeaheadRepository.kt \
       core/posting/src/test/kotlin/net/kikin/nubecita/core/posting/internal/DefaultActorTypeaheadRepositoryTest.kt
```

(The deleted repo test's MockEngine cases are now covered by `DefaultActorRepositoryTest` in Task 3 — confirm `searchTypeahead` success/empty/failure/blank-name are represented there; add any missing case.)

- [ ] **Step 4: Repoint the composer typeahead test + fake**

In the composer test fake for actor typeahead: implement `ActorRepository` (provide `searchActors` → `error("unused")`/empty page, `getActor` → `flowOf(null)`, keep the `searchTypeahead` stub). Update `ComposerViewModelTypeaheadTest` imports/types to the new interface name.

- [ ] **Step 5: Build + test both modules**

Run: `./gradlew :feature:composer:impl:testDebugUnitTest :core:posting:testDebugUnitTest`
Expected: PASS (composer typeahead unchanged; posting no longer owns actor search).

- [ ] **Step 6: Commit**

```bash
git add feature/composer/impl/ core/posting/
git commit -m "refactor(composer,core/posting): move @-mention typeahead to :core:actors

Refs: nubecita-26a6"
```

---

## Task 6: Cross-cutting verification

- [ ] **Step 1: Full graph + checks**

Run: `./gradlew :app:assembleDebug testDebugUnitTest spotlessCheck lint :app:checkSortDependencies`
Expected: BUILD SUCCESSFUL. If `checkSortDependencies` fails, run `./gradlew :<module>:sortDependencies` for the flagged modules and re-commit.

- [ ] **Step 2: Confirm schema committed**

Run: `git status --porcelain core/database/schemas/`
Expected: clean (the `3.json` was committed in Task 2). If `3.json` is untracked, `git add` + amend Task 2's commit or add a follow-up commit.

- [ ] **Step 3: On-device regression smoke (manual)**

Build/install debug. Verify: (a) Search → People tab returns + paginates actors; (b) composer `@`-mention dropdown still suggests actors as you type. Both must behave exactly as before. (Optional sanity: `adb shell` `sqlite3` the DB / or a temporary log to confirm the `actors` table populates after a search — write-through side effect.)

- [ ] **Step 4: Final commit if any sort/format fixes were applied**

```bash
git add -A && git commit -m "chore(core/actors): formatting + dependency sort

Refs: nubecita-26a6"
```

---

## Done criteria
- `:core:actors` owns actor search (typeahead + paginated) + cache read; both consumers migrated with no behavior change.
- `actors` table created via `@AutoMigration(2→3)`, `3.json` committed.
- Old `SearchActorsRepository` (search) and `ActorTypeaheadRepository` (posting) deleted; mappers consolidated.
- All gradle checks green; search + composer typeahead verified on-device.
- PR opened with `Closes: nubecita-26a6` (via bd-workflow finish flow). PR2 (FAB + recipient picker, `b6uv.5`/`b6uv.6`) builds on this.
