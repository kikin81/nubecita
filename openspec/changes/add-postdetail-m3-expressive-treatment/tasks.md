## 1. Design system: multi-image carousel in PostCard

- [ ] 1.1 Add `androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel` usage inside `:designsystem` `PostCard`'s existing image-embed branch — conditional on `images.size > 1`. Single-image path unchanged. (Test: new screenshot fixture under `:designsystem/src/screenshotTest/` for a 3-image carousel; existing single-image PostCard fixtures must regenerate byte-for-byte unchanged.)
- [ ] 1.2 Add a screenshot-test fixture under `:designsystem` rendering a 3-image PostCard via the carousel for regression coverage. Confirm the `feature-feed` PostCard fixtures (single-image) remain unchanged after the swap.
- [ ] 1.3 Add a screenshot-test fixture under `:designsystem` for a mixed-aspect-ratio (1 portrait + 2 landscape) 3-image carousel to lock per-slide sizing behavior.
- [ ] 1.4 Run `./gradlew :designsystem:validateDebugScreenshotTest :feature:feed:impl:validateDebugScreenshotTest` and confirm only the new multi-image fixtures changed; every single-image fixture stays untouched.

## 2. PostDetailScreen: Focus Post container hierarchy

- [ ] 2.1 In `PostDetailScreen`, wrap the rendering of `ThreadItem.Focus` in a `Surface` with `color = MaterialTheme.colorScheme.surfaceContainerHigh` and `shape = RoundedCornerShape(24.dp)`. Default elevation; no custom shadow. Ancestors / Replies / Folds keep their existing PostCard rendering on the default `surface` background.
- [ ] 2.2 Add a screenshot-test fixture under `feature/postdetail/impl/src/screenshotTest/` rendering a Focus + ancestors + replies thread under `NubecitaTheme(darkTheme = false)`.
- [ ] 2.3 Add the paired fixture under `NubecitaTheme(darkTheme = true)`. The light/dark pair is the visual contract this change ships — both must be present.
- [ ] 2.4 Spot-check the 24dp shape doesn't clip PostCard's internal padding awkwardly. If it does, fall back to 20dp before considering any custom drawing (tracked in `design.md` Risks).

## 3. PostDetailScreen: floating reply composer

- [ ] 3.1 Add a `FloatingActionButton` (M3 Expressive variant if available at the catalog's material3 version, else the standard variant) to `PostDetailScreen`'s `Scaffold` `floatingActionButton` slot. Always visible — no hide-on-scroll.
- [ ] 3.2 Wire the FAB tap to push the composer NavKey shipped by `nubecita-8f6.3` via `LocalMainShellNavState.current.add(...)` — same call shape as the PostCard reply button.
- [ ] 3.3 Add a unit test (or screen-level instrumentation test, if cheaper) confirming the FAB exists in the semantics tree and is tappable independent of scroll position.
- [ ] 3.4 Add the FAB to the screenshot-test fixtures (it should appear in the focused-post-with-ancestors and with-replies snapshots).

## 4. PostDetailScreen: image tap → media-viewer effect

- [ ] 4.1 Add `PostDetailEffect.NavigateToMediaViewer(uri: AtUri, imageIndex: Int)` to the `PostDetailContract.kt` effect sealed type.
- [ ] 4.2 Wire the image tap inside the Focus PostCard's image embed (single-image path) to dispatch the effect with `imageIndex = 0`.
- [ ] 4.3 Wire the image tap inside each multi-image carousel slide (in `:designsystem` PostCard) to dispatch the effect with the slide index. (May require a callback parameter on PostCard's image-embed branch — keep PostCard's caller-facing signature unchanged unless a callback addition is unavoidable; in that case, default it to a no-op so existing call sites don't break.)
- [ ] 4.4 In `PostDetailScreen`'s effect collector, handle `NavigateToMediaViewer` by pushing the media-viewer `NavKey` if it exists in `:core:common:navigation`. If it does NOT exist, log a Timber debug entry tagged `PostDetailScreen` and treat the effect as a no-op.
- [ ] 4.5 File a follow-up bd issue for the missing fullscreen media viewer route (from main, not from inside the worktree). Cross-reference its bd id in this change's PR description.
- [ ] 4.6 Add a unit test covering the no-op-and-log path so the missing-route fallback stays correct.

## 5. PostDetailScreen: pull-to-refresh

- [ ] 5.1 Wrap `PostDetailScreen`'s `LazyColumn` in `androidx.compose.material3.pulltorefresh.PullToRefreshBox` mirroring `feature/feed/impl/.../FeedScreen.kt` line for line.
- [ ] 5.2 Confirm pulling triggers `PostDetailEvent.Refresh` and the indicator anchors at the top of the screen content (above ancestors).
- [ ] 5.3 Verify the indicator does NOT visually collide with the focus container's `surfaceContainerHigh` background in either theme. If it does, push the pull-to-refresh wiring to a follow-up issue rather than compromise the container hierarchy (tracked in `design.md` Risks).
- [ ] 5.4 Add a unit / instrumentation test confirming pull dispatches `PostDetailEvent.Refresh`.

## 6. Screenshot suite completion

- [ ] 6.1 Add focused-post-with-ancestors fixture (light + dark themes).
- [ ] 6.2 Add with-replies fixture (focus + 4 replies; light only is fine — ancestors/replies use `surface` already covered by the light/dark pair from task 2.3).
- [ ] 6.3 Add single-post-no-thread fixture (focus only, no ancestors, no replies).
- [ ] 6.4 Add blocked-root-fallback fixture (`PostDetailLoadStatus.BlockedRoot` placeholder rendering).
- [ ] 6.5 Add multi-image-carousel-at-focus fixture (focus post with 3-image carousel).
- [ ] 6.6 Run `./gradlew :feature:postdetail:impl:validateDebugScreenshotTest` and commit baselines.

## 7. Verification + ship

- [ ] 7.1 `./gradlew :designsystem:testDebugUnitTest :designsystem:validateDebugScreenshotTest` clean.
- [ ] 7.2 `./gradlew :feature:postdetail:impl:testDebugUnitTest :feature:postdetail:impl:validateDebugScreenshotTest` clean.
- [ ] 7.3 `./gradlew :feature:feed:impl:validateDebugScreenshotTest` clean — single-image PostCard fixtures unchanged from this PR's diff.
- [ ] 7.4 `./gradlew :app:assembleDebug spotlessCheck lint` clean.
- [ ] 7.5 Manual: tap a feed multi-image post → carousel renders at focus; light + dark themes both show clear focus-vs-ancestor hierarchy; FAB is always visible; pull-to-refresh works; tap on image either opens viewer or no-ops cleanly.
- [ ] 7.6 PR description references this openspec change name (`add-postdetail-m3-expressive-treatment`), the bd id (`Closes: nubecita-m28.5.2`), and links the follow-up bd issue from task 4.5.
