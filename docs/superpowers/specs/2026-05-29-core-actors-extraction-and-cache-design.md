# `:core:actors` — shared actor search + DID-keyed Room cache

- **bd:** `nubecita-26a6` (PR1) · enables `nubecita-b6uv.6` (recipient picker, PR2)
- **Date:** 2026-05-29
- **Status:** Approved (design); pending implementation plan

## Summary

Consolidate the project's two divergent actor-search code paths into a new
`:core:actors` capability, and add a DID-keyed Room cache so actor display data
(handle / display name / avatar) can be served instantly. This is **PR1** of the
bundled `b6uv.5` + `b6uv.6` work; **PR2** (New-Chat FAB + recipient picker) builds
on it and consumes the cache read-side.

PR1 has **no user-facing change** — the only new behavior is a quiet write-through
that populates the cache whenever an actor search runs.

## Motivation

Actor search currently has two owners with divergent shapes:

| Owner | Module | XRPC | Shape |
|---|---|---|---|
| `SearchActorsRepository` | `:feature:search:impl` (internal) | `app.bsky.actor.searchActors` | paginated (`cursor` → `SearchActorsPage`) |
| `ActorTypeaheadRepository` | `:core:posting` | `app.bsky.actor.searchActorsTypeahead` | single-shot `List<ActorUi>` |

The chats recipient picker (`b6uv.6`) would be a **third** consumer, crossing the
project's "extract a shared helper at ≥3 uses" threshold. `DefaultSearchActorsRepository`
already carries a TODO to "promote the `ProfileView.toActorUi()` mapper if a third
consumer appears." This change does that promotion and unifies the two paths, while
adding the cache the picker (and future surfaces) will read.

## Caching policy

- **DID-keyed content cache**, not query→results. Upsert individual actors whenever
  seen; never cache search result sets keyed by query string.
- **Always network-refreshed.** The picker / typeahead always shows fresh network
  results; the cache is a display-hint accelerator only. Rationale: if a user blocks
  or unfollows an actor, a stale query-result cache could resurface them — a
  content cache that's always overwritten by the live response avoids that.
- **No TTL.** Rows are overwritten on every sighting via write-through.
- `lastSeenAt: Instant` is stored to power PR2's "recent" ordering and any future
  eviction cap; it is not used for invalidation.

## Architecture

### `:core:database` — owns the schema (single Room DB)

Per the project convention, Room entities/DAOs/migrations live only in `:core:database`,
and feature modules never depend on it directly — they go through `:core:<domain>`.

`model/ActorEntity.kt`:

```kotlin
@Entity(tableName = "actors")
internal data class ActorEntity(
    @PrimaryKey val did: String,
    val handle: String,
    val displayName: String?,   // mirrors ActorUi's nullable contract
    val avatarUrl: String?,
    val lastSeenAt: Instant,    // stamped by the repo via injected Clock; uses existing InstantConverter
)

internal fun ActorEntity.asExternalModel(): ActorUi =
    ActorUi(did = did, handle = handle, displayName = displayName, avatarUrl = avatarUrl)
```

`dao/ActorDao.kt`:

```kotlin
@Dao
internal interface ActorDao {
    @Upsert
    suspend fun upsert(actors: List<ActorEntity>)

    @Query("SELECT * FROM actors WHERE did = :did")
    fun getActor(did: String): Flow<ActorEntity?>
}
```

(A prefix-search query — `WHERE handle LIKE … OR displayName LIKE … ORDER BY lastSeenAt DESC`
— is deferred to PR2, where the picker's read-side consumes it.)

`NubecitaDatabase`: bump `version` 2 → **3**, add `ActorEntity::class` to `entities`,
add `abstract fun actorDao(): ActorDao`, and append the additive auto-migration —
**keeping the existing v1→v2 spec**:

```kotlin
autoMigrations = [
    AutoMigration(from = 1, to = 2, spec = BootstrapEntityDrop::class),
    AutoMigration(from = 2, to = 3),  // new "actors" table — purely additive, no spec needed
]
```

Build with `exportSchema = true` and **commit the generated
`core/database/schemas/net.kikin.nubecita.core.database.NubecitaDatabase/3.json`**.
Provide `ActorDao` in `DaosModule`.

### `:core:actors` — the actor capability (network + cache facade)

New module. Plugins: `nubecita.android.library` + `nubecita.android.hilt`. Deps:
`:core:auth` (`XrpcClientProvider`), `:core:database` (`ActorDao`), `:core:common`
(`@IoDispatcher`, `Clock`), `:data:models` (`ActorUi`). It does **not** apply the
room plugin — it consumes the DAO, it doesn't own entities.

```kotlin
interface ActorRepository {
    /** Fast as-you-type — app.bsky.actor.searchActorsTypeahead (ProfileViewBasic). Single-shot. Write-through. */
    suspend fun searchTypeahead(query: String, limit: Int = 8): Result<List<ActorUi>>

    /** Full paginated — app.bsky.actor.searchActors (ProfileView). Write-through. */
    suspend fun searchActors(query: String, cursor: String?, limit: Int = 25): Result<ActorSearchPage>

    /** Observe a single cached actor by DID. Cache read — consumed in PR2. */
    fun getActor(did: String): Flow<ActorUi?>
}

data class ActorSearchPage(
    val items: ImmutableList<ActorUi>,
    val nextCursor: String?,
)
```

`DefaultActorRepository` (internal, `@Singleton`) injects `XrpcClientProvider`,
`ActorDao`, `@param:IoDispatcher dispatcher`, and `Clock`. Both search methods mirror
`DefaultSearchActorsRepository` precisely:

- `require(limit in 1..100)` up-front guard.
- `withContext(dispatcher) { try { … } catch (c: CancellationException) { throw c } catch (t: Throwable) { Timber…; Result.failure(t) } }` — **cancellation-aware** (rethrow CE before the catch-all).
- XRPC via `ActorService(xrpcClientProvider.authenticated()).searchActors(...)` / `.searchActorsTypeahead(...)`.
- **Write-through:** on success, `dao.upsert(items.map { it.toEntity(lastSeenAt = clock.now()) })` before returning the `Result`. The upsert failing must not fail the search (it's a cache side effect) — wrap it so a cache write error is logged, not propagated.
- Returns `Result<…>` with **no error UX baked in** — each consumer keeps its own mapping.

Mappers consolidated here (replacing the two co-located copies), preserving the
blank-`displayName` → `null` normalization:

```kotlin
internal fun ProfileView.toActorUi(): ActorUi = …       // searchActors
internal fun ProfileViewBasic.toActorUi(): ActorUi = …  // searchActorsTypeahead
```

Hilt module binds `ActorRepository` → `DefaultActorRepository`.

### Consumer migration (drop-in — signatures preserved)

- **`:feature:search:impl`** — `SearchActorsViewModel` injects `ActorRepository` and
  calls `searchActors(query, cursor, limit)` (returns the same `ActorSearchPage` shape;
  `SearchActorsError` mapping stays at the VM). Delete `SearchActorsRepository`,
  `DefaultSearchActorsRepository`, `SearchActorsPage`, their DI module, and
  `FakeSearchActorsRepository` (repoint search tests to a fake `ActorRepository`).
- **`:feature:composer:impl`** — `ComposerViewModel` injects `ActorRepository` and calls
  `searchTypeahead(query)`. Delete `:core:posting`'s `ActorTypeaheadRepository`,
  `DefaultActorTypeaheadRepository`, their DI, and fake; repoint composer typeahead tests.

`SearchActorsPage` becomes `ActorSearchPage` (moved + renamed); the VM's page-handling
logic is otherwise unchanged.

## Error & cancellation model

- All repo methods return `Result<…>`; `CancellationException` always rethrown.
- Search People tab keeps its typed `SearchActorsError` sealed mapping at the VM.
- Composer typeahead keeps "collapse any failure to a hidden dropdown."
- Cache write-through errors are swallowed-with-log (never surface to the user or fail
  the network result).

## Testing

- **`:core:actors` repo unit tests:** `searchTypeahead` + `searchActors` — success,
  empty-list success, failure, mapper correctness (incl. blank → null), and
  `CancellationException` re-throw. Assert write-through upserts on success and that a
  cache-write failure does not fail the search.
- **`ActorDao` test:** `upsert` + `getActor` Flow emission. Use the same harness the
  existing `RecentSearchDao` test uses (instrumented or Robolectric — match precedent).
- **Consumer VM tests:** keep `SearchActorsViewModel` / composer typeahead tests,
  repointed at a fake `ActorRepository`.
- **Schema:** confirm `3.json` is generated and committed; schema validation passes.
- **Regression gate (no behavior change):** `./gradlew :app:assembleDebug
  testDebugUnitTest spotlessCheck lint :app:checkSortDependencies`, plus an on-device
  smoke of the search People tab and the composer `@`-mention typeahead.

## Out of scope (→ PR2 or later)

- Read-side cache consumption (picker "recent" list, prefix search query, typeahead
  seeding from cache).
- New-Chat FAB, `NewChat` NavKey, recipient picker screen (`b6uv.5` / `b6uv.6`).
- Eviction / row-count cap.
- Caching actors seen from non-search surfaces (convos, profiles) — possible later
  write-through points, not in this PR.
