# Post-Interaction Consolidation — PR2 (Wire Search) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Make the Search **Posts tab** post cards fully interactive (like / repost / reply / quote / share / overflow=mute/block/report) by wiring `SearchPostsViewModel` + `PostsTabContent` through the shared `PostInteractionHandler` + `rememberPostInteractions` that PR1 built and proved on the feed.

**Architecture:** `SearchPostsViewModel` injects the `@Singleton PostInteractionsCache` (read-merge) + a `PostInteractionHandler` (adopted via `by`), `bind(PostSurface.Search, viewModelScope)`; it seeds the cache on each page load and merges `cache.state` into its `FeedItemUi.Single` items. The screen uses `rememberPostInteractions` (direct effect observation) for the interaction callbacks + merges its own screen-nav slots (`onTap`→post detail, `onAuthorTap`→profile). New-consumer validation: unlike the feed this is **net-new behavior**, so search screenshots change (new affordances) — use the `update-baselines` label.

**Tech Stack:** PR1's `:core:post-interactions` (`PostInteractionHandler`, `PostInteractionsCache`, `PostSurface.Search`) + `:core:post-interactions-ui` (`rememberPostInteractions`, `InteractionStrings`), Hilt, Compose/M3, JUnit5 + MockK + Turbine, screenshot tests.

**Design spec:** `docs/superpowers/specs/2026-06-29-post-interaction-consolidation-design.md` (Component C / "search as the forcing function").

## Global Constraints

- **PR1 is merged** — `PostInteractionHandler`, `rememberPostInteractions(handler, snackbarHostState, strings, onInteractionError)`, `InteractionStrings`, the bench fakes, and `PostSurface.Search` all exist on `main`. Reuse them; do NOT modify the helper/delegate.
- **NOT byte-identical** — search gains functionality, so its post-card affordances + the `PostsTabContent`/`SearchPostsScreen` screenshots legitimately change. Regenerate the touched search baselines and open the PR with the **`update-baselines`** label (CI regenerates against its renderer). Do NOT touch any non-search baselines.
- **New strings need translations** — search's snackbar strings (error network/unauth/unknown, link-copied, clip-label, 8 coming-soon) are NEW → add `values/strings.xml` + **`values-b+es+419` + `values-pt-rBR`** in the same commit, or CI `Lint / android` fails `MissingTranslation`. Verify with `:feature:search:impl:lintProductionDebug`.
- **Surface attribution:** `surface = PostSurface.Search` (already in the enum) so `interact_post`/`share`/`interact_actor` carry `source_surface=search`. PII-free enums only.
- **No crashing build:** `:feature:search:impl`/`:app` flavored; after the wiring, bench smoke (`:app:installBenchDebug`, search a query, tap a post result's like → fills + count +1 + sticks offline; overflow works).
- Conventional Commits, **lowercase subject** (commitlint rejects capitalized/"PR2" leads); `Refs: nubecita-58dy`; no Co-Authored-By/Claude-Session footer; never `--no-verify`.

---

### Task 1: `SearchPostsViewModel` — adopt the handler + cache read-merge

**Files:**
- Modify: `feature/search/impl/.../SearchPostsViewModel.kt` (ctor deps, `by handler`, `bind`, seed+merge), `SearchPostsContract.kt` (a `NavigateToProfile(handle)` effect for author tap; the existing `OnOverflowAction`/`ShowComingSoon` event+effect are now owned by the handler — remove the stub handling)
- Modify: `feature/search/impl/build.gradle.kts` — add `implementation(project(":core:post-interactions"))` (cache + handler) if not present
- Test: `feature/search/impl/src/test/.../SearchPostsViewModelTest.kt`

**Interfaces — Consumes:** `PostInteractionsCache` (`val state`, `fun seed(List<PostUi>)`), `PostInteractionHandler` (`fun bind(surface, scope)`, `by`-delegated `onLike/onRepost/onReply/onQuote/onShare/onShareLongPress/onOverflowAction`, `tapMarkers`, `interactionEffects`), `PostSurface.Search`. Mirror `FeedViewModel`'s adoption (PR1) — read it.

- [ ] **Step 1 — failing tests** (MockK on `SearchPostsRepository` + a `FakePostInteractionHandler` mirroring the feed's + a fake/mock `PostInteractionsCache`, Turbine): after a query loads a page, `cache.seed(posts)` is called and `cache.state` is merged into `status.items` (a like in the cache reflects on the rendered item — mirror the feed's `applyInteractions`); `vm.onLike(post)` is delegated to the handler (assert via the fake handler's recorded call); `OnAuthorTapped(handle)` emits `NavigateToProfile(handle)`; `PostTapped` still emits `NavigateToPost`. Watch RED → GREEN.
- [ ] **Step 2 — run, watch fail** (`:feature:search:impl:testProductionDebugUnitTest`).
- [ ] **Step 3 — implement.** `SearchPostsViewModel(... repository, analytics, cache: PostInteractionsCache, handler: PostInteractionHandler) : MviViewModel<…>(…), PostInteractionHandler by handler`; `init { handler.bind(PostSurface.Search, viewModelScope) }`. In `runFirstPage`/`loadMore` success: `cache.seed(page.posts)`; add an `init` collector `cache.state.onEach { merge into status.items }.launchIn(viewModelScope)` (the items are `FeedItemUi.Single` — reuse the feed's `applyInteractions` mapping or the `:core:post-interactions` `mergeInteractionState`). Add `OnAuthorTapped` event → `NavigateToProfile(handle)`. The handler now owns `onOverflowAction` (mute/report/block/coming-soon) — delete the `OnOverflowAction`→`ShowComingSoon` stub. (No optimistic mute-removal in search — results are transient; note it as a possible later refinement.)
- [ ] **Step 4 — green.**
- [ ] **Step 5 — commit** `feat(search): SearchPostsViewModel adopts PostInteractionHandler + cache merge\n\nRefs: nubecita-58dy`

---

### Task 2: Search UI — `rememberPostInteractions` + strings + nav + screenshots

**Files:**
- Modify: `feature/search/impl/.../SearchPostsScreen.kt` (host `rememberPostInteractions`; pass `callbacks`+`tapMarkers` down; collect `NavigateToProfile` → `navState.add(Profile(handle))`; ensure a `SnackbarHostState`)
- Modify: `feature/search/impl/.../ui/PostsTabContent.kt` (take `callbacks: PostCallbacks` + `tapMarkers` as params instead of building the stub inline; pass to `PostCard`)
- Modify: strings — `values/strings.xml` + `values-b+es+419/strings.xml` + `values-pt-rBR/strings.xml` (the 13 `InteractionStrings` fields, search-scoped, e.g. `search_snackbar_*`)
- Modify: `feature/search/impl/build.gradle.kts` — `implementation(project(":core:post-interactions-ui"))`
- Test: update `PostsTabContent`/`SearchPosts` screenshot tests + regenerate baselines

**Interfaces — Consumes:** Task 1's VM (`by handler`, `NavigateToProfile`), `rememberPostInteractions`/`InteractionStrings` (PR1), `Profile` NavKey (`:feature:profile:api`, already a dep). Mirror the feed's `rememberFeedInteractions` use of `rememberPostInteractions` + `callbacks.copy` (PR1) — read it.

- [ ] **Step 1 — strings + translations.** Add the 13 search snackbar strings (`search_snackbar_error_network/unauthenticated/unknown`, `search_snackbar_link_copied`, `search_clipboard_label`, `search_snackbar_overflow_*_coming_soon`) to `values/` + `values-b+es+419/` + `values-pt-rBR/` (match existing search/feed terminology). Verify `:feature:search:impl:lintProductionDebug`.
- [ ] **Step 2 — wire the screen.** In `SearchPostsScreen`: `val interactions = rememberPostInteractions(handler = viewModel, snackbarHostState = …, strings = InteractionStrings(…the search strings…))`; `val callbacks = remember(viewModel, interactions.callbacks) { interactions.callbacks.copy(onTap = { viewModel.handleEvent(SearchPostsEvent.PostTapped(it.id)) }, onAuthorTap = { viewModel.handleEvent(SearchPostsEvent.OnAuthorTapped(it.handle)) }) }` (search has no haptics layer — keep it simple; do NOT re-wrap onLike/onRepost). Pass `callbacks` + `interactions.tapMarkers` into `PostsTabContent`. Collect `viewModel.effects` for `NavigateToPost`/`NavigateToProfile` → `navState.add(...)`; the interaction effects (snackbar/share/nav-to-composer/report/block) are observed by `rememberPostInteractions` directly.
- [ ] **Step 3 — `PostsTabContent`** takes `callbacks: PostCallbacks` + `tapMarkers: PostTapMarkers` params (drop the inline stub `PostCallbacks`); thread `tapMarkers` to `PostCard` for the count animation.
- [ ] **Step 4 — screenshots.** Update the search screenshot fixtures (the post cards are now fully interactive — affordances appear). Regenerate: `:feature:search:impl:updateProductionDebugScreenshotTest`; commit the changed baselines.
- [ ] **Step 5 — green** — `:feature:search:impl:testProductionDebugUnitTest` + `validateProductionDebugScreenshotTest` + `:feature:search:impl:lintProductionDebug` + `compileProductionDebugKotlin` + `:app:checkSortDependencies` + spotless.
- [ ] **Step 6 — commit** `feat(search): wire Posts tab interactions via rememberPostInteractions\n\nRefs: nubecita-58dy`

---

## Final verification (before PR)
- [ ] `:feature:search:impl` suite + screenshot validate + lint (translations) green; `:app:assembleDebug` + `:app:assembleBenchDebug` link; spotless + checkSort clean.
- [ ] **Bench smoke:** `:app:installBenchDebug` → Search tab → type a query → tap a post result's **like** (fills, count +1, sticks offline) + **repost** + **overflow → Mute/Report/Block** (mute removes-or-no-ops, report/block open the dialog) + **reply/quote** (composer opens) — no FATAL.
- [ ] Compose gate: adds `@Composable` wiring + new callbacks → run the compose-expert review before pushing.
- [ ] **PR opens WITH the `update-baselines` label** (search rendering legitimately changed) + `Refs: nubecita-58dy` (epic continues to PR3/PR4).
- [ ] Acceptance: the Search Posts tab cards are fully interactive — like/repost/reply/quote/share work (via the shared cache, so a like propagates to the feed/profile), overflow does real mute + report/block nav, all with `source_surface=search`; the "buttons render but do nothing" bug is fixed; the shared helper is validated on a from-scratch new consumer.

## Closes
Progresses `nubecita-76gd` (PR2 of epic `nubecita-58dy`).
