## 0. Mapping extraction (`:core:feed-mapping`)

- [x] 0.1 Create `:core:feed-mapping` Android library module: `core/feed-mapping/build.gradle.kts` mirroring `:core:auth`'s convention-plugin shape (no Compose, no Hilt — pure JVM-friendly mapping logic), `core/feed-mapping/src/main/kotlin/net/kikin/nubecita/core/feedmapping/` package.
- [x] 0.2 Add `:core:feed-mapping` to `settings.gradle.kts`.
- [x] 0.3 Move from `:feature:feed:impl/data/FeedViewPostMapper.kt` to `:core:feed-mapping`: the `toEmbedUi` dispatch, the three private wrapper-construction helpers (`ImagesView.toEmbedUiImages`, `VideoView.toEmbedUiVideo`, `ExternalView.toEmbedUiExternal`), `RecordViewRecord.toEmbedUiRecord`, `toAuthorUi`, `toViewerStateUi`, and a new top-level `toPostUiCore(postView: PostView): PostUi?` that captures the per-post projection logic the existing `FeedViewPost.toPostUiOrNull` performs (without the feedViewPost-specific `repostedBy` / `reason` extraction).
- [x] 0.4 Update `:feature:feed:impl/data/FeedViewPostMapper.kt` to add `implementation(project(":core:feed-mapping"))`, import the moved helpers, and shrink `FeedViewPost.toPostUiOrNull` to: call `toPostUiCore(this.post)`, then layer in the feed-specific bits (`repostedBy`, `reason`).
- [x] 0.5 Update `:feature:postdetail:impl/data/PostThreadMapper.kt` to add `implementation(project(":core:feed-mapping"))`, import the moved helpers, and replace the hardcoded `embed = EmbedUi.Empty` projection with `embed = toEmbedUi(view.post.embed)`. Remove the m28.5.1 KDoc section "Why the embed slot is collapsed to [EmbedUi.Empty]" — its work is done.
- [x] 0.6 Run `./gradlew :feature:feed:impl:testDebugUnitTest :feature:feed:impl:validateDebugScreenshotTest` — both must pass without baseline regeneration. The feed's behavior is the regression contract.
- [x] 0.7 Run `./gradlew :feature:postdetail:impl:testDebugUnitTest` — m28.5.1's existing mapper tests update only where they previously asserted `embed == EmbedUi.Empty`; tests asserting non-embed projection behavior MUST stay unchanged.
- [x] 0.8 Add a `:core:feed-mapping` unit test against shared embed fixtures: same JSON in, same `EmbedUi` out. The test fixture pool can mirror the existing feed mapper fixtures.

## 1. Design system: multi-image carousel + per-image-index callback in PostCard

- [x] 1.1 Add `androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel` usage inside `:designsystem` `PostCard`'s existing image-embed branch — conditional on `images.size > 1`. Single-image path unchanged. The carousel uses M3's default `preferredItemWidth` token (do NOT attempt to clone the single-image path's `fillMaxWidth() + heightIn(max = EMBED_HEIGHT)` sizing — they're not equivalent surfaces).
- [x] 1.2 Add an additive `onImageClick: (imageIndex: Int) -> Unit = {}` parameter to PostCard (default no-op). Wire it from the single-image path (`imageIndex = 0`) and from each carousel slide (`imageIndex = slide.index`). Existing call sites compile unchanged.
- [x] 1.3 Add a screenshot-test fixture under `:designsystem` rendering a 3-image PostCard via the carousel. Confirm `:feature:feed:impl`'s single-image PostCard fixtures remain byte-for-byte unchanged after the swap.
- [x] 1.4 Add a screenshot-test fixture under `:designsystem` for a mixed-aspect-ratio (1 portrait + 2 landscape) 3-image carousel to lock per-slide sizing behavior.
- [x] 1.5 Run `./gradlew :designsystem:validateDebugScreenshotTest :feature:feed:impl:validateDebugScreenshotTest` and confirm only the new multi-image fixtures changed; every single-image fixture stays untouched.

## 2. PostDetailScreen: Focus Post container hierarchy

- [x] 2.1 In `PostDetailScreen`, wrap the rendering of `ThreadItem.Focus` in a `Surface` with `color = MaterialTheme.colorScheme.surfaceContainerHigh` and `shape = RoundedCornerShape(24.dp)`. Default elevation; no custom shadow. Ancestors / Replies / Folds keep their existing PostCard rendering on the default `surface` background.
- [x] 2.2 Add a screenshot-test fixture under `feature/postdetail/impl/src/screenshotTest/` rendering a Focus + ancestors + replies thread under `NubecitaTheme(darkTheme = false)`.
- [x] 2.3 Add the paired fixture under `NubecitaTheme(darkTheme = true)`. The light/dark pair is the visual contract this change ships — both must be present.
- [x] 2.4 Spot-check the 24dp shape doesn't clip PostCard's internal padding awkwardly. If it does, fall back to 20dp before considering any custom drawing (tracked in `design.md` Risks).

## 3. PostDetailScreen: floating reply composer

- [x] 3.1 Add a `FloatingActionButton` (M3 Expressive variant if available at the catalog's material3 version, else the standard variant) to `PostDetailScreen`'s `Scaffold` `floatingActionButton` slot. Always visible — no hide-on-scroll.
- [x] 3.2 Wire the FAB tap to emit a `PostDetailEffect.NavigateToComposer(...)` (or whichever existing nav-effect shape is the closest match) that the screen's effect collector pushes via `LocalMainShellNavState.current.add(<composer NavKey from nubecita-8f6.3>)`. (No "PostCard reply button" pattern to mirror — both the feed VM's `OnReplyClicked` and the post-detail screen's reply slot are no-ops in m28.5.1; this task establishes the first reply-navigation implementation.)
- [x] 3.3 Apply bottom `contentPadding` to the LazyColumn equal to FAB height + standard edge spacing (target ~80–100dp combined) so the FAB never occludes the bottom-most reply when the user scrolls to the end of the thread. (Test: the with-replies screenshot fixture from task 6.2 must show the last reply's action row fully visible above the FAB at the resting end-of-thread scroll position — capture at the scroll-end position, not just at the top.)
- [x] 3.4 Add a unit test (or screen-level instrumentation test, if cheaper) confirming the FAB exists in the semantics tree and is tappable independent of scroll position.
- [x] 3.5 Add the FAB to the screenshot-test fixtures (it should appear in the focused-post-with-ancestors and with-replies snapshots).

## 4. PostDetailScreen: image tap → media-viewer effect

- [x] 4.1 Add `PostDetailEffect.NavigateToMediaViewer(postUri: String, imageIndex: Int)` to the `PostDetailContract.kt` effect sealed type. Note the `String` URI shape — matches `PostDetailRoute.postUri` and the rest of the screen's effect surface (`NavigateToPost`, `NavigateToAuthor`); call sites construct `AtUri(...)` only at the XRPC boundary if needed.
- [x] 4.2 Pass the new PostCard `onImageClick` callback (added in task 1.2) from the Focus PostCard call site to dispatch `NavigateToMediaViewer(post.id, imageIndex)`.
- [x] 4.3 Verify ancestor / reply PostCard call sites do NOT pass the `onImageClick` callback — taps on ancestor/reply images stay no-op for v1 (the design intent's media-viewer scope is the Focus post only; ancestor/reply images stay un-interactive until a follow-up changes that).
- [x] 4.4 In `PostDetailScreen`'s effect collector, handle `NavigateToMediaViewer` by pushing the media-viewer `NavKey` if it exists in `:core:common:navigation`. If it does NOT exist, (a) log a Timber debug entry tagged `PostDetailScreen` AND (b) show a transient Snackbar on the screen's `SnackbarHostState` reading "Fullscreen viewer coming soon" (or equivalent localized string). The Snackbar gives tactile feedback that the tap registered without blocking the user the way a dialog would. (Test: unit test for the missing-route branch confirms both the Timber log AND the Snackbar dispatch.)
- [x] 4.5 File a follow-up bd issue for the missing fullscreen media viewer route (from main, not from inside the worktree). Cross-reference its bd id in this change's PR description and in the Timber/Snackbar code comments so the future-removal site is grep-able. (Filed: nubecita-e02.)
- [x] 4.6 Add a unit test covering the missing-route fallback path so the Snackbar + Timber pair stays correct as a regression baseline.

## 5. Pull-to-refresh: verification only (already shipped in m28.5.1)

`PullToRefreshBox` already wraps `PostDetailScreen`'s `LazyColumn` and is bound to `PostDetailEvent.Refresh` (m28.5.1, already on `main`). This change does NOT re-implement it — only verifies the existing pull-to-refresh experience survives the new container hierarchy.

- [ ] 5.1 Verify the existing `PullToRefreshBox` indicator remains visible at the resting "pulled" position when overlaid against the new `surfaceContainerHigh` Focus container — the indicator's contrast against the slightly-elevated container surface is the regression risk called out in `design.md` Decision 5 / Risks.
- [ ] 5.2 Add a screenshot-test fixture capturing the pull-to-refresh indicator at the visible-pull position (mid-pull, indicator showing) on top of the Focus container — paired across light and dark themes if the indicator's appearance differs.
- [ ] 5.3 If the indicator visually blends into the focus container's background in either theme, the fix is to surface a follow-up bd issue (e.g. tint the indicator or shift the focus container's elevation) rather than reverting the container hierarchy. The container hierarchy is the visual contract this change ships; pull-to-refresh adapts to it.

## 6. Screenshot suite completion

- [ ] 6.1 Add focused-post-with-ancestors fixture (light + dark themes).
- [ ] 6.2 Add with-replies fixture at end-of-thread scroll position so the FAB-vs-bottom-reply contentPadding from task 3.3 is captured (light only is fine — light/dark contrast pair is covered by task 2.2/2.3).
- [ ] 6.3 Add single-post-no-thread fixture (focus only, no ancestors, no replies).
- [ ] 6.4 Add blocked-root fixture rendering a single `ThreadItem.Blocked` row (NOT a top-level `PostDetailLoadStatus.BlockedRoot` placeholder — that variant doesn't exist; blocked roots are surfaced as a Blocked row per the m28.5.1 contract).
- [ ] 6.5 Add multi-image-carousel-at-focus fixture (focus post with 3-image carousel) confirming the embed-mapping work from task 0.5 produces a non-Empty `EmbedUi.Images` for the focus post.
- [ ] 6.6 Add the pull-to-refresh-indicator-vs-focus-container fixture from task 5.2.
- [ ] 6.7 Run `./gradlew :feature:postdetail:impl:validateDebugScreenshotTest` and commit baselines.

## 7. Verification + ship

- [ ] 7.1 `./gradlew :core:feed-mapping:testDebugUnitTest` clean.
- [ ] 7.2 `./gradlew :designsystem:testDebugUnitTest :designsystem:validateDebugScreenshotTest` clean.
- [ ] 7.3 `./gradlew :feature:postdetail:impl:testDebugUnitTest :feature:postdetail:impl:validateDebugScreenshotTest` clean.
- [ ] 7.4 `./gradlew :feature:feed:impl:testDebugUnitTest :feature:feed:impl:validateDebugScreenshotTest` clean — feed unit tests AND screenshot fixtures unchanged from this PR's diff (the regression contract for the mapping extraction).
- [ ] 7.5 `./gradlew :app:assembleDebug spotlessCheck lint` clean.
- [ ] 7.6 Manual: tap a feed multi-image post → carousel renders at focus (only possible after task 0.5); light + dark themes both show clear focus-vs-ancestor hierarchy; FAB is always visible; bottom-most reply scrolls fully above the FAB; pull-to-refresh works and indicator stays visible against focus container; tap on focus image surfaces the "coming soon" Snackbar; tap on ancestor/reply images is a no-op.
- [ ] 7.7 PR description references this openspec change name (`add-postdetail-m3-expressive-treatment`), the bd id (`Closes: nubecita-m28.5.2`), and links the follow-up bd issue from task 4.5.
