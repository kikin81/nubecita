# Post-Interaction Consolidation — PR1 (Extract + Prove on Feed) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Extract a shared `PostInteractionHandler` delegate + a `rememberPostInteractions` Compose helper, add bench-flavor fakes so like/repost work offline, and migrate the **feed** onto them — proven by the existing feed tests passing unchanged and byte-identical feed screenshots.

**Architecture:** A plain (non-VM) `PostInteractionHandler` in `:core:post-interactions`, adopted by each VM via Kotlin `by` delegation (per-VM `surface`/`scope` supplied via a two-phase `bind()` in `init`). It writes through the existing `@Singleton PostInteractionsCache`, emits an `InteractionEffect` `Flow` collected **directly** by `rememberPostInteractions` (new `:core:post-interactions-ui` module) which owns nav/share/snackbar. Feed is migrated first because it has a byte-identical oracle (full test suite + committed screenshots).

**Tech Stack:** Kotlin, Hilt (`@Binds`, AGP source-set DI split for bench), Coroutines/Flow, Jetpack Compose + M3, JUnit5 + MockK + Turbine + `RecordingAnalyticsClient`, screenshot tests.

**Design spec:** `docs/superpowers/specs/2026-06-29-post-interaction-consolidation-design.md`.

## PREREQUISITE

**nubecita-ag2s (PR #629) must be merged to `main` before starting** — Task 4 migrates the feed *off* `feature/feed/impl/.../ui/FeedInteractions.kt` (`rememberFeedInteractions`), which that PR introduces. Branch PR1 off fresh `main` after #629 lands. (Reference shape from #629: `data class FeedInteractions(callbacks: PostCallbacks, onImageTap, onVideoTap, onRefresh, onRetry, onLoadMore, coordinator: FeedVideoPlayerCoordinator?)` returned by `rememberFeedInteractions(viewModel, snackbarHostState, haptics, onReplyClick, onQuoteClick, onNavigateToPost, onNavigateToAuthor, onNavigateToMediaViewer, onNavigateToVideoPlayer, onNavigateTo)`.)

## Global Constraints

- **Behavior-preserving on the feed.** Existing `FeedViewModelTest` + the whole `:feature:feed:impl` suite pass UNCHANGED; `FeedScreen` + `FeedViewScreen` screenshots stay BYTE-IDENTICAL (the stateless `FeedScreenContent`/`FeedViewScreenContent` are not touched — that is the proof). Do not regenerate baselines; a diff = a real behavior change to fix.
- **No network in bench.** The bench fakes return `Result.success` with no I/O; the bench DI split mirrors `:core:actors` exactly (production module at `src/production/.../internal/…`, bench at `src/bench/.../internal/…`, **same FQN**, AGP picks one per variant).
- **PII-free analytics:** `InteractPost(action, surface)` / `Share(method, surface)` carry enums only; logged on the success path; `surface = PostSurface.Feed` for the feed.
- **Per-uri tap guard:** `onLike`/`onRepost` track the active `Job` per post URI and ignore a repeat tap while in flight (the `activeJobs[uri]?.isActive == true` pattern from `DiscoverViewModel`/`FeedPinViewModel`).
- **MVI:** the `by`-delegated interaction methods are the second sanctioned exception (documented in CLAUDE.md, Task 4). The VM keeps `handleEvent` for screen-specific events.
- Conventional Commits, **lowercase subject**; `Refs: nubecita-58dy`; no Co-Authored-By/Claude-Session footer; never `--no-verify`. No new user-facing strings in PR1 (feed already owns all snackbar strings), so no translation work.

---

### Task 1: `PostInteractionHandler` delegate + `InteractionEffect`/`InteractionError` (`:core:post-interactions`)

**Files:**
- Create: `core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/PostInteractionHandler.kt` (interface + `PostTapMarkers` + `InteractionEffect` + `InteractionError`)
- Create: `core/post-interactions/src/main/kotlin/net/kikin/nubecita/core/postinteractions/internal/DefaultPostInteractionHandler.kt`
- Modify: `core/post-interactions/src/main/kotlin/.../di/PostInteractionsModule.kt` (`@Binds` `DefaultPostInteractionHandler` → `PostInteractionHandler`, **unscoped** — each VM gets its own instance)
- Test: `core/post-interactions/src/test/kotlin/.../internal/DefaultPostInteractionHandlerTest.kt`

**Interfaces — Produces:**
```kotlin
data class PostTapMarkers(val lastLikeTapPostUri: String? = null, val lastRepostTapPostUri: String? = null)

enum class InteractionError { Network, Unauthenticated, Unknown }

sealed interface InteractionEffect {
    data class ShowError(val error: InteractionError) : InteractionEffect
    data class SharePost(val intent: PostShareIntent) : InteractionEffect
    data class CopyPermalink(val permalink: String) : InteractionEffect
    data class NavigateToComposer(val replyToUri: String?, val quoteUri: String?) : InteractionEffect
    data class NavigateToReport(val post: PostUi) : InteractionEffect          // UI module builds Report.forPost(post)
    data class NavigateToBlock(val did: String, val handle: String) : InteractionEffect
    data class ShowComingSoon(val action: PostOverflowAction) : InteractionEffect
}

interface PostInteractionHandler {
    fun bind(surface: PostSurface, scope: CoroutineScope)   // call once in the VM's init
    val tapMarkers: StateFlow<PostTapMarkers>
    val interactionEffects: Flow<InteractionEffect>
    fun onLike(post: PostUi); fun onRepost(post: PostUi)
    fun onReply(post: PostUi); fun onQuote(post: PostUi)
    fun onShare(post: PostUi); fun onShareLongPress(post: PostUi)
    fun onOverflowAction(post: PostUi, action: PostOverflowAction)
}
```
**Consumes:** `PostInteractionsCache` (`val state`, `suspend fun toggleLike(postUri, postCid): Result<Unit>`, `toggleRepost(...)`), `MuteRepository` (`suspend fun muteActor(did): Result<Unit>`, `unmuteActor(did)`), `AnalyticsClient.log(...)`, `InteractPost(action: PostAction, surface: PostSurface)`, `Share(method: ShareMethod, surface)`, `post.toShareIntent()` (in `:core:post-interactions/sharing`), `PostOverflowAction` (`ReportPost / MuteAuthor / UnmuteAuthor / BlockAuthor / UnblockAuthor / MuteThread / UnmuteThread / CopyPostText`).

- [ ] **Step 1 — failing tests.** `DefaultPostInteractionHandlerTest` (`@ExtendWith(MainDispatcherExtension)`, MockK on cache + `MuteRepository`, `RecordingAnalyticsClient`, Turbine on `interactionEffects`, a `TestScope` passed to `bind`). Mirror the existing `FeedViewModelTest` analytics assertions. Assert: after `bind(PostSurface.Feed, scope)` — `onLike(unliked)` → `cache.toggleLike(id,cid)` called once + `analytics` == `[InteractPost(Like, Feed)]` + `tapMarkers.value.lastLikeTapPostUri == id`; `onLike(liked)` → `InteractPost(Unlike, Feed)`; **two rapid `onLike(same)` before the toggle completes → `toggleLike` + analytics fire exactly once** (per-uri guard); `onRepost` symmetric; a `toggleLike` failure → emits `InteractionEffect.ShowError`; `onShare` → `SharePost`; `onShareLongPress` → `CopyPermalink`; `onReply` → `NavigateToComposer(replyToUri=id, quoteUri=null)`; `onQuote` → `NavigateToComposer(null, id)`; `onOverflowAction(_, MuteAuthor)` → `muteRepository.muteActor(did)`; `ReportPost` → `NavigateToReport(post)`; `BlockAuthor` → `NavigateToBlock(did, handle)`; `UnblockAuthor/MuteThread/UnmuteThread/CopyPostText` → `ShowComingSoon(action)`; analytics only on success.
- [ ] **Step 2 — run, watch fail** (`./gradlew :core:post-interactions:testDebugUnitTest`).
- [ ] **Step 3 — implement** `DefaultPostInteractionHandler @Inject constructor(cache, muteRepository, analytics)`: `bind` stores `surface` + `scope` (lateinit); `tapMarkers` a `MutableStateFlow`; `interactionEffects` a `MutableSharedFlow(extraBufferCapacity=…)` or `Channel.receiveAsFlow()` (mirror how the cache/VMs expose one-shot effects — pick the buffered, never-suspend-emitter shape). `onLike`: per-uri `activeJobs` guard → read `isLikedByViewer` for the `PostAction` → `analytics.log(InteractPost(action, surface))` → set `tapMarkers` → `scope.launch { cache.toggleLike(post.id, post.cid).onFailure { emit ShowError(it.toInteractionError()) } }`. The overflow `when` mirrors `FeedViewModel.OnOverflowAction` (mute=real, report/block=nav-effect, rest=coming-soon) but emits *data* effects (no NavKey construction here — keeps the module `:feature:*:api`-free).
- [ ] **Step 4 — green** + the `@Binds` (unscoped) in `PostInteractionsModule`.
- [ ] **Step 5 — commit** `feat(post-interactions): add PostInteractionHandler delegate + InteractionEffect\n\nRefs: nubecita-58dy`

---

### Task 2: Bench fakes for the like/repost (+ follow) write path (`:core:post-interactions`)

**Files:**
- Create: `core/post-interactions/src/bench/kotlin/.../internal/BenchFakeLikeRepostRepository.kt`, `BenchFakeFollowRepository.kt`
- Create: `core/post-interactions/src/production/kotlin/.../di/PostInteractionsWriteModule.kt` (binds `DefaultLikeRepostRepository` + `DefaultFollowRepository`)
- Create: `core/post-interactions/src/bench/kotlin/.../di/PostInteractionsWriteModule.kt` (**same FQN**; binds the bench fakes)
- Modify: `core/post-interactions/src/main/.../di/PostInteractionsModule.kt` — **remove** `bindLikeRepostRepository` + `bindFollowRepository` (they move to the flavored `PostInteractionsWriteModule`); keep the cache + `SessionClearable` bindings in `src/main`.

**Pattern to mirror exactly:** `core/actors/src/{production,bench}/.../internal/ActorsModule.kt` + `core/actors/src/bench/.../internal/BenchFakeMuteRepository.kt` (read both). `LikeRepostRepository` = `like(StrongRef): Result<AtUri>`, `unlike(AtUri): Result<Unit>`, `repost(StrongRef): Result<AtUri>`, `unrepost(AtUri): Result<Unit>`. `FollowRepository` = `follow(did): Result<String>`, `unfollow(followUri): Result<Unit>`.

- [ ] **Step 1 — fakes.** `@Singleton internal class BenchFakeLikeRepostRepository @Inject constructor() : LikeRepostRepository` returning `Result.success(AtUri("at://bench/like/${'$'}{…}"))` for like/repost and `Result.success(Unit)` for unlike/unrepost (a non-blank synthetic AtUri so the cache's optimistic state has a viewer-record uri). `BenchFakeFollowRepository` symmetric (`follow` → `Result.success("at://bench/follow/…")`).
- [ ] **Step 2 — DI split.** Move the two write bindings out of the `src/main` module into the new `src/production` + `src/bench` `PostInteractionsWriteModule` (same FQN, `@InstallIn(SingletonComponent)`), production binding the `Default*`, bench binding the `BenchFake*`. Add the explanatory KDoc that `:core:actors`' module carries (AGP source-set selection; FQN-collision is intentional).
- [ ] **Step 3 — verify.** `./gradlew :core:post-interactions:assembleBenchDebug :app:assembleBenchDebug` (bench graph compiles with the fakes) **and** `:core:post-interactions:testDebugUnitTest` (production-path unit tests unaffected — the `src/test` `FakeLikeRepostRepository` is separate from the new bench fake).
- [ ] **Step 4 — commit** `feat(post-interactions): bench fakes for like/repost + follow write path\n\nRefs: nubecita-58dy`

---

### Task 3: `:core:post-interactions-ui` module + `rememberPostInteractions` (new module)

**Files:**
- Create: `core/post-interactions-ui/build.gradle.kts`, `core/post-interactions-ui/src/main/AndroidManifest.xml` (if the convention needs it — check `:core:common`)
- Modify: `settings.gradle.kts` — add `include(":core:post-interactions-ui")` (next to `:core:post-interactions`)
- Create: `core/post-interactions-ui/src/main/kotlin/net/kikin/nubecita/core/postinteractions/ui/PostInteractions.kt` (`data class PostInteractions` + `rememberPostInteractions`)
- Test/preview: covered indirectly by the feed migration's screenshots; add a small JVM test only if a pure mapping function is extracted.

**build.gradle.kts** (mirror `core/common/build.gradle.kts`): `plugins { alias(libs.plugins.nubecita.android.library.compose) }` (no Hilt — the helper takes the handler as a param); `namespace = "net.kikin.nubecita.core.postinteractions.ui"`; deps: `implementation(project(":core:post-interactions"))`, `implementation(project(":designsystem"))` (for `PostCallbacks`), `implementation(project(":core:common"))` (for `LocalMainShellNavState`), `implementation(project(":feature:composer:api"))` (`ComposerRoute`), `implementation(project(":feature:moderation:api"))` (`Report` / `Block`), the compose BOM/runtime/material3, `androidx.navigation3.runtime`. Run `:app:checkSortDependencies` after.

**Interfaces — Produces:**
```kotlin
data class PostInteractions(val callbacks: PostCallbacks, val tapMarkers: PostTapMarkers)

/** All snackbar copy the helper may show, pre-resolved via stringResource by the caller
 *  and passed in — so each surface keeps its OWN existing strings (PR1: the feed passes
 *  its current R.string.feed_snackbar_* values, keeping snackbar text byte-identical;
 *  later PRs pass theirs). Fields: errorNetwork, errorUnauthenticated, errorUnknown,
 *  linkCopied, clipLabel, + one per coming-soon PostOverflowAction. */
data class InteractionStrings(/* the fields above */)

@Composable
fun rememberPostInteractions(           // public — crosses the module boundary
    handler: PostInteractionHandler,
    snackbarHostState: SnackbarHostState,
    strings: InteractionStrings,
): PostInteractions
```
**Consumes:** `PostInteractionHandler` + `InteractionEffect` (Task 1), `PostCallbacks` (`:designsystem`), `LocalMainShellNavState`, `Report.forPost(post)` / `Block.forAccount(did, handle)` (`:feature:moderation:api`), `ComposerRoute(replyToUri=…, quotePostUri=…)` (`:feature:composer:api`), `context.launchPostShare(intent)`.

- [ ] **Step 1 — scaffold the module** (build.gradle.kts + settings include) and confirm it configures: `./gradlew :core:post-interactions-ui:help`.
- [ ] **Step 2 — implement `rememberPostInteractions`.** Build the `PostCallbacks` `remember`-keyed on `handler`, every slot wired to `handler.onXxx` (this is the generalization of `rememberFeedInteractions`' callbacks block — read it for the exact slot wiring). Collect `tapMarkers` via `collectAsStateWithLifecycle`. One `LaunchedEffect(handler, snackbarHostState) { handler.interactionEffects.collect { … } }` mapping: `ShowError` → snackbar (replace-not-stack: `currentSnackbarData?.dismiss()` then `showSnackbar`); `SharePost` → `context.launchPostShare`; `CopyPermalink` → clipboard + snackbar; `ShowComingSoon` → snackbar; `NavigateToComposer` → `navState.add(ComposerRoute(replyToUri, quotePostUri))`; `NavigateToReport` → `navState.add(Report.forPost(post))`; `NavigateToBlock` → `navState.add(Block.forAccount(did, handle))`. Return `PostInteractions(callbacks, tapMarkers)`.
- [ ] **Step 3 — green** `./gradlew :core:post-interactions-ui:assembleDebug :app:checkSortDependencies spotlessApply`.
- [ ] **Step 4 — commit** `feat(post-interactions-ui): rememberPostInteractions helper module\n\nRefs: nubecita-58dy`

---

### Task 4: Migrate the feed onto the shared handler + document the MVI exception

**Files:**
- Modify: `feature/feed/impl/.../FeedViewModel.kt` — `: MviViewModel<…>(), PostInteractionHandler by handler`; `@Inject` the handler; `init { handler.bind(PostSurface.Feed, viewModelScope) }`; **delete** the `OnLikeClicked/OnRepostClicked/OnShareClicked/OnShareLongPressed/OnOverflowAction` handling + the `lastLikeTapPostUri`/`lastRepostTapPostUri` state + the `boundSurface` analytics plumbing (now in the delegate); KEEP the `cache.state → feedItems.applyInteractions` merge collector and all pagination/Bind/Load/mute-list-mutation logic.
- Modify: `feature/feed/impl/.../FeedScreen.kt` + `FeedViewScreen.kt` — replace `rememberFeedInteractions(...)` interaction usage with `rememberPostInteractions(vm, snackbarHostState, InteractionStrings(...))` where the `InteractionStrings` is built from the feed's **existing** `R.string.feed_snackbar_*` / `feed_clipboard_*` values (resolved via `stringResource`, exactly as `rememberFeedInteractions` does today — so snackbar text is unchanged); source `callbacks`/`tapMarkers` from it; KEEP the feed-local video-coordinator wiring (move it inline or into a small `rememberFeedVideoCoordinator` — it is NOT a post-interaction concern).
- Modify/Delete: `feature/feed/impl/.../ui/FeedInteractions.kt` — retire the post-interaction half; keep only the video-coordinator helper if still needed, else delete.
- Modify: `feature/feed/impl/build.gradle.kts` — add `implementation(project(":core:post-interactions-ui"))`.
- Modify: `CLAUDE.md` — document the second sanctioned MVI exception.
- Modify: `feature/feed/impl/.../FeedViewModelTest.kt` only if a test referenced a now-deleted symbol (the *assertions* must not change — interaction analytics now come from the delegate; if a feed test asserted `InteractPost`, either keep it via the delegate path or move that assertion to `DefaultPostInteractionHandlerTest` — do NOT weaken coverage).

**Consumes:** Task 1 (`PostInteractionHandler by`, `handler.bind`), Task 3 (`rememberPostInteractions`).

- [ ] **Step 1 — migrate `FeedViewModel`** to `by handler` + `bind`, removing the now-delegated handlers/state. Compile: `./gradlew :feature:feed:impl:compileProductionDebugKotlin`.
- [ ] **Step 2 — migrate `FeedScreen` + `FeedViewScreen`** to `rememberPostInteractions`; relocate the video-coordinator wiring; ensure `FeedScreenContent`/`FeedViewScreenContent` (stateless) are **untouched**.
- [ ] **Step 3 — run the feed suite UNCHANGED.** `./gradlew :feature:feed:impl:testProductionDebugUnitTest` — all green with no assertion edits (only symbol-reference fixes allowed). If an `InteractPost` assertion moved to the delegate test, confirm the delegate test covers it.
- [ ] **Step 4 — screenshots BYTE-IDENTICAL.** `./gradlew :feature:feed:impl:validateProductionDebugScreenshotTest` — pass without regenerating. A diff means rendering changed: fix the migration (the `*Content` inputs must be identical), do not update baselines.
- [ ] **Step 5 — CLAUDE.md.** Under the MVI conventions, add the second sanctioned exception: "`by`-delegated post-interaction methods (`PostInteractionHandler`) — interaction events (`onLike/onRepost/onShare/onOverflow/onReply/onQuote`) are called directly on the VM via Kotlin `by` delegation, bypassing `handleEvent`. Bounded like the `TextFieldState` carve-out: the VM keeps `handleEvent` for screen-specific events; the delegate owns the shared cache write + analytics + `InteractionEffect`; reference `:core:post-interactions`."
- [ ] **Step 6 — commit** `refactor(feed): migrate to shared PostInteractionHandler + rememberPostInteractions\n\nRefs: nubecita-58dy`

---

## Final verification (before PR)

- [ ] `:core:post-interactions` (delegate + bench assemble), `:core:post-interactions-ui`, `:feature:feed:impl` suites green; `:app:assembleDebug` + `:app:assembleBenchDebug` link; spotless + `:app:lintProductionDebug` + `:feature:feed:impl:lintProductionDebug` + `checkSortDependencies` clean; feed screenshots byte-identical (the faithful-generalization proof).
- [ ] **Bench smoke (the new capability):** `:app:installBenchDebug` on emulator-5554 → open the feed → tap **like** (heart fills, count +1, sticks — no rollback), tap **repost** (toggles), open **overflow → Mute** (post removed), **overflow → Report** + **Block** (moderation dialog opens), **reply/quote** (composer opens) — all offline, no FATAL.
- [ ] Compose gate: Task 3 + Task 4 add/restructure `@Composable`s → run the compose-expert review on the diff before pushing.
- [ ] PR opens with **no `update-baselines` label** (rendering unchanged on purpose); `Refs: nubecita-58dy` (the epic stays open for PR2–4).
- [ ] Acceptance: the feed routes all post interactions through `PostInteractionHandler` + `rememberPostInteractions`; `cache` single-source-of-truth + analytics + mute behavior identical to today (plus the rapid-tap guard); interactions are bench-verifiable; the abstraction is now proven against the feed and ready for PR2 (search).
