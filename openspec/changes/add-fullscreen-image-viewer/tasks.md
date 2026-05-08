## 0. Version catalog: telephoto

- [x] 0.1 Add `telephoto = "0.19.0"` to `[versions]` in `gradle/libs.versions.toml` (latest stable at task-start).
- [x] 0.2 Add `telephoto-zoomable-image-coil3 = { module = "me.saket.telephoto:zoomable-image-coil3", version.ref = "telephoto" }` to `[libraries]`.
- [x] 0.3 Verified via Sonatype Guide: 0.19.0 has 0 CVEs, Apache-2.0, policy-compliant, not malicious, not EOL.

## 1. New module: `:core:posts`

- [x] 1.0.1 Create `core/posts/build.gradle.kts` (library + hilt convention plugins).
- [x] 1.0.2 Register `:core:posts` in `settings.gradle.kts`.
- [x] 1.0.3 Add `PostRepository` interface in `core/posts/.../PostRepository.kt`.
- [x] 1.0.4 Add `DefaultPostRepository` calling `FeedService.getPosts(...)`; projects via `:core:feed-mapping`'s `toPostUiCore`. Empty response → `PostNotFoundException`; null projection → `PostProjectionException`.
- [x] 1.0.5 Add `PostRepositoryModule` Hilt binding.
- [x] 1.0.6 Add `DefaultPostRepositoryTest` with fixtures `getposts_with_three_images.json` + `getposts_empty.json`. Covers success (3-image projection), empty response, network failure, NoSessionException.
- [x] 1.0.7 `./gradlew :core:posts:testDebugUnitTest :core:posts:assembleDebug` — 4/4 tests pass, build green.

## 1. New module: `:feature:mediaviewer:api`

- [x] 1.1 Create `feature/mediaviewer/api/build.gradle.kts` applying the `nubecita.android.library` convention plugin. Namespace `net.kikin.nubecita.feature.mediaviewer.api`. No Compose, no Hilt deps. Adds `implementation(libs.androidx.navigation3.runtime)` (or whatever the existing `:feature:*:api` modules pull in for `NavKey`) and `implementation(libs.kotlinx.serialization.core)`.
- [x] 1.2 Add `feature/mediaviewer/api/src/main/kotlin/net/kikin/nubecita/feature/mediaviewer/api/MediaViewer.kt` containing exactly:
  ```kotlin
  @Serializable
  data class MediaViewerRoute(
      val postUri: String,
      val imageIndex: Int,
  ) : NavKey
  ```
  with KDoc mirroring the `Feed.kt` / `PostDetailRoute.kt` style (purpose, why it lives in `:api`, primitive-string rationale).
- [x] 1.3 Register the new module in `settings.gradle.kts` (insert under the existing `:feature:postdetail:api` entry).
- [x] 1.4 Run `./gradlew :feature:mediaviewer:api:compileDebugKotlin` clean.

## 2. New module: `:feature:mediaviewer:impl`

- [x] 2.1 Create `feature/mediaviewer/impl/build.gradle.kts` applying the `nubecita.android.feature` convention plugin. Namespace `net.kikin.nubecita.feature.mediaviewer.impl`.
- [x] 2.2 Declare deps: `api(project(":feature:mediaviewer:api"))`, `implementation(project(":designsystem"))`, `implementation(project(":data:models"))`, `implementation(project(":core:common"))`, `implementation(project(":core:posts"))`, `implementation(libs.telephoto.zoomable.image.coil3)`, `implementation(libs.coil.compose)`, plus the standard hilt + compose deps the convention plugin already wires.
- [x] 2.3 Register the module in `settings.gradle.kts`.
- [x] 2.4 Add `feature/mediaviewer/impl/src/main/kotlin/.../MediaViewerContract.kt` with `MediaViewerState`, `MediaViewerEvent`, `MediaViewerEffect`, `MediaViewerLoadStatus` per the design's Decision 3 sealed-sum shape. `MediaViewerEvent` covers: `OnPageChanged(index: Int)`, `OnTapImage`, `OnAltBadgeClick`, `OnAltSheetDismiss`, `OnDismissRequest` (back / close / swipe-down), `OnRetry`. `MediaViewerEffect` covers: `Dismiss`, `ShowError(message: UiText)`.
- [x] 2.5 Add `feature/mediaviewer/impl/src/main/kotlin/.../MediaViewerViewModel.kt` extending `MviViewModel<MediaViewerState, MediaViewerEvent, MediaViewerEffect>`. Assisted-injected with `MediaViewerRoute`; constructor-injected with `:core:posts`'s `PostRepository`. `init` launches `postRepository.getPost(route.postUri)`. Read the resulting `PostUi.embed`: when it is `EmbedUi.Images`, project to `ImmutableList<ImageUi>`; when it is any other variant (defensive — viewer was opened on a non-image post), surface a `MediaViewerLoadStatus.Error(UiText.literal("This post has no images"))`. Failure → `Error(UiText.from(throwable))`.
- [x] 2.6 Add `feature/mediaviewer/impl/src/main/kotlin/.../FullsizeUrl.kt` with the internal `ImageUi.fullsizeUrl(): String` helper per design Decision 6. Plain Kotlin file; no Compose / Hilt deps.
- [x] 2.7 Add `feature/mediaviewer/impl/src/main/kotlin/.../MediaViewerScreen.kt`:
  - Accepts a `MediaViewerViewModel` and an `onDismiss: () -> Unit` callback.
  - Outermost composable: `Surface(color = Color.Black)` filling the slot. `BackHandler` collected to dispatch `OnDismissRequest`. `LaunchedEffect` collecting `MediaViewerEffect.Dismiss` → invokes `onDismiss()`; `ShowError` → `SnackbarHost`.
  - Inner: when `Loading`, render a centered `CircularProgressIndicator`; when `Error`, render a `Column` with the error message + a "Retry" button dispatching `OnRetry`; when `Loaded`, render the pager + chrome.
  - Pager: `HorizontalPager(state = rememberPagerState(initialPage = state.currentIndex, pageCount = { state.images.size }), userScrollEnabled = currentZoomFactor <= 1f)`. The pager state's `currentPage` flow is collected and dispatched as `OnPageChanged`.
  - Per-page leaf: `ZoomableAsyncImage(model = images[page].fullsizeUrl(), contentDescription = images[page].altText, contentScale = ContentScale.Fit, onClick = { dispatch(OnTapImage) })`. Wrap in a `Box` carrying a `Modifier.draggable(orientation = Orientation.Vertical)` that translates vertical offset and dispatches `OnDismissRequest` past threshold; the draggable is enabled only when zoom factor is at min.
  - Chrome overlay: `AnimatedVisibility(state.isChromeVisible)` driving a `Row` at the top (close button left dispatching `OnDismissRequest`, "${currentIndex + 1} / ${images.size}" center when `images.size > 1`, `ALT` badge right when `images[currentIndex].altText != null` dispatching `OnAltBadgeClick`).
  - Auto-fade timer: `LaunchedEffect(state.isChromeVisible, state.currentIndex)` that delays 3s and dispatches a `setChromeVisible(false)` event.
  - Alt sheet: `if (state.isAltSheetOpen) ModalBottomSheet(onDismissRequest = { dispatch(OnAltSheetDismiss) }) { Text(images[currentIndex].altText.orEmpty(), modifier = Modifier.verticalScroll(...)) }`.
- [x] 2.8 Add `feature/mediaviewer/impl/src/main/kotlin/.../di/MediaViewerNavigationModule.kt` providing the `@Provides @IntoSet @MainShell EntryProviderInstaller` mirroring `PostDetailNavigationModule` (assisted-inject ViewModel via `hiltViewModel<MediaViewerViewModel, MediaViewerViewModel.Factory>(creationCallback = { it.create(route) })`; `onDismiss = { LocalMainShellNavState.current.removeLast() }`).
- [x] 2.9 Run `./gradlew :feature:mediaviewer:impl:compileDebugKotlin :feature:mediaviewer:impl:assembleDebug` clean.

## 3. Wire `:app` to pick up the entry

- [x] 3.1 Add `implementation(project(":feature:mediaviewer:impl"))` to `app/build.gradle.kts` so Hilt's `@MainShell` multibinding includes the new entry. (Per the convention in `CLAUDE.md` / `build-logic/README.md`, only the `:impl` dep is needed; transitive `:api` flows from there.)
- [x] 3.2 Run `./gradlew :app:assembleDebug` clean — verifies the multibinding compiles without conflict.

## 4. Modify `:feature:postdetail:impl`: hoist callback, remove Snackbar fallback

- [x] 4.1 Add `implementation(project(":feature:mediaviewer:api"))` to `feature/postdetail/impl/build.gradle.kts`.
- [x] 4.2 In `PostDetailScreen.kt`, add a hoisted parameter `onNavigateToMediaViewer: (postUri: String, imageIndex: Int) -> Unit` matching the existing `onNavigateToPost` / `onNavigateToAuthor` shape. Default to `{}` only if other navigation callbacks have defaults; otherwise no default (consistent with the surrounding hoisted-callback shape).
- [x] 4.3 Replace the `is PostDetailEffect.NavigateToMediaViewer ->` branch in `PostDetailScreen.kt`'s effect collector. Old behavior (Timber log + `snackbarHostState.showSnackbar(mediaViewerComingSoonMessage)`) becomes `onNavigateToMediaViewer(effect.postUri, effect.imageIndex)`. Remove the `mediaViewerComingSoonMessage` local.
- [x] 4.4 Delete `R.string.postdetail_snackbar_media_viewer_coming_soon` from `feature/postdetail/impl/src/main/res/values/strings.xml`. Run `./gradlew :feature:postdetail:impl:lint` to verify nothing references the removed string.
- [x] 4.5 In `PostDetailNavigationModule.kt`, wire the new callback as `onNavigateToMediaViewer = { uri, index -> navState.add(MediaViewerRoute(postUri = uri, imageIndex = index)) }`.
- [~] 4.6 Update the existing effect-collector unit test in `PostDetailScreen` / `PostDetailViewModelTest` that asserted the Snackbar fallback. Replace with a test confirming the hoisted callback receives the right `(postUri, imageIndex)` pair when `NavigateToMediaViewer` fires. The "OnFocusImageClicked emits NavigateToMediaViewer with the focus URI and image index" VM-level test stays unchanged — it tests the effect emission, which is unrelated to the screen-level wiring.
- [x] 4.7 Run `./gradlew :feature:postdetail:impl:testDebugUnitTest :feature:postdetail:impl:validateDebugScreenshotTest` clean. Screenshot baselines should not change unless the FAB / focus-container layout shifts; the Snackbar removal is a behavior change but is not visible in any existing baseline (none of the baselines capture mid-Snackbar state).

## 5. Tests for `:feature:mediaviewer:impl`

- [x] 5.1 Add `feature/mediaviewer/impl/src/test/kotlin/.../MediaViewerViewModelTest.kt`:
  - `init load → Loaded with images and currentIndex == route.imageIndex`
  - `init load failure → Error with retry-clears-error path`
  - `OnPageChanged(index) updates state.currentIndex and resets isChromeVisible to true`
  - `OnTapImage toggles isChromeVisible`
  - `OnAltBadgeClick sets isAltSheetOpen = true`; `OnAltSheetDismiss clears it`
  - `OnDismissRequest emits MediaViewerEffect.Dismiss`
  - `OnRetry from Error → Loading → Loaded` (or → Error on second failure)
- [x] 5.2 Add `feature/mediaviewer/impl/src/test/kotlin/.../FullsizeUrlTest.kt`:
  - `feed_thumbnail URL → fullsize URL` (canonical Bluesky CDN shape)
  - `URL without @feed_thumbnail token → unchanged` (defensive fall-through)
  - `Empty URL → empty` (defensive)
- [ ] 5.3 Add `feature/mediaviewer/impl/src/screenshotTest/kotlin/.../MediaViewerScreenScreenshotTest.kt` with fixtures:
  - `Loading` (light + dark)
  - `Loaded(single)` (light + dark) — chrome visible, single-image, no page indicator
  - `Loaded(multi)` (light + dark) — chrome visible, three-image, "1 / 3" indicator, ALT badge present (one image with alt text)
  - `Error` (light + dark) — error message + retry button
  - `Loaded with alt sheet open` (light) — sheet covers lower half with the full alt text
- [ ] 5.4 Add `feature/mediaviewer/impl/src/androidTest/kotlin/.../MediaViewerScreenInstrumentedTest.kt`:
  - Back press → `onDismiss` invoked
  - Close button click → `onDismiss` invoked
  - Page swipe → `currentIndex` updates (asserted via the chrome's "N / M" semantics text)
  - Tap on image → chrome toggles (asserted via `AnimatedVisibility`-tracked semantics)
- [ ] 5.5 Run `./gradlew :feature:mediaviewer:impl:testDebugUnitTest :feature:mediaviewer:impl:validateDebugScreenshotTest` clean. Commit baselines.
- [ ] 5.6 Add the `run-instrumented` PR label per the project's androidTest convention (memory: `feedback_run_instrumented_label_on_androidtest_prs`).

## 6. Verification + ship

- [x] 6.0 `./gradlew :core:posts:testDebugUnitTest :core:posts:assembleDebug` clean.
- [x] 6.1 `./gradlew :feature:mediaviewer:api:testDebugUnitTest :feature:mediaviewer:api:assembleDebug` clean.
- [ ] 6.2 `./gradlew :feature:mediaviewer:impl:testDebugUnitTest :feature:mediaviewer:impl:validateDebugScreenshotTest :feature:mediaviewer:impl:assembleDebug` clean.
- [x] 6.3 `./gradlew :feature:postdetail:impl:testDebugUnitTest :feature:postdetail:impl:validateDebugScreenshotTest` clean — regression contract for the Snackbar removal.
- [x] 6.4 `./gradlew :app:assembleDebug spotlessCheck lint` clean.
- [ ] 6.5 `./gradlew jacocoTestReportAggregated` clean — coverage on the new module is part of the project's 70%-on-changed-files gate.
- [ ] 6.6 Manual walkthrough on emulator (use the project's `android-cli` skill to start one): tap a feed multi-image post → post-detail opens → tap focus image at index 1 → viewer opens at index 1; pinch-zoom; swipe-left to index 2 (only when min-zoom); double-tap-zoom; swipe-down at min-zoom → dismiss; tap on ancestor / reply image → no-op (preserved); back press from viewer → returns to post-detail; rotate while zoomed → state survives.
- [x] 6.7 Grep for the removed strings to confirm cleanup: `grep -rn "media_viewer_coming_soon\|nubecita-e02 -" .` should return zero hits in committed source. (The bd id reference in PR descriptions / commit footers is fine.)
- [ ] 6.8 PR description references this openspec change name (`add-fullscreen-image-viewer`), the bd id (`Closes: nubecita-e02`), and links the m28.5.2 design doc's Decision 4 (the placeholder this change retires).
- [ ] 6.9 File a follow-up bd issue for "Feed PostCard image taps open the media viewer" — the deliberately-out-of-scope companion change. Cross-reference the issue id in this PR's description.
