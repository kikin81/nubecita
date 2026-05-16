# Posts + Actors search data layers — design

**Date:** 2026-05-16
**Scope:** `nubecita-vrba.3` (Posts search data layer) + `nubecita-vrba.4` (Actors search data layer). Both ship as feature-internal repositories inside `:feature:search:impl/data/`. `vrba.4` also promotes the existing `ActorTypeaheadUi` from `:core:posting` to `:data:models` as `ActorUi`.
**Status:** Draft for user review.

## Why a shared spec

The two repositories are mechanically similar — both wrap an atproto-kotlin RPC, both produce a cursor-paged `Result<Page>`, both inject `XrpcClientProvider` + `@IoDispatcher`, both follow the `FeedRepository` shape. The shared design lives here; the per-PR boundary (PR 1 = `vrba.3` Posts, PR 2 = `vrba.4` Actors + the actor-model promotion) is captured in the matching implementation plan.

## Decisions

### D1. Repositories live in `:feature:search:impl` (not a new `:core:search`)

Default per the `vrba.2` precedent: only Search consumes Posts-search and Actors-search results today. If a future feature (e.g. Profile suggestions, Discover) needs the same RPCs, promote then. Adding `:core:search` now would be speculative.

### D2. Return type: `Result<SearchXxxPage>` using Kotlin's stdlib `Result<T>`

Matches `FeedRepository.getTimeline(...): Result<TimelinePage>`. Throwables surface to the VM; VM-side `Throwable.toSearchXxxError()` extension functions in `vrba.6` / `vrba.7` map them to typed UI errors (`SearchPostsError.Network / RateLimited / Unknown` style). The repo boundary does **not** introduce a `DataError` sealed type — that's deliberately VM-side per project convention.

### D3. Cursor-based paging, repo-stateless

Caller passes `cursor: String?` (null = first page); page returns `nextCursor: String?` (null = end-of-results). The repo holds no state — the VM (vrba.6 / vrba.7) owns the cursor and re-issues calls. This matches the `FeedRepository` contract exactly.

### D4. Page limit: 25 per request

Slightly lower than `TIMELINE_PAGE_LIMIT = 30` because search results are typically scanned, not deeply scrolled. The Bluesky `searchPosts` and `searchActors` lexicons accept 1–100; 25 is a sensible default that we can tune if engagement signals say otherwise.

### D5. Posts results use `FeedItemUi.Single`

`searchPosts` returns a flat list of `PostView` (no thread reconstruction). `FeedItemUi.Single` is the existing flat-post variant the feed already renders. Reusing it lets the Posts tab in `vrba.6` use the same post-card composables the feed module already exercises — zero new rendering work.

Trade-off: `Single` is named within the feed's three-variant union (`Single / ReplyCluster / SelfThreadChain`). It's grammatically a little awkward outside that context, but the renaming cost outweighs the benefit. We leave it as-is.

### D6. Actors results use `ActorUi` (promoted from `:core:posting`'s `ActorTypeaheadUi`)

The existing `ActorTypeaheadUi(did, handle, displayName: String?, avatarUrl: String?)` in `:core:posting` is exactly the actor-row shape Search needs. The promotion:

1. **New file:** `data/models/src/main/kotlin/net/kikin/nubecita/data/models/ActorUi.kt` with the same fields.
2. **Delete:** `core/posting/src/main/kotlin/net/kikin/nubecita/core/posting/ActorTypeaheadUi.kt`.
3. **Update references** in `:core:posting` (`ActorTypeaheadRepository`, `DefaultActorTypeaheadRepository`, related tests) and `:feature:composer:impl` (VM, contract, composables, tests) via mechanical find-and-replace.

Naming: `ActorUi` drops the misleading `Typeahead` suffix, signaling the type is now broadly applicable to any actor-row context. Composer's typeahead still works because the **repository** there is still `ActorTypeaheadRepository` — only the model that flows through it gets renamed.

This refactor lands inside the `vrba.4` PR (not as a separate prep PR) because it's mechanical and bounded to ~10 references.

### D7. Dispatcher + logging match `DefaultFeedRepository`

`withContext(@IoDispatcher dispatcher) { runCatching { ... }.onFailure { Timber.tag(TAG).e(throwable, "...failed: %s", throwable.javaClass.name) } }`. The `javaClass.name` log on failure preserves the diagnostic identity the project's existing error-mapping relies on.

### D8. Mappers reuse existing project infrastructure

- **Posts:** the existing post-view mapping lives in `:core:feed-mapping`. The mapper used by `DefaultFeedRepository` to produce `FeedItemUi.Single` from a `PostView` should be reused (or a sibling `PostView.toFeedItemUiSingle()` exposed if the existing one is feed-shaped only). The implementer verifies the surface and reuses what's there; no new mapping logic if avoidable.
- **Actors:** a new `ProfileView.toActorUi()` extension in `:feature:search:impl/data/`, mirroring the existing typeahead repository's mapper shape. Single source of truth for the `ProfileView` → `ActorUi` conversion is per-consumer for now (composer's typeahead has its own); promote to `:core:posting` or `:data:models` if a third consumer appears.

### D9. Tests use hand-written fakes, plain JVM, `:core:testing` deps

Mirror `vrba.2` and `vrba.5` test conventions: hand-written `FakeXrpcClient` / `FakeFeedService` / `FakeActorService` (whichever the existing project already exposes — implementer verifies via `:feature:composer:impl`'s typeahead test setup), not mockk. Plain `runTest { ... }` with `kotlinx.coroutines.test`. No Robolectric, no `connectedDebugAndroidTest`.

## Posts data layer (`vrba.3`)

### Files

**New:**

- `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/SearchPostsRepository.kt`:
  - `internal interface SearchPostsRepository` with `suspend fun searchPosts(query: String, cursor: String?, limit: Int = SEARCH_POSTS_PAGE_LIMIT): Result<SearchPostsPage>`.
  - `internal data class SearchPostsPage(val items: ImmutableList<FeedItemUi.Single>, val nextCursor: String?)`.
  - `internal const val SEARCH_POSTS_PAGE_LIMIT: Int = 25`.
- `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/DefaultSearchPostsRepository.kt`:
  - `@Inject constructor(private val xrpcClientProvider: XrpcClientProvider, @param:IoDispatcher private val dispatcher: CoroutineDispatcher)`.
  - Body: `withContext(dispatcher) { runCatching { FeedService(client).searchPosts(SearchPostsRequest(q = query, cursor = cursor, limit = limit.toLong())).let { response -> SearchPostsPage(response.posts.map { it.toFeedItemUiSingle() }.toImmutableList(), response.cursor) } }.onFailure { Timber.tag(TAG).e(it, ...) } }`.
- `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/di/SearchPostsRepositoryModule.kt` — `@Module @InstallIn(SingletonComponent::class) abstract class ... { @Binds internal abstract fun bindsSearchPostsRepository(impl: DefaultSearchPostsRepository): SearchPostsRepository }`.
- `feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/data/SearchPostsRepositoryTest.kt`:
  - Cases: `searchPosts_happyPath_mapsPostsAndCursor`, `searchPosts_emptyResult_returnsEmptyPage`, `searchPosts_throws_surfacesFailure`, `searchPosts_passesCursorAndLimitThrough`.

**Modified:**

- `feature/search/impl/build.gradle.kts`: add `implementation(project(":core:auth"))`, `implementation(project(":core:feed-mapping"))`, `implementation(libs.atproto.runtime)`, `implementation(libs.atproto.models)`.

### Mapping path

`SearchPostsResponse.posts: List<PostView>` → for each entry, project via the existing `:core:feed-mapping` post-view → UI helper. If the existing helper produces a `FeedItemUi.Single` directly, reuse it. If it only produces `FeedItemUi` (the sealed union), wrap the result in a check that confirms `Single` and either filters non-Single variants out (unexpected for searchPosts since it doesn't return reply clusters) or returns the union and lets the caller flat-handle. **Implementer verifies the existing helper shape in `:core:feed-mapping` before deciding.**

## Actors data layer (`vrba.4`)

### Files

**`ActorUi` promotion (lands first in the same commit as the Actors repo, or as a preceding commit on the same branch):**

- **New:** `data/models/src/main/kotlin/net/kikin/nubecita/data/models/ActorUi.kt`:
  ```kotlin
  package net.kikin.nubecita.data.models

  import androidx.compose.runtime.Immutable

  @Immutable
  public data class ActorUi(
      val did: String,
      val handle: String,
      val displayName: String?,
      val avatarUrl: String?,
  )
  ```
- **Deleted:** `core/posting/src/main/kotlin/net/kikin/nubecita/core/posting/ActorTypeaheadUi.kt`.
- **Modified (mechanical find-and-replace, `ActorTypeaheadUi` → `ActorUi`):**
  - `core/posting/src/main/kotlin/net/kikin/nubecita/core/posting/ActorTypeaheadRepository.kt`.
  - `core/posting/src/main/kotlin/net/kikin/nubecita/core/posting/internal/DefaultActorTypeaheadRepository.kt`.
  - `core/posting/src/test/kotlin/net/kikin/nubecita/core/posting/internal/DefaultActorTypeaheadRepositoryTest.kt`.
  - `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerViewModel.kt`.
  - `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/state/*.kt` — wherever `ActorTypeaheadUi` is referenced.
  - `feature/composer/impl/src/test/kotlin/net/kikin/nubecita/feature/composer/impl/*Test.kt` — any test fixture or assertion site.

  The implementer runs a project-wide grep for `ActorTypeaheadUi`, applies the rename, then verifies all referencing source sets compile + their tests still pass.

  No build-graph changes — `:core:posting` already depends on `:data:models` transitively (or directly).

**Actors search repo (new):**

- `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/SearchActorsRepository.kt`:
  - `internal interface SearchActorsRepository` with `suspend fun searchActors(query: String, cursor: String?, limit: Int = SEARCH_ACTORS_PAGE_LIMIT): Result<SearchActorsPage>`.
  - `internal data class SearchActorsPage(val items: ImmutableList<ActorUi>, val nextCursor: String?)`.
  - `internal const val SEARCH_ACTORS_PAGE_LIMIT: Int = 25`.
- `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/DefaultSearchActorsRepository.kt`:
  - Same shape as Posts repo. Calls `ActorService(client).searchActors(SearchActorsRequest(q = query, cursor = cursor, limit = limit.toLong()))`. Maps `response.actors: List<ProfileView>` to `List<ActorUi>` via a new `ProfileView.toActorUi()` extension co-located in the same package.
- `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/di/SearchActorsRepositoryModule.kt` — `@Binds` wiring.
- `feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/data/SearchActorsRepositoryTest.kt` — same case shape as Posts.

**Modified:**

- `feature/search/impl/build.gradle.kts`: no new deps beyond what vrba.3 already added (still `:core:auth`, atproto runtime/models). `:core:feed-mapping` was for Posts only; Actors doesn't need it.

## Testing strategy

Per repo:

| Case | Assertion |
|---|---|
| `searchXxx_happyPath_mapsResultsAndCursor` | Given a fake service returning N entries + cursor "c2", repo returns a `SearchXxxPage` with N mapped items + `nextCursor = "c2"`. |
| `searchXxx_emptyResult_returnsEmptyPage` | Given a fake service returning empty list + null cursor, repo returns empty page + null cursor. |
| `searchXxx_throws_surfacesFailure` | Given a fake service throwing, repo returns `Result.failure(throwable)` with the same throwable identity. |
| `searchXxx_passesCursorAndLimitThrough` | Asserts the constructed `SearchXxxRequest` carries the caller's cursor + limit unchanged. |

For Actors specifically, plus the promotion verification:

- `:feature:composer:impl:testDebugUnitTest` — re-run; must still be 100% green after the rename.
- `:core:posting:testDebugUnitTest` — re-run; same.

## Risk + rollback

- **Risk: existing `:core:feed-mapping` post-view helper isn't shaped to produce `FeedItemUi.Single` directly.** If the helper only produces the sealed union, we'd need a tiny wrapper. The implementer reports as `DONE_WITH_CONCERNS` if forced to deviate; the wrapper stays bounded to `DefaultSearchPostsRepository`.
- **Risk: `ActorTypeaheadUi` is referenced in more places than the search reveals.** Mechanical find-and-replace catches direct references; instances behind `*` imports require a build pass to expose. The plan's verification step runs every relevant module's unit tests, which fails fast on broken references.
- **Risk: `searchPosts` / `searchActors` rate limits.** Out of scope at the repo layer — the VMs (`vrba.6` / `vrba.7`) will debounce and single-flight per their MVI design. The repo just translates the call.
- **Rollback:** each PR is independently revertible. `vrba.4`'s rename is the only cross-module touch; reverting that PR also reverts the rename.

## Out of scope

- **VM-side error mapping.** `Throwable.toSearchPostsError()` / `Throwable.toSearchActorsError()` land with the consuming VMs (`vrba.6` / `vrba.7`).
- **Trending / Discover / tagged-search RPCs.** Epic non-goals.
- **Result caching.** Each query is a fresh RPC. The recent-search chip strip is the only "history" affordance.
- **`:core:search` module extraction.** Deferred until a second feature needs the same RPCs.
- **`ProfileView.toActorUi()` promotion to `:data:models`.** Out of scope — composer's typeahead already has its own mapper; consolidating to a single mapper is a separate small refactor if/when justified.

## PR structure

Two PRs, one per bd child, in order:

1. **PR 1 — `feat(feature/search/impl): Posts search data layer`** (`nubecita-vrba.3`). Single commit. No model changes. Depends only on merged `main` (which already has vrba.5).

2. **PR 2 — `feat(data/models,feature/search/impl): Actors search + promote ActorUi`** (`nubecita-vrba.4`). Two commits inside the branch (squash-merges to one):
   - `refactor(data/models,core/posting,feature/composer/impl): promote ActorTypeaheadUi to :data:models as ActorUi`.
   - `feat(feature/search/impl): Actors search data layer`.

   Cut from `main` after PR 1 merges so the branch base is fresh.

## Open questions

None outstanding. Ready for review.
