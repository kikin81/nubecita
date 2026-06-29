# Explore Component C — Discover State (nubecita-trpn) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Fill the empty no-query Search page with a horizontal carousel of Threads-style **suggested-account cards** (avatar, "N mutuals", inline Follow, tap→Profile) + a horizontal carousel of Google-News-style **suggested-feed cards** (header + 2-3 lazy-loaded preview posts, inline Pin, tap→FeedView), alongside recent searches — and instrument Discover engagement (ty26) to measure the 18→1 search drop-off.

**Architecture:** All in `:feature:search:impl` (no new modules — it already has `FeedService` + `XrpcClientProvider` + `:core:feed-mapping`). A new `SuggestionsRepository` (data) + a new `DiscoverViewModel` (owns the suggestion state, optimistic follow/pin, lazy previews, analytics) + new card composables. `SearchPhase.Discover` renders recent searches (existing, from `SearchViewModel`) + the two carousels (from `DiscoverViewModel`). Reuses Component A (`PinnedFeedsRepository`), `FollowRepository`, the `interact_actor`/`interact_feed` + `PostSurface.Explore` analytics (Component B), and the `Profile`/`FeedView` NavKeys. Full design: `docs/superpowers/specs/2026-06-28-explore-page-design.md`.

## Global Constraints

- **Stable endpoints only:** `app.bsky.actor.getSuggestions` (accounts) + `app.bsky.feed.getSuggestedFeeds` (feeds) + `app.bsky.feed.getFeed` (the per-card preview). All in the SDK. No unspecced/trending.
- **Battery-safe lazy previews:** fetch the 2-3-post preview ONLY for feed cards on-screen, gated on the carousel being **settled** (`listState.isScrollInProgress == false` / debounce — no fling spam), **cache** each fetched preview (no refetch on re-scroll), never all up front. (Memory: battery is top priority.)
- **PII-free analytics:** `interact_actor` / `interact_feed` carry enums only (`actor_action`/`feed_action`, `source_surface=explore`) — no DIDs/handles/URIs.
- **Optimistic + targeted rollback:** mirror the mute/pin pattern — flip the flag on the current state, call the repo, flip back on failure (no snapshot clobber); errors → snackbar effect.
- **No crashing build:** `:feature:search:impl`/`:app` flavored; after the screen wiring lands, run the **bench smoke** (`:app:installBenchDebug`, launch, open Search → Discover renders cards, tap a card → Profile/FeedView, no FATAL).
- Conventional Commits, **lowercase subject**; `Refs: nubecita-trpn`; no Co-Authored-By/Claude-Session footer; never `--no-verify`. New strings need **es-419 + pt-rBR translations** in the same commit (CI Lint/android fails MissingTranslation otherwise — `:app` lint misses it; verify with `:feature:search:impl:lintProductionDebug`).

---

### Task 1: `SuggestionsRepository` + UI models (`:feature:search:impl/data`)

**Files:**
- Create: `feature/search/impl/.../data/SuggestionsRepository.kt` (+ `DefaultSuggestionsRepository`) + DI binding (mirror `SearchFeedsRepository`'s module)
- Create: `feature/search/impl/.../data/SuggestedAccountUi.kt`, `SuggestedFeedUi.kt`, `FeedPreviewPostUi.kt` (UI models; `@Immutable`)
- Test: `feature/search/impl/src/test/.../DefaultSuggestionsRepositoryTest.kt` (MockEngine harness — mirror `DefaultSearchFeedsRepositoryTest`)

**Interfaces — Produces:**
```kotlin
interface SuggestionsRepository {
    suspend fun getSuggestedAccounts(limit: Int = 15): Result<List<SuggestedAccountUi>>
    suspend fun getSuggestedFeeds(limit: Int = 15): Result<List<SuggestedFeedUi>>
    suspend fun getFeedPreview(feedUri: String, limit: Int = 3): Result<List<FeedPreviewPostUi>>
}
data class SuggestedAccountUi(val did, handle, displayName, avatarUrl, val isFollowing: Boolean, val followUri: String?, val mutualsCount: Int, val mutualAvatarUrls: ImmutableList<String>)
data class SuggestedFeedUi(val uri, displayName, creatorHandle, avatarUrl, val description: String?, val isPinned: Boolean = false)
data class FeedPreviewPostUi(val authorHandle, val authorAvatarUrl, val text: String, val thumbnailUrl: String?)
```

- [ ] **Step 1: Write failing tests** (MockEngine + real `XrpcClient`, mirror `DefaultSearchFeedsRepositoryTest`): `getSuggestedAccounts` issues `app.bsky.actor.getSuggestions` and maps `ProfileView` → `SuggestedAccountUi` (isFollowing from `viewer.following != null`, followUri from `viewer.following`, mutualsCount from `viewer.knownFollowers.count`, mutual avatars from its followers); `getSuggestedFeeds` issues `app.bsky.feed.getSuggestedFeeds` → `GeneratorView` mapping; `getFeedPreview` issues `app.bsky.feed.getFeed(limit=3)` and maps the first ≤3 posts to `FeedPreviewPostUi` (author + text snippet + first image thumb); failures → `Result.failure`; `CancellationException` propagates.
- [ ] **Step 2: Run, watch fail** — `:feature:search:impl:testProductionDebugUnitTest`.
- [ ] **Step 3: Implement.** `DefaultSuggestionsRepository(@Inject xrpcClientProvider, @IoDispatcher)`. Use `ActorService(client).getSuggestions(GetSuggestionsRequest(limit))`, `FeedService(client).getSuggestedFeeds(GetSuggestedFeedsRequest(limit))`, `FeedService(client).getFeed(GetFeedRequest(feed=AtUri(uri), limit))`. **Confirm the exact field paths** on `ProfileView.viewer` (`following`, `knownFollowers.count`/`.followers`) and `GeneratorView` from the SDK as you implement; map the preview posts via `:core:feed-mapping` (the module already depends on it) or a minimal inline extraction (author handle/avatar + record text + first image). `runCatching{}.onFailure{ if CancellationException throw; Timber.w }`.
- [ ] **Step 4: Green** + DI binding (`@Binds SuggestionsRepository`).
- [ ] **Step 5: Commit** — `feat(search): SuggestionsRepository (getSuggestions/getSuggestedFeeds/getFeed preview)\n\nRefs: nubecita-trpn`

---

### Task 2: `DiscoverViewModel` (`:feature:search:impl`)

**Files:**
- Create: `feature/search/impl/.../DiscoverViewModel.kt` + `DiscoverContract.kt` (state/event/effect)
- Test: `feature/search/impl/src/test/.../DiscoverViewModelTest.kt`

**Interfaces:**
- Consumes: `SuggestionsRepository` (T1), `FollowRepository` (`:core:post-interactions`), `PinnedFeedsRepository` (`:core:feeds`), `AnalyticsClient`.
- `DiscoverState`: `suggestedAccounts: ImmutableList<SuggestedAccountUi>` + an accounts load status; `suggestedFeeds: ImmutableList<SuggestedFeedUi>` (each with `preview: ImmutableList<FeedPreviewPostUi>?` + `previewStatus`) + a feeds load status. Each section loads independently; hidden on empty/error.
- Events: `onAppear` (load once on success; **auto-retry on re-appear if last attempt errored/empty**), `onRefresh`, `onFollowTapped(did)`, `onPinTapped(uri)`, `onFeedCardVisible(uri)` (trigger lazy preview), `onAccountTapped(did)`/`onFeedTapped(uri)` (→ NavigateTo effects), `onAccountDismissed(did)` (session-local).
- Effects: `NavigateToProfile(handle)`, `NavigateToFeed(uri, name)`, `ShowError`.

- [ ] **Step 1: Write failing tests** (MockK `SuggestionsRepository`/`FollowRepository`/`PinnedFeedsRepository`, `RecordingAnalyticsClient`, Turbine): both sections load concurrently on `onAppear`; a failed section is empty (hidden) + auto-retries on next `onAppear`; **follow** → optimistic `isFollowing=true` flip + `FollowRepository.follow(did)` + logs `InteractActor(Follow, Explore)`, rollback + error on failure; **unfollow** symmetric (`unfollow(followUri)`); **pin** → optimistic `isPinned` flip + `PinnedFeedsRepository.pinFeed/unpinFeed` + logs `InteractFeed(Pin/Unpin, Explore)`, rollback on failure (mirror Component B's `FeedPinViewModel`); **lazy preview** → `onFeedCardVisible(uri)` fetches `getFeedPreview` once and caches it (a second `onFeedCardVisible` for the same uri does NOT refetch); dismiss filters the account out. Watch RED → GREEN.
- [ ] **Step 2: Run, watch fail.**
- [ ] **Step 3: Implement** with the targeted flag-flip rollback (mirror mute/`FeedPinViewModel`). `isPinned` per feed is seeded from `PinnedFeedsRepository.observePinnedFeeds()` (uri match). Preview fetch is keyed per-uri + cached in state (`previewStatus`: idle→loading→loaded/error). Analytics logged on the success path only.
- [ ] **Step 4: Green** — `:feature:search:impl:testProductionDebugUnitTest`.
- [ ] **Step 5: Commit** — `feat(search): DiscoverViewModel (suggestions + optimistic follow/pin + lazy preview + analytics)\n\nRefs: nubecita-trpn`

---

### Task 3: Discover cards + carousels + SearchScreen wiring (`:feature:search:impl`)

**Files:**
- Create: `feature/search/impl/.../ui/SuggestedAccountCard.kt`, `ui/SuggestedFeedCard.kt`, `ui/DiscoverSections.kt` (the two `LazyRow` carousels + section headers)
- Modify: `feature/search/impl/.../SearchScreen.kt` (render the Discover sections under `SearchPhase.Discover` — recent searches + accounts carousel + feeds carousel; host `DiscoverViewModel`; collect its nav/error effects → `LocalMainShellNavState`/snackbar)
- Modify: string resources + **es-419 + pt-rBR translations** (section titles, Follow/Following, Pin/Pinned, "N mutuals", "Pull to refresh to discover content", content descriptions)
- Test: `feature/search/impl/src/screenshotTest/.../DiscoverScreenshotTest.kt` (mirror `SearchScreenScreenshotTest`)

**Interfaces:** Consumes `DiscoverViewModel` (T2), `SuggestedAccountUi`/`SuggestedFeedUi`/`FeedPreviewPostUi` (T1). Reuses `NubecitaAvatar`, a primary/secondary button, the `surfaceContainer` token.

- [ ] **Step 1: Account card + carousel.** `SuggestedAccountCard` (Threads-style: elevated card, avatar + name + @handle, **"N mutuals"** with small overlapping mutual avatars when `mutualsCount>0`, full-width Follow/**Following** button, optional dismiss ×). Card tap → `onAccountTapped`; Follow button is separate. A horizontal `LazyRow` carousel with `key={it.did}`. `@Preview` + screenshot (with/without mutuals; following state).
- [ ] **Step 2: Feed card + carousel + lazy preview.** `SuggestedFeedCard` (Google-News-style: header = feed avatar + name + creator + **Pin/Pinned** toggle; body = the 2-3 `preview` posts, or a shimmer placeholder while `previewStatus==loading`). Card tap → `onFeedTapped` (→ FeedView). The carousel reports visible feed uris to `onFeedCardVisible` **gated on scroll-settled** (`snapshotFlow` on the `LazyRow` `listState`, `isScrollInProgress==false` + small prefetch) so previews load only for on-screen cards. `@Preview` + screenshots (with preview loaded; loading; pinned).
- [ ] **Step 3: SearchScreen Discover wiring.** Under `SearchPhase.Discover`: a vertical scroll with Recent searches (existing) → Suggested accounts carousel → Discover feeds carousel; each section hidden when empty/errored; if all empty + no recent → the **"Pull to refresh to discover content"** hint. Host `DiscoverViewModel` (`hiltViewModel`), fire `onAppear`, collect effects → `LocalMainShellNavState.add(Profile/FeedView)` + snackbar. Pull-to-refresh → `onRefresh`.
- [ ] **Step 4: Screenshots** — `DiscoverScreenshotTest` rendering the populated Discover state (accounts + feeds with previews), a partial state, and the empty hint, light/dark. Generate + commit baselines (`:feature:search:impl:updateDebugScreenshotTest`). (If CI render drift, the PR uses the `update-baselines` label.)
- [ ] **Step 5: Green** — `:feature:search:impl:testProductionDebugUnitTest` + `validateDebugScreenshotTest` + `:feature:search:impl:lintProductionDebug` (catches MissingTranslation) + compile.
- [ ] **Step 6: Commit** — `feat(search): Discover cards + carousels + SearchPhase.Discover wiring\n\nRefs: nubecita-trpn`

---

## Final verification (before PR)
- [ ] `:feature:search:impl` suite green; `:app:assembleDebug` links; spotless + `:app:lintProductionDebug` **AND `:feature:search:impl:lintProductionDebug`** clean (translations); screenshot baselines committed.
- [ ] **Bench smoke (no crashing build):** `:app:installBenchDebug` → launch → Search tab → Discover renders the account + feed carousels (bench fakes) → tap an account → Profile; tap a feed → FeedView; tap Follow + Pin → no FATAL.
- [ ] Compose gate: adds new `@Composable`s (cards) → run the compose-expert review before pushing.
- [ ] Register new GA4 params if any via the LOCAL script (`source_surface` already covers `explore`; `actor_action`/`feed_action` already registered from prior epics).
- [ ] Acceptance: the empty Search page now shows suggested accounts (inline Follow, tap→Profile, mutuals) + suggested feeds (Google-News cards with lazy previews, inline Pin, tap→FeedView); follow/pin are optimistic with rollback; engagement events fire with `source_surface=explore` (closes ty26). Sections hide independently on empty/error + auto-retry on re-appear.

## Closes
`nubecita-trpn` (Component C) — and **nubecita-ty26** (Discover engagement analytics ships in Task 2/3).
