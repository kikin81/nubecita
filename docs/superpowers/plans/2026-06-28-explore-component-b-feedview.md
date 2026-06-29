# Explore Component B — Custom-Feed View Screen (nubecita-mdnl) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** A full-screen `FeedView(feedUri, displayName)` sub-route that paginates `app.bsky.feed.getFeed` for an arbitrary generator and renders through the shared `PostFeedList` (B0), with a Pin/Pinned toggle in the top bar (Component A) and the new `interact_feed` analytics event (ty26).

**Architecture / key decision:** **Reuse the home `FeedViewModel`** (bound to the feed URI via `FeedEvent.Bind(uri, FeedKind.Generator)`) for content — it already does `getFeed` pagination + the like/repost/mute/share/overflow handlers + emits `ViewFeed(FeedType.Custom)` on load, with zero host/chip coupling. **No new feed VM.** Add only a small **pin-toggle holder** for the top bar. Co-located in `:feature:feed` (no new module). Full design: `docs/superpowers/specs/2026-06-28-explore-page-design.md`.

**Tech Stack:** Nav3 (`NavKey`, `@MainShell` `EntryProviderInstaller`, `LocalMainShellNavState`), Compose/M3, Hilt, the existing `FeedRepository.getFeed` + `FeedViewModel` + `PostFeedList`, `:core:feeds` `PinnedFeedsRepository`, `:core:analytics`, JUnit5 + MockK + Turbine + RecordingAnalyticsClient, screenshot tests.

## Global Constraints

- **Full-screen sub-route, NOT adaptive** — plain `entry<FeedView> { … }` with no `adaptiveDialog()` (it's a primary content surface like PostDetail/Profile, not an overlay). Registered `@MainShell`.
- **Reuse, don't duplicate** — `FeedViewModel` for content; `PostFeedList` for rendering; the `PostCallbacks` shape from `FeedScreen`. If `FeedScreen`'s `PostCallbacks` construction can be lifted to a small shared `internal` helper without behavior change, do so; otherwise replicate it (it's screen-level lambda wiring).
- **PII-free analytics** (locked 049f decision): `interact_feed` carries enums only (`feed_action`, `source_surface`) — no feed URIs/DIDs/handles.
- **No crashing build** — `:feature:feed`/`:app` flavored; after the screen + nav wiring lands, run the **bench smoke** (`:app:installBenchDebug`, launch, no FATAL) since this adds a DI graph entry + a new screen.
- Conventional Commits, **lowercase subject**; `Refs: nubecita-mdnl`; no Co-Authored-By/Claude-Session footer; never `--no-verify`.

---

### Task 1: `FeedView` NavKey + `interact_feed` analytics event

**Files:**
- Modify: `feature/feed/api/src/main/kotlin/net/kikin/nubecita/feature/feed/api/Feed.kt` (add the NavKey)
- Modify: `core/analytics/src/main/kotlin/net/kikin/nubecita/core/analytics/AnalyticsEvent.kt` (add `InteractFeed` + `FeedAction` enum; add `PostSurface.FeedView` + `PostSurface.Explore`)
- Modify: `core/testing/.../RecordingAnalyticsClient.kt` if it enumerates events (keep the shared superset compiling)
- Test: `core/analytics/src/test/.../AnalyticsEventTest` (or the existing event-params test) for `InteractFeed`

**Interfaces — Produces:**
```kotlin
// :feature:feed:api
@Serializable
data class FeedView(val feedUri: String, val displayName: String? = null) : NavKey

// :core:analytics
enum class FeedAction(val wire: String) { Pin("pin"), Unpin("unpin") }
data class InteractFeed(
    val action: FeedAction,
    val surface: PostSurface,          // FeedView for B; Explore for C's Discover pin
    override val name: String = "interact_feed",
    override val params: Map<String, AnalyticsValue> = mapOf(
        "feed_action" to Str(action.wire),
        "source_surface" to Str(surface.wire),
    ),
) : AnalyticsEvent
// add PostSurface.FeedView("feed_view") and PostSurface.Explore("explore") to the PostSurface enum
```

- [ ] **Step 1: Write failing test** — assert `InteractFeed(FeedAction.Pin, PostSurface.FeedView)` has `name == "interact_feed"` and params `feed_action=pin`, `source_surface=feed_view`; same for Unpin/Explore. Mirror the existing event-params test style.
- [ ] **Step 2: Run, watch fail.**
- [ ] **Step 3: Implement** the NavKey + the event + the two new `PostSurface` values. Keep `RecordingAnalyticsClient` / any event registry compiling (grep for exhaustive `when (event)` over `AnalyticsEvent`).
- [ ] **Step 4: Green** — `./gradlew :core:analytics:testDebugUnitTest :feature:feed:api:compileDebugKotlin`.
- [ ] **Step 5:** Note in the report that `feed_action` is a NEW GA4 event param → register the custom dimension via the LOCAL `register_ga4_dimensions.py` (not committed); `source_surface` already registered (just gains values, no re-registration).
- [ ] **Step 6: Commit** — `feat(analytics): add interact_feed event + FeedView NavKey + FeedView/Explore surfaces\n\nRefs: nubecita-mdnl`

---

### Task 2: `FeedViewScreen` + pin-toggle holder + `@MainShell` registration

**Files:**
- Create: `feature/feed/impl/src/main/.../FeedPinController.kt` (or `FeedPinViewModel`) — the top-bar pin state + toggle
- Create: `feature/feed/impl/src/main/.../FeedViewScreen.kt` — the screen (reuses `FeedViewModel` + `PostFeedList`)
- Modify: `feature/feed/impl/src/main/.../di/FeedNavigationModule.kt` — add the `@MainShell` `EntryProviderInstaller` for `FeedView`
- Test: `feature/feed/impl/src/test/.../FeedPinControllerTest.kt`; `feature/feed/impl/src/screenshotTest/.../FeedViewScreenScreenshotTest.kt`

**Interfaces:**
- Consumes: `FeedViewModel` (`FeedEvent.Bind(uri, FeedKind.Generator)`, its `FeedScreenViewState` + effects + `PostCallbacks`), `PostFeedList` (B0), `PinnedFeedsRepository` (A), `InteractFeed`/`AnalyticsClient` (Task 1), `LocalMainShellNavState`.

**Pin holder** — a `@HiltViewModel` (or assisted with the uri) exposing `isPinned: StateFlow<Boolean>` = `pinnedFeedsRepository.observePinnedFeeds().map { res -> res.feeds.any { it.uri == feedUri } }`, and `fun togglePin()` → `if (isPinned) unpinFeed(uri) else pinFeed(uri)`; on success `analytics.log(InteractFeed(action, PostSurface.FeedView))`; on failure emit an error effect (snackbar). Mutex/idempotency already handled in `:core:feeds`.

- [ ] **Step 1: Write failing pin-holder tests** (JVM, mock `PinnedFeedsRepository` + `RecordingAnalyticsClient`): `isPinned` reflects whether the uri is in `observePinnedFeeds()`; `togglePin()` when unpinned → `pinFeed(uri)` + logs `InteractFeed(Pin, FeedView)`; when pinned → `unpinFeed` + logs `Unpin`; failure → error effect, no crash.
- [ ] **Step 2: Run, watch fail.**
- [ ] **Step 3: Implement the pin holder**, then the **`FeedViewScreen`**:
  - `hiltViewModel<FeedViewModel>()`; `LaunchedEffect(feedUri) { feedViewModel.handleEvent(FeedEvent.Bind(feedUri, FeedKind.Generator)) }`.
  - Build `PostCallbacks` exactly as `FeedScreen` does (reuse a shared helper if cleanly extractable; else replicate), and collect `FeedViewModel`'s effects (NavigateTo/SharePost/error) the same way `FeedScreen` does — wire nav via `LocalMainShellNavState`.
  - `Scaffold(containerColor = surface)` with a `TopAppBar`: back nav + `displayName` (fallback to a generic title) + a **Pin/Pinned `IconToggleButton`** driven by the pin holder. Body = `PostFeedList(...)` fed from `FeedViewModel`'s `FeedScreenViewState` (hoist `cardColor`/`cardShape` from `MaterialTheme` once, mirror `FeedScreenContent`). Apply standard edge-to-edge insets (TopAppBar owns the top inset; body `consumeWindowInsets`).
  - `ViewFeed(FeedType.Custom)` comes for free from the reused `FeedViewModel`.
- [ ] **Step 4: Register `@MainShell`** — add a `@Provides @IntoSet @MainShell EntryProviderInstaller` in `FeedNavigationModule` mirroring the home `Feed` provider's nav-callback wiring, `entry<FeedView> { route -> FeedViewScreen(route.feedUri, route.displayName, … nav callbacks via LocalMainShellNavState …) }`. **No `adaptiveDialog()`.**
- [ ] **Step 5: Screenshot tests** — mirror `FeedScreenScreenshotTest`: a `FeedViewScreenContent`-style fixture host rendering loaded / initial-loading / empty / initial-error, plus pinned-vs-unpinned top bar, light/dark. Generate + commit baselines (`:feature:feed:impl:updateDebugScreenshotTest`).
- [ ] **Step 6: Green** — `:feature:feed:impl:testProductionDebugUnitTest` + `validateDebugScreenshotTest` + `:feature:feed:impl:compileProductionDebugKotlin`.
- [ ] **Step 7: Commit** — `feat(feed): FeedView screen + pin toggle (reuses FeedViewModel + PostFeedList)\n\nRefs: nubecita-mdnl`

---

### Task 3: Push site — open `FeedView` from the Search Feeds tab

**Files:**
- Modify: `feature/search/impl/.../SearchFeedsContract.kt` (add `FeedTapped` event + `NavigateToFeed` effect)
- Modify: `feature/search/impl/.../SearchFeedsViewModel.kt` (emit the effect on tap)
- Modify: `feature/search/impl/.../SearchFeedsScreen.kt` (make the feed row clickable → `FeedTapped`; collect `NavigateToFeed` → `navState.add(FeedView(...))`)
- Modify: `feature/search/impl/build.gradle.kts` if it needs `:feature:feed:api` (for the `FeedView` NavKey)
- Test: `feature/search/impl/src/test/.../SearchFeedsViewModelTest.kt`

**Interfaces:** Consumes `FeedView` (Task 1). Mirrors how the Posts/People tabs already emit nav effects collected by the screen.

- [ ] **Step 1: Write failing test** — `SearchFeedsViewModel` on `FeedTapped(uri, displayName)` emits `SearchFeedsEffect.NavigateToFeed(uri, displayName)` (Turbine on the effect flow). Mirror the existing Posts/People nav-effect tests.
- [ ] **Step 2: Run, watch fail.**
- [ ] **Step 3: Implement** the event/effect + the VM emission; make `FeedRow` clickable in `SearchFeedsScreen` and collect the effect → `LocalMainShellNavState.current.add(FeedView(feedUri = uri, displayName = name))`. Remove the "no tap-through in V1" comment.
- [ ] **Step 4: Green** — `:feature:search:impl:testProductionDebugUnitTest`; add/refresh a screenshot only if the row visuals changed (a clickable row usually doesn't).
- [ ] **Step 5: Commit** — `feat(search): open FeedView from the Feeds tab\n\nRefs: nubecita-mdnl`

---

## Final verification (before PR)
- [ ] `:feature:feed:impl` + `:feature:search:impl` + `:core:analytics` suites green; `:app:assembleDebug` links; spotless + lint clean; screenshot baselines committed.
- [ ] **Bench smoke (no crashing build):** `:app:installBenchDebug` → launch → navigate to a feed from the Search Feeds tab → FeedView opens, renders posts via `PostFeedList`, the Pin toggle works, back nav works — no FATAL.
- [ ] Compose gate: this adds `@Composable`s (FeedViewScreen + top bar) → run the compose-expert review on the diff before pushing.
- [ ] Acceptance: tapping a feed opens a full-screen FeedView paginating `getFeed`; like/repost/mute/overflow work (reused); Pin/Pinned toggle pins via Component A and fires `interact_feed`; `ViewFeed(Custom)` fires on load.
