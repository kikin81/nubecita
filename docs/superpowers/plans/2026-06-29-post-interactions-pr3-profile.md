# Post-Interaction Consolidation — PR3 (Migrate Profile) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Migrate `:feature:profile:impl`'s hand-inlined post-interaction wiring onto the shared `PostInteractionHandler` + `rememberPostInteractions` (as the feed already is) — a **byte-identical refactor** (no behavior change, screenshots untouched, profile tests' observable assertions unchanged).

**Architecture:** `ProfileViewModel by handler` + `handler.bind(PostSurface.Profile, viewModelScope)`; retire the inline `onLike`/`onRepost`/`onReply`/`onQuote`/`onShare`/overflow handlers + their `ProfileEvent`/`ProfileEffect` entries; `ProfileScreen` uses `rememberPostInteractions` for the interaction callbacks + effect observation. Profile keeps three small things (per the design, §"Each feature VM keeps…"): its per-tab `cache.state → items` merge (the list element type differs), its **optimistic mute as a flag-flip across all three tabs** (it owns that), and — for byte-identical parity — **block stays coming-soon** (an `onOverflowAction` override; block→real is PR4's job).

**Tech Stack:** PR1's `:core:post-interactions` (`PostInteractionHandler`, `PostInteractionsCache`, `PostSurface.Profile`) + `:core:post-interactions-ui` (`rememberPostInteractions`, `InteractionStrings`), Hilt (`@AssistedInject`), Compose/M3, JUnit5 + MockK + Turbine, screenshot tests.

**Design spec:** `docs/superpowers/specs/2026-06-29-post-interaction-consolidation-design.md` (step 3 of the phasing; line 118).

## Global Constraints

- **PR1+PR2 are merged** — `PostInteractionHandler`, `rememberPostInteractions(handler, snackbarHostState, strings, onInteractionError)`, `InteractionStrings`, `PostSurface.Profile` all exist on `main`. Reuse; do NOT modify `:core:post-interactions*`.
- **Byte-identical** — the stateless `ProfileScreenContent` (and every `ui/*` content composable) is UNTOUCHED; profile screenshots must stay byte-identical (0 baseline changes → NO `update-baselines` label). The tap-markers (`lastLikeTapPostUri`/`lastRepostTapPostUri`) STAY on `ProfileScreenViewState`, now driven by a `handler.tapMarkers` collector (mirror `FeedViewModel`), so `ProfileScreenContent`'s signature doesn't change.
- **Block stays coming-soon (PR3).** The design defers block→real to PR4. Profile's `onOverflowAction` override keeps `BlockAuthor → ProfileEffect.ShowPostOverflowComingSoon(BlockAuthor)` exactly as today. Do NOT delegate `BlockAuthor` to the handler (that would make it real). PR4 removes this arm.
- **No new strings.** Profile already has its overflow/error/share strings; `InteractionStrings` is built from the EXISTING profile string resources (the helper is resource-free). If any of the 13 `InteractionStrings` fields lacks an existing profile string, reuse the feed's value — but verify `:feature:profile:impl:lintProductionDebug` (no MissingTranslation).
- **Surface attribution:** `bind(PostSurface.Profile, …)` so `interact_post`/`share` carry `source_surface=profile`. PII-free enums.
- **Atomic cache-merge.** Write the (retained) `cache.state → items` collector to read state INSIDE `setState` (not the old `.map { uiState.value … }.collect { setState { it } }` outside-read shape — that's the race Gemini caught in PR2, tracked for the feed as nubecita-w4o9). Same observable result → the existing merge test stays green.
- Conventional Commits, **lowercase subject** (commitlint rejects capitalized/"PR3" leads); `Refs: nubecita-58dy`; no Co-Authored-By/Claude-Session footer; never `--no-verify`. `:feature:profile:impl`/`:app` are flavored → use `:feature:profile:impl:testProductionDebugUnitTest` etc.

---

### Task 1: `ProfileViewModel` — adopt the handler, retire the inline interaction code

**Files:**
- Modify: `feature/profile/impl/.../ProfileViewModel.kt` (ctor, `by handler`, `bind`, tap-marker collector, atomic merge, `onOverflowAction` override, retire inline handlers)
- Modify: `feature/profile/impl/.../ProfileContract.kt` (retire `OnLikeClicked/OnRepostClicked/OnReplyClicked/OnQuoteClicked/OnShareClicked/OnShareLongPressed` events + `SharePost/CopyPermalink` effects + the `NavigateTo(ComposerRoute|Report.forPost)` post-card cases; KEEP `OnPostOverflowAction` routed to the override, KEEP `ShowPostOverflowComingSoon` for block, KEEP `ShowError`, KEEP hero/nav effects + `NavigateTo(EditProfile|Report.forAccount)`)
- Modify: `feature/profile/impl/build.gradle.kts` — add `implementation(project(":core:post-interactions"))` if not already present (it is — cache is used today)
- Test: `feature/profile/impl/src/test/.../ProfileViewModelTest.kt`

**Interfaces — Consumes:** `PostInteractionHandler` (`bind(surface, scope)`, `by`-delegated `onLike/onRepost/onReply/onQuote/onShare/onShareLongPress/onOverflowAction`, `tapMarkers`, `interactionEffects`), `PostSurface.Profile`, `PostInteractionsCache` (kept for `seed` + merge). **THE reference is `FeedViewModel` (on main)** — read its `by handler` adoption, `bind`, `tapMarkers→state` collector, and the `onOverflowAction` override shape, then adapt for profile's flag-flip mute + coming-soon block.

- [ ] **Step 1 — failing/updated tests.** Build a `FakePostInteractionHandler` for profile's test source set (copy the feed's `feature/feed/impl/src/test/.../FakePostInteractionHandler.kt`, or reference a shared one). Update `newVm()` (ProfileViewModelTest ~line 2095) to drop `analytics` (verify it's interaction-only) + inject the fake handler; keep `FakePostInteractionsCache` (seed/merge) + `FakeMuteRepository`. **Delete** the tests that move to `DefaultPostInteractionHandlerTest` (already covered there): the like/repost `toggleLike`+`InteractPost` tests (~1120/1143/1253), share/permalink (~1279/1301/1326), reply/quote composer-nav (~1343/1368), `OnPostOverflowAction emits ShowPostOverflowComingSoon` for the now-delegated actions (~907), `ReportPost emits NavigateTo` (~945). **Keep unchanged:** all mute/unmute tests (~1444–2068, incl. the "rollback only reverts tab statuses" at ~1915), the `cache emission projects onto active tab` merge test (~1393), and the `BlockAuthor` coming-soon assertion (block STAYS coming-soon — keep/retarget that test to assert `ProfileEffect.ShowPostOverflowComingSoon(BlockAuthor)`). **Add:** a test asserting `vm.interactionEffects` (the delegated handler channel) emits for `onReply`/`onQuote`/`onShare`/`onShareLongPress` and that `onOverflowAction(ReportPost)`/`(UnblockAuthor/MuteThread/…)` reach the handler (mirror FeedViewModelTest). Watch RED.
- [ ] **Step 2 — run, watch fail** (`:feature:profile:impl:testProductionDebugUnitTest`).
- [ ] **Step 3 — implement the VM.**
  - Ctor: replace direct interaction use — keep `postInteractionsCache` (seed + merge), `muteRepository` (mute override); **drop `analytics`** if only used by the retired interaction handlers (verify no other call site); **add `handler: PostInteractionHandler`**. Class header: `… , PostInteractionHandler by handler`. `@AssistedInject`/`@AssistedFactory` unchanged.
  - `init`: `handler.bind(PostSurface.Profile, viewModelScope)`; add a `handler.tapMarkers` collector → `setState { copy(lastLikeTapPostUri = it.lastLikeTapPostUri, lastRepostTapPostUri = it.lastRepostTapPostUri) }` (mirror FeedViewModel lines 83–91), so the existing state fields + `ProfileScreenContent` are unchanged.
  - **Atomic merge:** rewrite the `cache.state` collector (currently ~113–118, `.map { uiState.value.applyInteractions(it) }.collect { setState { it } }`) to `cache.state.collect { snapshot -> setState { applyInteractions(snapshot) } }` — read+merge inside `setState`. Keep `seed()` at the 3 sites (direct `postInteractionsCache.seed`, like the feed).
  - **Retire** the inline `OnLikeClicked/OnRepostClicked/OnReplyClicked/OnQuoteClicked/OnShareClicked/OnShareLongPressed` handlers (lines ~183–224) — the `by`-delegated methods replace them; the screen calls `viewModel.onLike(post)` etc. Remove the now-dead `InteractPost`/`toggleLike`/`toggleRepost`/`toShareIntent` imports + the `setState { copy(lastLikeTapPostUri…) }` that moved to the tap-marker collector.
  - **`onOverflowAction` override** (the only `PostInteractionHandler` method profile overrides): `MuteAuthor` → existing flag-flip `setState { updateMutedByAuthor(did, true) }` + `muteRepository.muteActor(did)` + rollback `setState { updateMutedByAuthor(did, false) }` + `sendEffect(ProfileEffect.ShowError(...))`; `UnmuteAuthor` → symmetric; `BlockAuthor` → `sendEffect(ProfileEffect.ShowPostOverflowComingSoon(BlockAuthor))` (KEEP coming-soon — byte-identical, PR4 flips it); **else** → `handler.onOverflowAction(post, action)` (so `ReportPost`→real `NavigateToReport`, and `UnblockAuthor`/`MuteThread`/`UnmuteThread`/`CopyPostText`→handler `ShowComingSoon`). The hero-level `StubActionTapped(Block)` is separate and unchanged.
- [ ] **Step 4 — green** (`:feature:profile:impl:testProductionDebugUnitTest` + `:feature:profile:impl:compileProductionDebugKotlin`).
- [ ] **Step 5 — commit** `refactor(profile): ProfileViewModel adopts PostInteractionHandler\n\nRefs: nubecita-58dy`

---

### Task 2: `ProfileScreen` — `rememberPostInteractions`, shrink the effect collector

**Files:**
- Modify: `feature/profile/impl/.../ProfileScreen.kt` (replace the inline `PostCallbacks` `remember` with `rememberPostInteractions` + `callbacks.copy`; shrink the `effects` collector; drop the moved string/clipboard vars)
- Modify: `feature/profile/impl/build.gradle.kts` — add `implementation(project(":core:post-interactions-ui"))`; run `:app:checkSortDependencies`
- DO NOT MODIFY: `ProfileScreenContent.kt` or any `ui/*Content` composable (byte-identical)
- Test: profile screenshot tests stay byte-identical (no fixture/baseline changes)

**Interfaces — Consumes:** Task 1's VM (`by handler`), `rememberPostInteractions`/`InteractionStrings`. **THE reference is `FeedScreen`'s `rememberFeedInteractions`** (the haptics-wrapping `callbacks.copy` shape) — read it; profile mirrors it (profile DOES have a haptics layer, unlike search).

- [ ] **Step 1 — wire the screen.** Replace the inline `remember(viewModel, context, haptics) { PostCallbacks(...) }` (ProfileScreen ~91–143) with:
  - `val interactions = rememberPostInteractions(handler = viewModel, snackbarHostState = …, strings = InteractionStrings(<the existing profile R.string.* overflow/error/link-copied values>), onInteractionError = { haptics.rejected() })`
  - `val callbacks = remember(viewModel, interactions.callbacks, context, haptics) { interactions.callbacks.copy(onTap = { viewModel.handleEvent(ProfileEvent.PostTapped(it.id)) }, onAuthorTap = { viewModel.handleEvent(ProfileEvent.HandleTapped(it.handle)) }, onLike = { if (it.viewer.isLikedByViewer) haptics.likeOff() else haptics.likeOn(); viewModel.onLike(it) }, onRepost = { …haptics… viewModel.onRepost(it) }, onReply/onQuote/onShare = { haptics.lightTap(); viewModel.onReply(it) /* etc */ }, onShareLongPress = { viewModel.onShareLongPress(it) }, onExternalEmbedTap = { /* existing CCT launch */ }, onQuotedPostTap = { viewModel.handleEvent(ProfileEvent.OnQuotedPostTapped(it.uri)) }, onOverflowAction = { p, a -> viewModel.onOverflowAction(p, a) }) }` — match FeedScreen's exact haptics calls so behavior is identical. Pass `callbacks` to `ProfileScreenContent(postCallbacks = callbacks, …)` (unchanged signature).
- [ ] **Step 2 — shrink the effects collector.** In the `LaunchedEffect(Unit) { viewModel.effects.collect { … } }` (~185–261): REMOVE the `SharePost`, `CopyPermalink`, and `NavigateTo(ComposerRoute|Report.forPost)` post-card arms (now observed by `rememberPostInteractions` via `interactionEffects`). KEEP: `ShowError` (`haptics.rejected()` + snackbar), `ShowPostOverflowComingSoon` (block coming-soon snackbar — still emitted by the override), the hero `ShowComingSoon`, `NavigateToPost/Profile/Settings/Message/MediaViewer/VideoPlayer`, and `NavigateTo(EditProfile|Report.forAccount)`. Delete the now-unused pre-resolved overflow/share/clip string vars + `clipboardManager` (~158–172) that moved into `InteractionStrings`/the helper.
- [ ] **Step 3 — verify byte-identical.** `:feature:profile:impl:testProductionDebugUnitTest` + `:feature:profile:impl:validateProductionDebugScreenshotTest` (0 baseline changes) + `:feature:profile:impl:lintProductionDebug` (no MissingTranslation) + `compileProductionDebugKotlin` + `:app:checkSortDependencies` + spotless. If validate shows a diff, the cause is a `ProfileScreenContent`/callbacks change — fix to restore byte-identity, do NOT regenerate baselines.
- [ ] **Step 4 — commit** `refactor(profile): ProfileScreen uses rememberPostInteractions\n\nRefs: nubecita-58dy`

---

## Final verification (before PR)
- [ ] `:feature:profile:impl` suite + screenshot validate (0 baseline changes) + lint green; `:app:assembleProductionDebug` + `:app:assembleBenchDebug` link; spotless + checkSort clean.
- [ ] **Bench smoke** (`:app:installBenchDebug`, emulator-5554): open a Profile (tap an author from the feed) → tap a post's **like** (fills, count +1, sticks) + **repost** + **overflow → Mute** (author's posts flag/grey across tabs, optimistic) + **Report** (opens dialog) + **Block** (still shows the coming-soon snackbar — NOT real, that's PR4) + **reply/quote** (composer opens) — no FATAL. Capture screenshots.
- [ ] Compose gate: the screen rewire adds `@Composable` interaction wiring → run the compose-expert review before pushing (focus: the `callbacks.copy` keyed on `interactions.callbacks`, the tap-marker mirror, the two disjoint effect collectors — helper's `interactionEffects` vs the VM's own `effects` — must not double-collect).
- [ ] Whole-branch review (opus) — like/repost/mute/block parity, atomic merge across 3 tabs, no double-collect, byte-identical.
- [ ] PR opens WITHOUT `update-baselines` (0 baseline changes) + `Refs: nubecita-58dy`.
- [ ] Acceptance: profile post cards behave EXACTLY as before (like/repost/reply/quote/share/mute/report real, block still coming-soon) but now via the shared handler — the inline duplicate is gone; `source_surface=profile`; screenshots byte-identical; the feed/search/profile now all share one interaction path.

## Closes
Progresses `nubecita-98bl` (PR3 of epic `nubecita-58dy`). Block→real lands in PR4 (`nubecita-tgqv`).
