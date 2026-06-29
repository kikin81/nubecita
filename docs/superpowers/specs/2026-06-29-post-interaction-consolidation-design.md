# Post-Interaction Consolidation — Design

**Status:** Approved design, ready for implementation planning.
**Date:** 2026-06-29
**Goal:** Replace the per-screen copy-pasted post-interaction wiring (like / repost / reply / quote / share / overflow) with one shared `PostInteractionHandler` delegate + one shared `rememberPostInteractions` Compose helper, wire it into the currently-unwired Search Posts tab, unify capabilities across all surfaces, and make interactions bench-verifiable.

## Background

Like/repost **state** is already a single source of truth: `:core:post-interactions` exposes a `@Singleton PostInteractionsCache` whose `state: StateFlow<PersistentMap<String, PostInteractionState>>` every screen subscribes to and projects via `PostUi.mergeInteractionState(...)`. A like on one screen propagates everywhere with no refetch. The cache also owns the optimistic flip + rollback + single-flight per post URI. **That part is good and is NOT changing.**

The **wiring around the cache is duplicated four ways**, and one of them is a broken stub:

| Surface | Like/Repost | Reply/Quote | Share | Overflow (mute/block/report) | Wiring |
|---|---|---|---|---|---|
| **Feed** (`:feature:feed:impl`) | ✅ cache | ✅ | ✅ | mute✅ report✅ **block✅** | `ui/FeedInteractions.kt` `rememberFeedInteractions` (shared across the 2 feed screens only) |
| **Profile** (`:feature:profile:impl`) | ✅ cache | ✅ | ✅ | mute✅ report✅ block=coming-soon | full inline duplicate (VM handlers + inline `PostCallbacks` + effect collector) |
| **Post-detail** (`:feature:postdetail:impl`) | ✅ cache | ✅ | ✅ | mute✅ report✅ block=coming-soon | full inline duplicate |
| **Search Posts tab** (`:feature:search:impl`) | ❌ no-op | quote suppressed | ❌ no-op | coming-soon only | `PostsTabContent.kt` wires only `onTap` + `onOverflowAction`; `SearchPostsViewModel` has **no cache dependency at all** |

So: each of feed/profile/post-detail re-implements the same cluster (cache-state subscriber, `onLike → cache.toggleLike + InteractPost(surface) analytics + sticky tap-marker + ShowError`, share/permalink effects, the overflow `when`, an inline `PostCallbacks`, an effect collector). `rememberFeedInteractions` (just landed via nubecita-ag2s) de-duped only the two **feed** screens. Search renders the affordances from the default `PostCard` but the callbacks are the no-op data-class defaults. **Block** is real only in the feed; **unblock / mute-thread / unmute-thread / copy-text** are coming-soon everywhere (and stay so — see Out of Scope). Mute and report are real everywhere already.

## Decisions

- **Approach: composition delegate, not inheritance, not pure-Compose.** A base `PostInteractionsViewModel` is impossible (Kotlin single inheritance — the feature VMs already extend `MviViewModel` and each needs pagination/tabs/thread on top). A pure-Compose helper that writes to the cache directly scatters analytics + moderation-nav into Compose and breaks "the VM owns canonical state." Composition keeps MVI intact, avoids inheritance, and yields independently-testable units.
- **Kotlin `by` delegation — zero forwarding boilerplate.** `PostInteractionHandler` is an interface; each VM does `class FooViewModel(... handler ...) : MviViewModel<…>(), PostInteractionHandler by handler`. The `onLike/onRepost/onShare/onShareLongPress/onOverflowAction/onReply/onQuote` methods are exposed to Compose for free, no manual forwarding.
- **Direct effect observation — no per-VM effect mapping.** The delegate exposes its own `Flow<InteractionEffect>`. The shared Compose helper collects it **directly** and performs the side effects, bypassing each screen's MVI effect loop for interactions. This is not just less code — it is the *correct* home: the repo forbids ViewModels from touching `LocalMainShellNavState`, so moderation/composer navigation was always emitted as an effect for the composable to route. The composable is where `navState.add(...)` belongs.
- **Uniform capabilities across all four surfaces.** Every surface gets the identical capability set; only the `surface: PostSurface` enum varies (for `InteractPost` analytics attribution — `Feed / Profile / Search / PostDetail`, all already defined). No per-surface capability-gating (no `Set<Capability>` flags) — that is speculative complexity with no consumer today. Rationale: a `PostCard` should behave identically everywhere (design-system predictability); search is a legitimate proactive-moderation surface (find a bad actor → mute/block in place); and uniform means zero gating logic.
- **A second sanctioned MVI exception, documented in CLAUDE.md.** `by`-delegated interaction methods (`vm.onLike(post)`) are called directly by the screen, bypassing `handleEvent`. This is a deliberate, bounded departure mirroring the existing `TextFieldState` carve-out (a cross-cutting concern where VM-as-pure-projection is relaxed on purpose). The VM keeps `handleEvent` for all screen-specific events.
- **Bench-verifiable interactions.** A bench-flavor fake for the like/repost write path so the bench smoke verifies interactions *work* (heart fills, count +1, repost toggles) — not merely "didn't crash" — on each surface as it migrates.

## Architecture

### 1. `PostInteractionHandler` (delegate) — `:core:post-interactions`

```kotlin
interface PostInteractionHandler {
    /** Two-phase init: the VM supplies its analytics surface + lifecycle scope in `init`.
     *  Done as a method (not constructor args) so the handler stays a plain Hilt
     *  constructor param the VM can `by`-delegate — `by` requires a header param. */
    fun bind(surface: PostSurface, scope: CoroutineScope)

    /** Optimistic tap-markers for the count ±1 animation — interaction state, not screen state. */
    val tapMarkers: StateFlow<PostTapMarkers>            // lastLikeTapPostUri / lastRepostTapPostUri
    val interactionEffects: Flow<InteractionEffect>      // share / copy / nav / coming-soon / error

    fun onLike(post: PostUi)
    fun onRepost(post: PostUi)
    fun onReply(post: PostUi)
    fun onQuote(post: PostUi)
    fun onShare(post: PostUi)
    fun onShareLongPress(post: PostUi)
    fun onOverflowAction(post: PostUi, action: PostOverflowAction)
}

class DefaultPostInteractionHandler @Inject constructor(
    private val cache: PostInteractionsCache,
    private val muteRepository: MuteRepository,
    private val analytics: AnalyticsClient,
) : PostInteractionHandler { … }

// host VM:
class SearchPostsViewModel @Inject constructor(
    /* … */ private val handler: DefaultPostInteractionHandler,
) : MviViewModel<…>(), PostInteractionHandler by handler {
    init { handler.bind(surface = PostSurface.Search, scope = viewModelScope) }
}
```

- **3 injected deps** (cache, `MuteRepository`, `AnalyticsClient`); the per-VM `surface` + `viewModelScope` arrive via `bind()` in the VM's `init` (a constructor param can't be `by`-delegated *and* carry per-VM assisted values, so the two-phase `bind` resolves that). The handler is **unscoped** (each VM injection gets its own instance) while the cache it writes to is the app singleton, so cross-screen propagation is unchanged. Block/report/composer NavKeys are **not** constructed here (keeps `:core:post-interactions` free of `:feature:*:api` deps) — they are emitted as *data* effects and the UI module builds the NavKey (see §3). Share is `post.toShareIntent()` (already in `:core:post-interactions/sharing`).
- `onLike/onRepost` → `scope.launch { cache.toggleLike/Repost(id, cid) }` + `analytics.log(InteractPost(action, surface))` + update `tapMarkers`. The cache owns the optimistic flip / rollback / single-flight (unchanged); a failure surfaces as `InteractionEffect.ShowError`.
- `onOverflowAction` → `MuteAuthor/UnmuteAuthor` call `muteRepository` (real, with the optimistic-removal contract preserved by the host VM's list — see §4); `ReportPost`/`BlockAuthor` → emit `InteractionEffect.NavigateToReport(post)` / `NavigateToBlock(did, handle)`; the still-unbuilt actions → `InteractionEffect.ShowComingSoon(action)`.
- Constructed per host VM via an `@AssistedFactory` (so each VM passes its own `surface` + `viewModelScope`). The cache it writes to is the app-wide singleton, so cross-screen propagation is unchanged.

### 2. `InteractionEffect` (sealed) — `:core:post-interactions`

`ShowError(InteractionError)` — where `InteractionError` is a small **shared** error enum owned by `:core:post-interactions` (network / unauthenticated / unknown), which `rememberPostInteractions` maps to the snackbar string (each surface already owns equivalent strings; feed's `FeedError` stays feed-internal and is not reused here) · `SharePost(PostShareIntent)` · `CopyPermalink(String)` · `NavigateToComposer(replyToUri: String?, quoteUri: String?)` · `NavigateToReport(PostUi)` · `NavigateToBlock(did, handle)` · `ShowComingSoon(PostOverflowAction)`. Pure data — no NavKey, no Android types — so the delegate module stays feature-free and unit-testable.

### 3. `rememberPostInteractions` — new module `:core:post-interactions-ui`

A thin Compose module (depends on `:core:post-interactions`, `:designsystem` for `PostCallbacks`, `:core:common:navigation` for `LocalMainShellNavState`, and the `:feature:*:api` NavKey modules it routes to — `moderation:api`, `composer:api`).

```kotlin
@Composable
internal fun rememberPostInteractions(
    handler: PostInteractionHandler,
    snackbarHostState: SnackbarHostState,
    comingSoonStrings: … ,                 // pre-resolved via stringResource at composition
): PostInteractions                        // { callbacks: PostCallbacks, tapMarkers: PostTapMarkers }
```

- Builds the `PostCallbacks` (every slot wired to `handler.onXxx`), `remember`-keyed on `handler`.
- Owns one `LaunchedEffect(handler, snackbarHostState)` that collects `handler.interactionEffects` and performs each side effect: `ShowError`/`CopyPermalink`/`ShowComingSoon` → snackbar (replace-not-stack); `SharePost` → `context.launchPostShare`; `NavigateToComposer`/`NavigateToReport`/`NavigateToBlock` → `LocalMainShellNavState.current.add(ComposerRoute(...) / Report.forPost(...) / Block.forAccount(...))`.
- Returns the `tapMarkers` so the screen hands `lastLikeTapPostUri`/`lastRepostTapPostUri` to its `PostCard`s (count animation).
- This is the generalization of today's `rememberFeedInteractions`; the feed's video-coordinator wiring stays feed-specific (it is not a post-interaction concern) and remains in `:feature:feed:impl`.

### 4. Per-VM responsibilities (what stays)

Each feature VM keeps: its `handleEvent` for screen-specific events; a tiny `cache.state → its-list-type` merge collector (the list element types differ — `FeedItemUi` vs `ThreadItem` vs profile tab items — so the ~1-line merge is not worth extracting and is *not* duplication of logic, only of a `map`); and, for mute, the optimistic list mutation (remove-by-author) it already owns. Everything else (the interaction methods + their effects + analytics + tap-markers) comes from the delegate via `by`.

### 5. Use-case refactor seam (deferred)

The delegate has 3 real deps today — not a god class. **The explicit threshold for splitting into a `PostInteractionUseCases` wrapper (per Interface Segregation) is when `block` / `mute-thread` / `unblock` / `copy-text` become *real* actions** (each pulling its own repository). Until then, a single `DefaultPostInteractionHandler` is correct (YAGNI). Noted so the split is a deliberate decision, not drift.

## Bench support (verify interactions while migrating)

`:core:post-interactions` has no bench source set, so in bench `LikeRepostRepository` falls through to the real `DefaultLikeRepostRepository` → the offline write fails → the cache's optimistic flip rolls back (like flickers and reverts). `MuteRepository` already has a bench fake (`core/actors/src/bench/BenchFakeMuteRepository.kt`); block/report/quote/reply are navigation and already work offline.

**Add** `core/post-interactions/src/bench/.../BenchFakeLikeRepostRepository.kt` (returns `Result.success`, no network) + its bench DI module, mirroring `core/posting/src/bench/BenchFakePostingRepository` + `core/actors/src/bench/BenchFakeMuteRepository`. With the write faked to succeed, the optimistic like/repost flip **sticks** offline. **Also add** the identical `BenchFakeFollowRepository` (same gap; fixes the "Failed to follow" seen in the Discover bench smoke) — adjacent scope, folded in here because it is the same one-time pattern.

These land at the **front of PR1**, so from the first PR every migration's bench smoke verifies real interaction behavior (heart fills + count +1, repost toggles, mute removes, quote/reply open the composer, block/report open the moderation dialog) on each surface.

## Delivery — four sequential PRs (one bd task each)

Feed-first, not search-first, because **the feed migration has an oracle and search does not**: the feed is the most complete existing consumer (full `FeedViewModelTest` + committed screenshots + `rememberFeedInteractions`), so migrating it proves the shared code reproduces existing behavior **byte-identically** before it touches anything new. Search is net-new behavior with no oracle; validate the abstraction on the known case first, then apply it to the new case. The delegate API is nonetheless designed for the new-consumer (search) ergonomics from day one. Not monolithic: a core + new-module + four-feature diff is unreviewable and would surface an abstraction bug everywhere at once.

1. **Extract + prove (feed).** Add `PostInteractionHandler` + `DefaultPostInteractionHandler` + `InteractionEffect` (`:core:post-interactions`); the `BenchFakeLikeRepostRepository` + `BenchFakeFollowRepository` bench fakes; the new `:core:post-interactions-ui` with `rememberPostInteractions`. Migrate the **feed** (`FeedViewModel by handler`; `FeedScreen`/`FeedViewScreen` use `rememberPostInteractions`; retire `rememberFeedInteractions`'s interaction half, keeping the video-coordinator part feed-local). Document the second MVI exception in CLAUDE.md. **Gate:** feed unit tests pass unchanged; **byte-identical** FeedScreen/FeedViewScreen screenshots; bench smoke shows like/repost/mute working offline.
2. **Wire search.** `SearchPostsViewModel by handler` (gains the cache read-merge + the delegate); `PostsTabContent` uses `rememberPostInteractions`; search posts become fully interactive nodes (incl. moderation), `surface = PostSurface.Search`. The user-visible fix + the new-consumer validation. **Gate:** new search interaction tests; bench smoke verifies each action on a search result.
3. **Migrate profile.** `ProfileViewModel by handler`; `ProfileScreen` uses `rememberPostInteractions`; retire its inline copy. **Gate:** profile tests unchanged; byte-identical profile screenshots; bench smoke.
4. **Migrate post-detail + close block parity.** `PostDetailViewModel by handler`; `PostDetailScreen` uses `rememberPostInteractions`; retire its inline copy. Block goes coming-soon → real here (free, via the shared handler's `NavigateToBlock`). **Gate:** post-detail tests unchanged; byte-identical screenshots; bench smoke confirms block now opens the dialog.

## Error handling

Like/repost failures: the cache's existing optimistic-flip + rollback is unchanged; the delegate surfaces the failure as `InteractionEffect.ShowError` → a replace-not-stack snackbar + reject haptic (today's feed behavior, now shared). Mute failures: the host VM's optimistic list mutation rolls back (unchanged). Navigation effects cannot fail. Coming-soon actions: snackbar only. No new error surfaces.

## Testing

- **Delegate:** JVM unit tests in isolation — mock `PostInteractionsCache` + `MuteRepository`, `RecordingAnalyticsClient`, Turbine on `interactionEffects`. Assert: each action calls the right cache/mute method; `InteractPost(action, surface)` polarity + surface; overflow routing (mute=real, report/block=nav effect, rest=coming-soon); tap-markers update; analytics on success path only.
- **Compose helper:** the effect→side-effect mapping (snackbar/share/nav) via the existing screen test harnesses.
- **Feed (PR1):** existing `FeedViewModelTest` passes unchanged + byte-identical screenshots = the faithful-generalization proof.
- **Search (PR2):** new `SearchPostsViewModel` interaction tests (it had none).
- **Per-surface bench smoke:** the new capability — each PR's bench run verifies the interaction *works* offline, not just that it doesn't crash.
- **Screenshot validation:** feed/profile/post-detail migrations must stay byte-identical (the stateless `*Content` composables are untouched, as with ag2s); search gains new baselines.

## Out of scope (explicit)

- **Making `unblock` / `mute-thread` / `unmute-thread` / `copy-post-text` real** — they stay `ShowComingSoon` everywhere (now uniformly). Only `block` is promoted to its existing feed behavior. Those are separate features.
- **The `PostInteractionUseCases` split** — seam documented (§5), built only when the coming-soon actions become real.
- **Follow consolidation** — only the `BenchFakeFollowRepository` is in scope (bench parity); a shared *follow*-interaction handler (profile/discover) is a separate future effort.
- **The cache itself** — `PostInteractionsCache` / `LikeRepostRepository` semantics are unchanged.

## Delivery mechanics

One bd epic + four child tasks (PR1–4), implemented via the standard subagent-driven flow. Per the team's OpenSpec preference, this design migrates into an OpenSpec change when the bd epic is created, with the change's tasks cross-linked to the bd ids.
