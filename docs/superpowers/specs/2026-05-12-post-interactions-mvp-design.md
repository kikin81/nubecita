# Post interactions MVP — design

**Epic:** `nubecita-8f6` (Post interactions: like, repost, reply, share)
**Scope:** First PR of the epic. Promotes the existing `LikeRepostRepository`, introduces a shared `PostInteractionsCache` with cross-screen sync, wires Feed (refactor) + PostDetail (new) + Profile (new).
**Out of scope (filed as follow-up bd issues):** repost-vs-Quote bottomsheet, Quote composer routing, reply launch from PostCard icon, share intent, Room graduation for cross-session persistence.

## Why

Two problems with the current state:

1. **Cross-screen consistency bug.** `FeedViewModel` owns its own optimistic like/repost state. If the user likes a post on PostDetail (which is currently stubbed `onLike = {}`), back-navigates to Feed, the Feed still shows the post as unliked because Feed's VM never observed PostDetail's mutation. Symmetric problem with Profile. The bug compounds because PostDetail and Profile don't even fire interaction events yet — wiring them naively would mean three independent optimistic-state implementations, three independent rollback handlers, three places to fix any future bug.
2. **No shared write surface.** `LikeRepostRepository` is `internal` to `:feature:feed:impl`. Its KDoc literally says: "If a second consumer (post detail, notifications) later needs the same write path, the change that adds the consumer also promotes this interface to a new `:core` module." That moment is now.

The fix is a **cache + broadcast pattern** at the repository layer. A single `@Singleton` cache owns all optimistic state for posts the user has interacted with this session. Every VM subscribes to the cache's `StateFlow` and merges its own list against the cache at consumption time. A like on PostDetail mutates the cache; Feed's subscriber fires immediately; the Feed screen re-renders with the new state.

This document specifies the MVP of that architecture: in-memory cache, all three current consumers (Feed, PostDetail, Profile) wired, cross-screen sync verified end-to-end.

## Architecture

### Module layout

```
:core/post-interactions/                 # NEW module
├── src/main/kotlin/.../
│   ├── PostInteractionsCache.kt              # public interface
│   ├── PostInteractionState.kt               # data class + PendingState enum
│   ├── LikeRepostRepository.kt               # MOVE from :feature:feed:impl
│   ├── internal/
│   │   ├── DefaultPostInteractionsCache.kt   # @Singleton impl
│   │   └── DefaultLikeRepostRepository.kt    # MOVE (impl unchanged)
│   └── di/
│       └── PostInteractionsModule.kt         # Hilt bindings
└── src/test/kotlin/.../
    └── DefaultPostInteractionsCacheTest.kt   # 11 scenarios

:feature:feed:impl/                      # MODIFY
├── src/main/kotlin/.../
│   ├── data/
│   │   ├── LikeRepostRepository.kt           # DELETE (moved)
│   │   └── DefaultLikeRepostRepository.kt    # DELETE (moved)
│   ├── di/LikeRepostRepositoryModule.kt      # DELETE (moved)
│   └── FeedViewModel.kt                      # MODIFY: drop toggleLike/toggleRepost (~150 lines)
├── build.gradle.kts                          # MODIFY: + :core/post-interactions
└── src/test/kotlin/.../
    └── FeedViewModelTest.kt                  # MODIFY: FakeLikeRepostRepo → FakePostInteractionsCache

:feature:postdetail:impl/                # MODIFY (new wiring)
├── src/main/kotlin/.../
│   ├── PostDetailViewModel.kt                # MODIFY: + cache subscribe, dispatch + error route
│   ├── PostDetailContract.kt                 # MODIFY: + OnLikeClicked / OnRepostClicked events
│   └── PostDetailScreen.kt                   # MODIFY: PostCallbacks(onLike, onRepost) wired
├── build.gradle.kts                          # MODIFY: + :core/post-interactions
└── src/test/kotlin/.../
    └── PostDetailViewModelTest.kt            # MODIFY: + 3-4 new tests

:feature:profile:impl/                   # MODIFY (new wiring)
├── src/main/kotlin/.../
│   ├── ProfileViewModel.kt                   # MODIFY: + cache subscribe, dispatch + error route
│   ├── ProfileContract.kt                    # MODIFY: + OnLikeClicked / OnRepostClicked events
│   └── ProfileScreen.kt                      # MODIFY: PostCallbacks no-op stubs → real wiring
├── build.gradle.kts                          # MODIFY: + :core/post-interactions
└── src/test/kotlin/.../
    └── ProfileViewModelTest.kt               # MODIFY: + 4-5 new tests

:core/auth/                              # MODIFY
└── src/main/kotlin/.../DefaultAuthRepository.kt   # MODIFY: cache.clear() before revocation
```

### Cross-cutting changes

Two new things in this PR:

1. `:core/post-interactions` module — the cache + broadcast layer + promoted `LikeRepostRepository`.
2. PostDetail + Profile gain real like/repost wiring (previously stubbed).

## Decisions

### Decision 1: Cache + broadcast pattern, not per-VM optimistic state

A pure per-VM optimistic-flip approach (what FeedVM does today) doesn't solve cross-screen sync. A pure `SharedFlow<PostInteraction>` (Gemini's first sketch) solves sync but misses late subscribers, refresh-during-in-flight, and queryability. The hybrid — a `StateFlow<PersistentMap<String, PostInteractionState>>` cache that emits on every mutation — gives us:

- Cross-screen sync (every emission reaches every live subscriber)
- Late-subscriber correctness (`cache.value` is queryable; a newly-mounted VM reads the current truth)
- Refresh-during-in-flight correctness (via the seed-merger rule, see Decision 2)
- Single-flight per-postUri (via an internal `pendingLikeWrite` flag on `State`)
- Tracking of the user's like-record AtUri (needed by `deleteRecord` to unlike)

The cache becomes the canonical source of truth for "is post X liked/reposted right now"; VMs are pure projections.

**Why this over keeping FeedVM's existing ~150-line optimistic logic + adding broadcast on top:** Profile and PostDetail would each have to replicate the 150 lines. Three independent implementations of the same logic is the bug surface this design eliminates.

### Decision 2: `seed()` preserves in-flight optimistic state

Atproto's appview is eventually consistent — a `createRecord` for `app.bsky.feed.like` is committed instantly to the user's PDS, but the appview's denormalized `viewer.like` field may lag by seconds to minutes. A naive `seed()` that always reseeds from wire data would un-flip the heart while the user is staring at the screen waiting for the like to commit.

**Merger rule:**

- If `cache[postUri].pendingLikeWrite == Pending` (an in-flight like/unlike network call): preserve the cache state entirely, ignore wire data for this post.
- Else if `cache[postUri].viewerLikeUri != null` and wire's `viewer.like == null`: preserve the cache's `viewerLikeUri` and `likeCount` — assume wire is stale.
- Else: seed from wire (`likeCount`, `viewerLikeUri`, etc.).

Same rule for repost. Counts always trust the cache when `pendingLikeWrite` is in any state; once the network call resolves (success or failure), `pendingLikeWrite` clears and the next `seed()` accepts wire data normally — by which time the appview has typically caught up.

### Decision 3: `Result<Unit>` from `toggleLike`/`toggleRepost`, caller routes errors

The cache mutates state internally on failure (rollback) so cross-screen state stays consistent. But the snackbar/error UI belongs to the SCREEN that initiated the action. Returning `Result.failure(throwable)` from `toggleLike` lets the calling VM route to its own `FooEffect.ShowError` channel — preserves the existing per-screen error pattern, doesn't centralize error UI.

**Why not a separate `errors: SharedFlow<InteractionError>` on the cache:** every VM would subscribe and all would surface the error (e.g., PostDetail and Feed both show the snackbar when the user is on Feed). The "active screen owns the snackbar" semantic is lost. Caller-routed is correct.

### Decision 4: Sign-out clears the cache via injected dependency

`DefaultAuthRepository.signOut()` gains an injected `PostInteractionsCache` and calls `cache.clear()` before revocation. Two lines. Simpler than the cache subscribing to `SessionState` transitions, and the dependency direction is sensible (auth owns the sign-out lifecycle; cache is a downstream client of session state).

### Decision 5: `:core/post-interactions`, not `:core/posts` expansion or `:core/feed`

- `:core/posts` currently does `getPost(uri)` only — adding interactions would blur the read/write boundary.
- `:core/feed` (per the existing KDoc note in `LikeRepostRepository`) misnames the scope — like/repost apply to any PostCard, not feed-specifically.
- `:core/post-interactions` cleanly scopes "things the user does TO posts" — like, repost, and future moderation actions (block, mute, report) fit naturally.

### Decision 6: PENDING sentinel string for in-flight `viewerLikeUri`

While a like is in flight, the cache writes `viewerLikeUri = "pending:optimistic"` (a constant). The `mergeInteractionState` extension that projects cache state onto `PostUi` strips this to `null` on the way out (the boolean `isLikedByViewer = viewerLikeUri != null` still computes `true`, so the heart shows liked). No downstream consumer ever sees the sentinel — the constant is `internal` to `:core/post-interactions/internal/`.

**Why not a separate `inFlightUris: Set<String>` field on the cache:** putting the in-flight bit on `State` means VMs that subscribe to `cache.state` see in-flight info via the same emission. A separate Set would require a second subscription. Same shape, less wiring.

## Components

### `PostInteractionsCache` (public interface)

```kotlin
interface PostInteractionsCache {
    /** Canonical state. Emits on every mutation. */
    val state: StateFlow<PersistentMap<String, PostInteractionState>>

    /** Seed/refresh from freshly-loaded wire posts. Preserves in-flight state. */
    fun seed(posts: List<PostUi>)

    /**
     * Toggle like. Single-flight per [postUri]; double-tap is no-op.
     * Returns Result.failure on network failure so caller can route error.
     */
    suspend fun toggleLike(postUri: String, postCid: String): Result<Unit>

    /** Symmetric for repost. */
    suspend fun toggleRepost(postUri: String, postCid: String): Result<Unit>

    /** Clear the cache. Called on sign-out. */
    fun clear()
}
```

### `PostInteractionState`

```kotlin
data class PostInteractionState(
    val viewerLikeUri: String? = null,
    val viewerRepostUri: String? = null,
    val likeCount: Long = 0,
    val repostCount: Long = 0,
    val pendingLikeWrite: PendingState = PendingState.None,
    val pendingRepostWrite: PendingState = PendingState.None,
)

enum class PendingState { None, Pending }
```

### `LikeRepostRepository` (promoted, unchanged)

```kotlin
interface LikeRepostRepository {
    suspend fun like(post: StrongRef): Result<AtUri>
    suspend fun unlike(likeUri: AtUri): Result<Unit>
    suspend fun repost(post: StrongRef): Result<AtUri>
    suspend fun unrepost(repostUri: AtUri): Result<Unit>
}
```

Visibility goes from `internal` to `public` (cross-module callers). Implementation logic is unchanged from the current `:feature:feed:impl/data/` version.

### `DefaultPostInteractionsCache` (impl, internal to module)

Wraps `LikeRepostRepository`. Owns:

- `private val _cache = MutableStateFlow<PersistentMap<String, PostInteractionState>>(persistentMapOf())`
- `private val likeJobs = mutableMapOf<String, Job>()` — single-flight per-postUri guard
- `private val repostJobs = mutableMapOf<String, Job>()` — same for repost
- `@ApplicationScope` `CoroutineScope` injection — write coroutines survive screen unmount

`toggleLike(postUri, postCid)` algorithm:

1. If `likeJobs[postUri]?.isActive == true`, return `Result.success(Unit)` (synthetic; the in-flight call wins). Single-flight.
2. Read `before = _cache.value[postUri] ?: PostInteractionState()`.
3. Compute optimistic state: flip `viewerLikeUri` (null ↔ PENDING_SENTINEL), adjust `likeCount` ±1 (coerced ≥ 0), set `pendingLikeWrite = Pending`.
4. `_cache.update { it.put(postUri, optimistic) }` → emits.
5. Launch in `@ApplicationScope`:
   ```
   try {
       val result = if (before.viewerLikeUri == null) {
           repo.like(StrongRef(uri = AtUri(postUri), cid = Cid(postCid))).getOrThrow().raw
       } else {
           repo.unlike(AtUri(before.viewerLikeUri)).getOrThrow(); null
       }
       _cache.update { it.put(postUri, optimistic.copy(viewerLikeUri = result, pendingLikeWrite = None)) }
       Result.success(Unit)
   } catch (t: Throwable) {
       _cache.update { it.put(postUri, before) }
       Result.failure(t)
   } finally {
       likeJobs.remove(postUri)
   }
   ```
6. Caller awaits the launched Job's `await()` for the `Result`.

Symmetric for `toggleRepost`. `clear()` is `_cache.update { persistentMapOf() }`.

`seed(posts: List<PostUi>)` merger:

```kotlin
_cache.update { current ->
    posts.fold(current) { acc, post ->
        val existing = acc[post.id]
        val merged = when {
            existing?.pendingLikeWrite == Pending || existing?.pendingRepostWrite == Pending -> existing
            existing != null && existing.viewerLikeUri != null && post.viewer.likeUri == null -> existing
            else -> PostInteractionState(
                viewerLikeUri = post.viewer.likeUri,
                viewerRepostUri = post.viewer.repostUri,
                likeCount = post.stats.likeCount.toLong(),
                repostCount = post.stats.repostCount.toLong(),
            )
        }
        acc.put(post.id, merged)
    }
}
```

### VM consumption pattern

Each VM in `init`:

```kotlin
viewModelScope.launch {
    cache.state
        .map { interactionMap -> currentItems().applyInteractions(interactionMap) }
        .distinctUntilChanged()
        .collect { merged -> setState { copy(/* items = merged */) } }
}
```

Plus event handlers:

```kotlin
is OnLikeClicked -> viewModelScope.launch {
    cache.toggleLike(event.post.id, event.post.cid)
        .onFailure { sendEffect(FooEffect.ShowError(it.toFooError())) }
}
```

Plus `seed()` call after every wire fetch (initial load, refresh, pagination):

```kotlin
val page = repo.getTimeline(...)
cache.seed(page.posts)   // canonical state updated; subscribers re-project
setState { copy(items = page.posts.toImmutableList()) }
```

### `PostUi.mergeInteractionState` extension

```kotlin
// In :data:models alongside PostUi
fun PostUi.mergeInteractionState(s: PostInteractionState): PostUi =
    copy(
        viewer = viewer.copy(
            isLikedByViewer = s.viewerLikeUri != null,
            likeUri = s.viewerLikeUri?.takeIf { it != PENDING_LIKE_SENTINEL },
            isRepostedByViewer = s.viewerRepostUri != null,
            repostUri = s.viewerRepostUri?.takeIf { it != PENDING_REPOST_SENTINEL },
        ),
        stats = stats.copy(
            likeCount = s.likeCount.toInt(),
            repostCount = s.repostCount.toInt(),
        ),
    )
```

The `PENDING_*_SENTINEL` constants live in `:core/post-interactions/internal/` and are exposed via a package-private `const` (or as part of the `PostInteractionState` companion). The `mergeInteractionState` extension lives in `:data:models` so all consumers can use it; `:data:models` gains a thin dep on `:core/post-interactions` for this constant + the `PostInteractionState` type.

## Data flow

### Like flow (success path)

1. User taps heart on a PostCard in Feed.
2. PostCard's `PostCallbacks.onLike(post)` fires.
3. FeedScreen wraps it as `FeedEvent.OnLikeClicked(post)` → dispatches to VM.
4. `FeedViewModel.handleEvent`: `viewModelScope.launch { cache.toggleLike(post.id, post.cid).onFailure { ... } }`.
5. Cache reads current state, computes optimistic, `_cache.update { put(optimistic) }`. Emits.
6. All subscribers (FeedVM, ProfileVM if active, PostDetailVM if active) receive the new map.
7. FeedVM's subscriber projects new state onto its `feedItems`; `setState` triggers PostCard recomposition; heart flips to filled, count +1.
8. Cache's `@ApplicationScope` coroutine fires `repo.like(StrongRef(...))`.
9. Network returns `AtUri("at://did:plc:viewer/app.bsky.feed.like/abc")`.
10. Cache promotes pending to real: `_cache.update { put(optimistic.copy(viewerLikeUri = "at://...", pendingLikeWrite = None)) }`. Emits.
11. Subscribers re-project; no visible change (the heart was already filled, count was already correct).
12. `toggleLike` returns `Result.success(Unit)`; FeedVM's `.onFailure` branch doesn't execute.

### Like flow (failure path)

Same as above through step 5. Then:

6. Network throws `IOException("net down")`.
7. Cache rolls back: `_cache.update { put(before) }` where `before` is the pre-tap snapshot. Emits.
8. Subscribers re-project; heart flips back to outline, count -1.
9. `toggleLike` returns `Result.failure(IOException)`.
10. FeedVM's `.onFailure` runs: `sendEffect(FeedEffect.ShowError(FeedError.Network))`.
11. Snackbar surfaces on Feed.

### Cross-screen sync flow

1. User on PostDetail screen, taps like.
2. PostDetailVM → `cache.toggleLike(...)`. Optimistic state emitted.
3. PostDetailVM's subscriber projects → PostDetail's focused-post UI shows liked.
4. **Concurrent:** FeedVM is alive on the back stack (per `MainShellNavState`'s per-tab persistence). Its subscriber ALSO receives the emission; projects onto its `feedItems`.
5. User taps back → returns to Feed. Feed renders from its current `uiState.feedItems`, which already reflects the like.
6. No re-fetch needed; no flicker; consistent state.

### Refresh-during-in-flight flow (preserved semantic from current FeedVM)

1. User taps like on Feed. Optimistic state emitted with `pendingLikeWrite = Pending`.
2. User immediately pulls to refresh.
3. FeedVM's `refresh()` runs; wire returns fresh posts with stale `viewer.like = null` and stale `likeCount` for the just-liked post.
4. FeedVM calls `cache.seed(freshPosts)` BEFORE `setState { copy(feedItems = ...) }`.
5. Seed merger sees `pendingLikeWrite = Pending` for the just-liked post → preserves cache state for that post. All other posts reseed from wire.
6. FeedVM's `setState` updates `feedItems` from the fresh wire posts.
7. FeedVM's `cache.state` subscriber emits the merged projection → the just-liked post still shows liked (cache wins), all other posts reflect wire.
8. Network call from step 1 eventually returns. Cache promotes pending → real, `pendingLikeWrite = None`. Subscribers re-project; visual unchanged.

## Error handling

| Failure | Where it's surfaced |
|---|---|
| `like` / `unlike` / `repost` / `unrepost` network failure | Cache rolls back state internally; `toggleLike`/`toggleRepost` returns `Result.failure(throwable)`; calling VM routes to its own `FooEffect.ShowError` |
| `NoSessionException` from underlying repo (signed-out mid-flight) | Same — `Result.failure`; VM surfaces as `FeedError.Unauthenticated` / etc. |
| Double-tap during in-flight | Cache's single-flight guard returns `Result.success(Unit)` synthetically; no double network call; no error effect surfaced |
| Sign-out mid-flight | `cache.clear()` resets state; in-flight `@ApplicationScope` coroutines complete and their Result is dropped (the calling VM may have already unmounted); no leakage |
| Cache `seed()` called with overlapping posts | Idempotent; merger preserves invariants; no failure mode |

Each consuming VM (FeedVM, ProfileVM, PostDetailVM) keeps its own `Throwable → FooError → FooEffect.ShowError` mapping — unchanged from today's pattern. No centralized error UI.

## Tests

### Unit tests in `:core/post-interactions`

`DefaultPostInteractionsCacheTest.kt` — 11 scenarios using `FakeLikeRepostRepository`:

1. `toggleLike` from empty cache (post not yet seeded): optimistic emit → success emit; repo called once with `like(StrongRef(uri, cid))`.
2. `toggleLike` from seeded `(likeUri=null, count=10)`: optimistic emit (likeUri=PENDING, count=11) → success emit (likeUri=real, count=11).
3. `toggleLike` from seeded `(likeUri="at://like/abc", count=11)` (unlike path): optimistic emit → success emit; repo called with `unlike(AtUri(...))`.
4. `toggleLike` network failure: optimistic emit → rollback emit to pre-tap state; `Result.failure` returned.
5. `toggleLike` double-tap during in-flight: second call is no-op; repo called exactly once; returns `Result.success(Unit)`.
6. `seed(posts)` on empty cache: every post lands with wire data.
7. `seed(posts)` preserves in-flight optimistic state: post with `pendingLikeWrite == Pending` keeps existing State.
8. `seed(posts)` with no in-flight write reseeds wire: counts + likeUri from wire.
9. `toggleLike` then `seed(stalePost)` mid-flight: cache state after seed equals pre-seed optimistic; success completion promotes.
10. `clear()` after toggles: state resets to empty map.
11. Symmetric `toggleRepost` (cases 1, 4, 5).

### Unit tests in `:feature:feed:impl`

`FeedViewModelTest.kt` modifications:

- DELETE: `like flips viewer + count optimistically …` (now owned by cache tests).
- DELETE: `like rolls back state and emits ShowError on failure` (rewrite below).
- DELETE: `repost flips viewer + count optimistically …`.
- DELETE: `repost rolls back and emits ShowError on failure`.
- REWRITE `OnLikeClicked dispatches cache.toggleLike and emits ShowError on failure`: fake cache returns `Result.failure(IOException)`; assert FeedVM emitted `FeedEffect.ShowError(FeedError.Network)`.
- REWRITE same for `OnRepostClicked`.
- ADD `loadFeed calls cache.seed(page.posts)`: assert seed invocation with the loaded posts.
- ADD `cache emission projects onto feedItems`: emit a `PostInteractionState` for a known postUri; assert `uiState.feedItems`'s matching post reflects the new viewer/stats.

`FakePostInteractionsCache` (impl of the public interface) replaces `FakeLikeRepostRepository`.

### Unit tests in `:feature:profile:impl`

`ProfileViewModelTest.kt` additions:

- `OnLikeClicked dispatches cache.toggleLike(post.id, post.cid)`: assert the call; assert no `ShowError` on success.
- `OnLikeClicked emits ShowError on cache failure`: fake returns `Result.failure`; assert `ProfileEffect.ShowError`.
- `cache state emits project onto the active tab's items`: emit for a postUri in `postsStatus.items`; assert merged state lands.
- `cache projection works across all three tabs`: emit for postUris in `repliesStatus.items` and `mediaStatus.items`; assert both reflect.
- Same shape for `OnRepostClicked`.

### Unit tests in `:feature:postdetail:impl`

`PostDetailViewModelTest.kt` additions: same dispatch test, error test, projection test (both the focused post AND a thread item).

### Screenshot tests

**No new baselines.** PostCard's liked/reposted variants already have screenshot coverage in `:designsystem`. The cache changes WHERE state comes from, not the rendering.

### Instrumentation tests

**None new in this PR.** Existing instrumentation tests on each screen continue to exercise the click → state-update → render path; they'll automatically pick up the new pipeline once their fakes point at `FakePostInteractionsCache`.

A future cross-screen instrumentation test ("like on PostDetail → back to Feed → still liked") would be belt-and-suspenders coverage. Out of scope for this PR; file as a follow-up if the unit-level coverage feels insufficient.

## Risks / trade-offs

| Risk | Mitigation |
|---|---|
| 3+ concurrent `cache.state` collectors (Feed + Profile + PostDetail, more later). Each emission triggers all of them. | StateFlow is conflated; only LATEST value is delivered; subscribers don't block each other. Map is bounded by session-touched posts (~hundreds); projection is O(N) on each VM's own list. Verify 120Hz scrolling via macrobench (`nubecita-ppj`) post-migration. |
| In-memory cache wipes on process death — an in-flight like at the moment Android kills the process is lost; the user sees un-liked state on relaunch. | Acceptable for this epic. Sub-second network calls during foreground are the common case. Future sibling epic graduates the cache to a Room backing; contract is upgrade-friendly (in-memory → Room is a backing swap, not a contract change). |
| PENDING sentinel string for `viewerLikeUri` could leak downstream if a consumer reads `PostUi.viewer.likeUri` directly. | `mergeInteractionState` explicitly strips the sentinel before exposing `PostUi`. The constant is `internal` to `:core/post-interactions/internal/`. Document in `PostInteractionState`'s KDoc that the field is opaque — consumers MUST go through `mergeInteractionState`. |
| Atproto eventual consistency: post-like `getTimeline` may return `viewer.like = null` for minutes. Naive reseed would un-flip the heart. | `seed()` merger rule preserves cache state when `pendingLikeWrite == Pending` OR when cache's `viewerLikeUri != null` and wire is null. Stale counts during this window stay at the optimistic value until wire catches up. |
| Concurrent refresh during in-flight like (existing FeedVM semantic). | Cache test #9 covers this; merger rule handles it. Migration preserves the semantic at the cache level instead of the VM level. |
| Cross-screen sync requires every PostCard-rendering screen to call `cache.seed(...)` after each wire fetch — easy to forget on new screens. | `PostInteractionsCache.seed`'s KDoc documents the contract as mandatory. A future "abstract feed-loading helper" could centralize it; premature today. |
| Sign-out clearing in flight: `@ApplicationScope` job continues but its result is dropped. | `Result.failure` with `CancellationException` is correct behavior — the user signed out; the state is moot; the screen has unmounted. No leak. |

## Out of scope for this PR (filed as follow-up bd issues)

| # | Follow-up | Why deferred |
|---|---|---|
| 1 | `feature/feed/impl: repost-vs-Quote bottomsheet` | Real bug from manual testing. Independent UX surface — doesn't gate cross-screen sync. Small follow-up PR. |
| 2 | `feature/composer: Quote-post mode` | Depends on composer epic state. Bottomsheet (#1) routes Quote to a "Coming soon" snackbar until this lands. |
| 3 | `feature/postdetail/impl: PostCallbacks.onReply wires composer` | Half-wired today via `OnReplyClicked` for the focused-post FAB. Per-PostCard reply icon is composer-epic concern. |
| 4 | `feature/{feed,postdetail,profile}/impl: PostCallbacks.onShare → Android intent` | AT URI → bsky.app permalink. Standalone PR; share path is independent. |
| 5 | `:offline-first-post-cache: graduate PostInteractionsCache to Room backing` | Cross-session / process-death durability. Sibling epic to `nubecita-8f6`, not a child. |
| 6 | `:designsystem.PostCard: in-flight pending-write spinner` | Optional polish — ship only if UX feedback shows users tap-tap during in-flight calls and find the delay confusing. |

## Acceptance

This PR is done when:

- [ ] `:core/post-interactions` module exists with `PostInteractionsCache` interface, `DefaultPostInteractionsCache` impl, `LikeRepostRepository` interface + impl (moved from `:feature:feed:impl`), `PostInteractionState` data class, `PostInteractionsModule` Hilt module.
- [ ] `FeedViewModel.toggleLike` + `toggleRepost` methods are deleted; event handlers delegate to `cache.toggleLike` / `cache.toggleRepost` and route failures via `FeedEffect.ShowError`.
- [ ] `FeedViewModel` subscribes to `cache.state` in `init` and projects onto `feedItems`.
- [ ] `loadFeed` / `refresh` / `loadMore` each call `cache.seed(posts)` after wire fetch.
- [ ] `:feature:feed:impl` no longer contains `LikeRepostRepository*.kt`; `build.gradle.kts` deps updated to use `:core/post-interactions`.
- [ ] `ProfileViewModel` wires `OnLikeClicked` / `OnRepostClicked`, subscribes to cache + projects across all 3 tabs, seeds on every per-tab page load + refresh.
- [ ] `ProfileScreen.kt` PostCallbacks `onLike` / `onRepost` no-ops replaced with real event dispatches.
- [ ] `PostDetailViewModel` wires same events + projection (focused post + thread tree); `PostDetailScreen.kt` PostCallbacks wired.
- [ ] `DefaultAuthRepository.signOut` calls `cache.clear()` before revocation.
- [ ] Cache unit tests cover the 11 scenarios from §Tests.
- [ ] Per-VM unit tests cover dispatch + projection + error-routing.
- [ ] `./gradlew :core:post-interactions:testDebugUnitTest :feature:feed:impl:testDebugUnitTest :feature:profile:impl:testDebugUnitTest :feature:postdetail:impl:testDebugUnitTest :app:assembleDebug spotlessCheck lint :app:checkSortDependencies` all green locally.
- [ ] On-device smoke (Pixel 10 Pro XL): like a post on Feed → tap into PostDetail → still shows liked. Unlike on PostDetail → back to Feed → shows unliked. Same for Profile. Like a post on Profile's Posts tab → switch to Replies tab → switch back to Posts → still liked.
- [ ] 6 follow-up bd issues filed and linked from the PR body.

## References

- Epic: `nubecita-8f6` (Post interactions: like, repost, reply, share) — this design refines the design note in that issue's description
- Bug surfaced during: PR #163 (bead F) manual testing — the "like on PostDetail doesn't reflect on Feed" cross-screen consistency problem
- Atproto lexicons: `app.bsky.feed.like`, `app.bsky.feed.repost` (both `createRecord` / `deleteRecord` shapes); see `~/code/kikinlex` for generated types
- `:feature:feed:impl/data/LikeRepostRepository.kt` — the existing interface being promoted (KDoc literally anticipates this move)
- `:feature:feed:impl/FeedViewModel.kt:222-375` — the existing optimistic-flip-with-rollback code being deleted in this PR (replaced by `DefaultPostInteractionsCache`)
- `:designsystem.PostCallbacks` — the unchanged composable seam consumed by every PostCard
- Gemini's input: identified the cross-screen consistency bug, proposed the SharedFlow pattern that this design refined into a StateFlow+cache shape
