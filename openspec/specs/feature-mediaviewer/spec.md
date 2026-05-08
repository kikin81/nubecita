# feature-mediaviewer Specification

## Purpose
TBD - created by archiving change add-fullscreen-image-viewer. Update Purpose after archive.
## Requirements
### Requirement: `MediaViewerRoute` is the canonical NavKey for the fullscreen image viewer

The system SHALL expose `net.kikin.nubecita.feature.mediaviewer.api.MediaViewerRoute(postUri: String, imageIndex: Int)` as the only `androidx.navigation3.runtime.NavKey` that navigates to the fullscreen image viewer. Both fields are primitives — `postUri` is a plain `String` (not the lexicon-typed `AtUri` value class) matching `PostDetailRoute.postUri` and the rest of the project's NavKey shape, and `imageIndex` is an `Int` referring to the position inside the post's `app.bsky.embed.images` payload (zero-based). The route MUST live in `:feature:mediaviewer:api` (NavKey-only module per the api/impl convention in `CLAUDE.md`).

#### Scenario: Post-detail focus-image tap navigates via MediaViewerRoute

- **WHEN** the user taps an image inside the focus post in `PostDetailScreen` (single-image or per-carousel-slide)
- **THEN** `PostDetailViewModel` emits `PostDetailEffect.NavigateToMediaViewer(postUri, imageIndex)` and the screen's collector calls `LocalMainShellNavState.current.add(MediaViewerRoute(postUri = postUri, imageIndex = imageIndex))` — no other `NavKey` type is constructed for this transition

#### Scenario: NavKey carries primitive fields

- **WHEN** `MediaViewerRoute` is serialized via the `kotlinx.serialization` Nav 3 surface
- **THEN** the encoded form is two primitive fields (a string and an int); no nested wrapper appears in the persisted nav state, so process death and any future deep-link routing round-trip the route cleanly

### Requirement: `MediaViewerViewModel` state machine has a sealed load-status sum

The system SHALL expose `MediaViewerViewModel` extending `MviViewModel<MediaViewerState, MediaViewerEvent, MediaViewerEffect>` per the project's MVI conventions. `MediaViewerState` MUST carry a `loadStatus: MediaViewerLoadStatus` field (sealed sum). `MediaViewerLoadStatus` is a `sealed interface` with exactly the variants:

- `Loading` — initial fetch in flight; no payload
- `Loaded(images: ImmutableList<ImageUi>, currentIndex: Int, isChromeVisible: Boolean, isAltSheetOpen: Boolean)` — all viewer-active state lives here
- `Error(error: UiText)` — sticky; the screen renders an error layout with a retry affordance

The state MUST NOT use a flat `isLoading: Boolean` — these lifecycle phases are mutually exclusive per the project's MVI flat-vs-sealed rule in `CLAUDE.md`. The per-`Loaded` fields (`currentIndex`, `isChromeVisible`, `isAltSheetOpen`) only make sense when `images` is populated, so they live inside the `Loaded` variant rather than as flat top-level fields that would require runtime invariants.

#### Scenario: Initial load transitions Loading → Loaded

- **WHEN** `MediaViewerViewModel` is constructed with a `MediaViewerRoute` and the underlying `PostRepository.getPost(postUri)` succeeds with an embed carrying images
- **THEN** `loadStatus` transitions `Loading → Loaded(images = …, currentIndex = route.imageIndex, isChromeVisible = true, isAltSheetOpen = false)`

#### Scenario: Initial load failure surfaces an Error variant

- **WHEN** `PostRepository.getPost(postUri)` returns a failure on the initial fetch
- **THEN** `loadStatus` becomes `Error(UiText.from(error))`; no `Loaded` payload is constructed; the screen renders a retry layout

#### Scenario: Retry from Error transitions back through Loading

- **WHEN** `loadStatus == Error` and the user taps the retry affordance dispatching `MediaViewerEvent.OnRetry`
- **THEN** `loadStatus` transitions `Error → Loading`; on success the state advances to `Loaded`; on a second failure the state returns to `Error`

#### Scenario: Page change updates currentIndex and resets chrome timer

- **WHEN** `loadStatus == Loaded` and `MediaViewerEvent.OnPageChanged(index)` fires
- **THEN** `loadStatus` is `Loaded(images, currentIndex = index, isChromeVisible = true, isAltSheetOpen = …)` — the chrome's auto-fade timer is reset by the `isChromeVisible` write

### Requirement: ViewModel re-fetches via `:core:posts`'s `PostRepository`; NavKey carries no image payload

The viewer SHALL fetch the post's image set via `:core:posts`'s `PostRepository.getPost(uri)` — given only `(postUri, imageIndex)` from the NavKey. The ViewModel MUST NOT receive the image list inline through the NavKey; the NavKey contract stays narrow (two primitives). Coil's existing disk cache (already populated by the feed / post-detail's thumbnail render) makes thumbnail re-rendering instant; the `@fullsize` URL streams in as a separate cache key.

If the post has been deleted server-side between the time the user opened post-detail and tapped the image, the fetch returns failure and the viewer renders `Error` rather than bouncing back to post-detail — the user explicitly opened the viewer, so the surface stays visible with a retry affordance until the user dismisses. If the fetch succeeds but the post's `embed` is not `EmbedUi.Images` (defensive — the viewer was opened on a non-image post via some out-of-band path), the viewer renders `Error` with a "This post has no images" message rather than rendering an empty pager.

#### Scenario: ViewModel loads via PostRepository from :core:posts

- **WHEN** `MediaViewerViewModel`'s constructor is inspected
- **THEN** it MUST declare a `private val postRepository: PostRepository` parameter typed against `net.kikin.nubecita.core.posts.PostRepository` and MUST NOT import `app.bsky.feed.getPosts` / `getPostThread` clients directly

#### Scenario: Non-image embed surfaces as Error

- **WHEN** `getPost` resolves successfully but the resulting `PostUi.embed` is not `EmbedUi.Images` (e.g., the URI was opened on a video-only post via a future deep link)
- **THEN** `loadStatus` becomes `Error` with a "no images" message; the pager does not render an empty page set

#### Scenario: NavKey does not carry the image list

- **WHEN** `MediaViewerRoute`'s declared fields are inspected
- **THEN** the only fields are `postUri: String` and `imageIndex: Int`; no `images: List<…>` field is present

### Requirement: Viewer renders fullsize CDN images via `ImageUi.url`

The viewer SHALL render each page directly from `ImageUi.url`. The `:core:feed-mapping` projection (`toImageUiList`) maps `image.fullsize.raw` into `ImageUi.url`, so the URL is already the fullsize CDN variant — no per-page URL transform is required at this layer.

A previous draft of this spec called for an `ImageUi.fullsizeUrl()` helper that would swap a `@feed_thumbnail` token for `@fullsize`; that helper was a no-op in production because the input URL never carries the `@feed_thumbnail` token (the mapper resolves to `image.fullsize.raw`, which uses the `feed_fullsize` path segment, not a `@<size>` suffix). The helper was removed under PR #139 / Copilot review feedback.

A separate follow-up (`nubecita-w70`) tracks switching the feed-side mapper to `image.thumb.raw` so feed PostCards stop downloading fullsize bytes for thumbnail-sized cells. Once that change ships, the viewer will need its own thumb→fullsize URL transform — either re-introducing a helper or deriving fullsize at the viewer layer. Until then, the viewer reads `ImageUi.url` as-is.

#### Scenario: ZoomableAsyncImage receives ImageUi.url unchanged

- **WHEN** the viewer's `LoadedState` renders a page for `image: ImageUi`
- **THEN** the `model` parameter passed to `ZoomableAsyncImage` is exactly `image.url` — no transform, no swap, no helper interposed

### Requirement: Pinch-to-zoom + paging + swipe-down dismiss compose without conflicts

The viewer screen SHALL render each page via `me.saket.telephoto:zoomable-image-coil3`'s `ZoomableAsyncImage` and host the pages in `androidx.compose.foundation.pager.HorizontalPager`. Gesture composition rules:

- `HorizontalPager.userScrollEnabled` MUST be bound to `currentZoomFactor <= 1f` so swipe-paging is disabled while the current page is zoomed (telephoto's pan absorbs the gesture instead).
- A vertical `Modifier.draggable` on the pager wrapper (the swipe-down-to-dismiss layer) MUST be enabled only when the current page is at min-zoom; above min-zoom the draggable is disabled and the gesture goes to telephoto's pan.
- Single-tap on the image dispatches `MediaViewerEvent.OnTapImage` (chrome toggle); double-tap is reserved for telephoto's double-tap-zoom; long-press is unbound in v1.

#### Scenario: Paging disabled while zoomed

- **WHEN** the current page's `ZoomableState.contentTransformation.scale.scaleX > 1f`
- **THEN** `HorizontalPager.userScrollEnabled` is `false` and a horizontal swipe pans the zoomed image instead of advancing the page

#### Scenario: Paging enabled at min-zoom

- **WHEN** the current page's zoom factor is at min-scale
- **THEN** `HorizontalPager.userScrollEnabled` is `true` and a horizontal swipe advances the page; `OnPageChanged(index)` fires once the new page settles

#### Scenario: Swipe-down dismiss at min-zoom

- **WHEN** the user drags the page vertically past the dismiss threshold while at min-zoom
- **THEN** `MediaViewerEvent.OnDismissRequest` fires; the ViewModel emits `MediaViewerEffect.Dismiss`; the screen's collector calls `LocalMainShellNavState.current.removeLast()`

#### Scenario: Swipe-down inactive while zoomed

- **WHEN** the user drags vertically while the current page is zoomed above min-scale
- **THEN** the dismiss draggable is disabled; the vertical drag pans the zoomed image instead of triggering dismiss

### Requirement: Tap-to-toggle chrome with auto-fade and per-image alt-text sheet

The viewer SHALL render an overlay chrome layer containing (from left to right) a close button, a page indicator (`"${currentIndex + 1} / ${images.size}"` shown only when `images.size > 1`), and an `ALT` badge (shown only when `images[currentIndex].altText != null`). The chrome's visibility MUST be driven by `state.isChromeVisible`:

- Chrome MUST be visible on entry (`isChromeVisible = true` in the initial `Loaded` state).
- Chrome MUST auto-fade after 3 seconds of inactivity. The 3-second timer resets when `isChromeVisible` transitions to `true` and when `currentIndex` changes.
- A single tap on the image MUST dispatch `OnTapImage`, toggling `isChromeVisible`.
- The `ALT` badge MUST open a `ModalBottomSheet` with the full alt text on click. The sheet's `onDismissRequest` MUST clear `isAltSheetOpen`; tapping outside the sheet or pressing back while the sheet is open MUST dismiss the sheet rather than the viewer.

The chrome MUST be implemented with `androidx.compose.animation.AnimatedVisibility` and `androidx.compose.material3.ModalBottomSheet` — never with hand-rolled animation or hand-positioned scrims.

#### Scenario: Chrome visible on entry then auto-fades

- **WHEN** the viewer enters `Loaded` and three seconds pass with no user interaction
- **THEN** `isChromeVisible` transitions to `false`; the close button, page indicator, and ALT badge are no longer rendered

#### Scenario: Tap on image toggles chrome

- **WHEN** the user single-taps the image
- **THEN** `OnTapImage` dispatches; `isChromeVisible` toggles; if it transitions to `true`, the auto-fade timer resets

#### Scenario: Page change re-shows chrome

- **WHEN** the user swipes to a new page
- **THEN** `OnPageChanged(index)` fires; `isChromeVisible` is set to `true`; the auto-fade timer resets

#### Scenario: ALT badge opens bottom sheet with full alt text

- **WHEN** the current image's `altText` is non-null and the user taps the `ALT` badge
- **THEN** `OnAltBadgeClick` dispatches; `isAltSheetOpen` becomes `true`; a `ModalBottomSheet` renders the full alt text in a scrollable container

#### Scenario: ALT badge absent when no alt text

- **WHEN** the current image's `altText` is null
- **THEN** the `ALT` badge MUST NOT render in the chrome overlay; the close button and page indicator (if applicable) remain

#### Scenario: Page indicator absent for single-image posts

- **WHEN** `state.images.size == 1`
- **THEN** the page indicator (`"1 / 1"`) MUST NOT render — the close button and (conditionally) the `ALT` badge are the only chrome elements

### Requirement: Effect-driven dismiss; ViewModel never imports `LocalMainShellNavState`

`MediaViewerViewModel` SHALL emit `MediaViewerEffect.Dismiss` (no payload) for all three dismiss paths: swipe-down past threshold at min-zoom, close-button tap, and back press collected by a `BackHandler` at the screen level. The screen's `LaunchedEffect` collector MUST be the only place that calls `LocalMainShellNavState.current.removeLast()` — the ViewModel MUST NOT inject the nav state holder, per the `CLAUDE.md` MVI rule that ViewModels never reach into Compose `CompositionLocal`s.

#### Scenario: Back press dispatches dismiss

- **WHEN** the user presses the system back button while the viewer is `Loaded` and the alt sheet is closed
- **THEN** the screen's `BackHandler` dispatches `OnDismissRequest`; the ViewModel emits `Dismiss`; the screen's collector invokes `removeLast()` on `LocalMainShellNavState`

#### Scenario: ViewModel does not import LocalMainShellNavState

- **WHEN** the import set of `MediaViewerViewModel.kt` is inspected
- **THEN** it MUST NOT contain `LocalMainShellNavState` or any `CompositionLocal`-originated nav holder; the screen module is the only place that imports the nav state holder

### Requirement: `:feature:mediaviewer:impl` registers an `@OuterShell`-qualified `EntryProviderInstaller`

The viewer's `:impl` module MUST contribute a `@Provides @IntoSet @OuterShell EntryProviderInstaller` registering `MediaViewerRoute` in the OUTER `NavDisplay` (`MainNavigation` in `:app`), not inside `MainShell`'s inner `NavDisplay`. Hosting on the outer shell escapes `MainShell`'s `NavigationSuiteScaffold` — the bottom nav bar / rail does NOT render while the viewer is open, giving a true fullscreen canvas. This deviates from the project's general convention (per `CLAUDE.md`, `@OuterShell` collects `Splash → Login → Main`; tab-internal sub-routes live on `@MainShell`) deliberately, because the viewer is a fullscreen modal that should escape the tab structure entirely.

Pop semantics: `goBack()` on the outer Navigator pops the viewer and lands on `Main`, which preserves `MainShell`'s inner back stack. The user returns to the same `PostDetailScreen` they tapped from with screen state intact.

The entry block MUST resolve the per-route `MediaViewerViewModel` via the assisted-inject Hilt bridge (`hiltViewModel<MediaViewerViewModel, MediaViewerViewModel.Factory>(creationCallback = { it.create(route) })`) — same pattern as `PostDetailNavigationModule`. The block MUST read the outer Navigator via `LocalAppNavigator.current` (a `CompositionLocal` provided by `MainNavigation` at the root of the outer `NavDisplay`'s composition) and wire `onDismiss = { navigator.goBack() }`.

#### Scenario: OuterShell qualifier on the entry provider

- **WHEN** Hilt's `Set<EntryProviderInstaller>` qualified by `@OuterShell` is resolved
- **THEN** the set includes the viewer's installer; the corresponding `@MainShell` set does NOT include it

#### Scenario: Bottom nav bar hidden while viewer is open

- **WHEN** the user taps a focus-post image and the viewer is pushed
- **THEN** `MainShell`'s `NavigationSuiteScaffold` chrome (bottom nav bar on mobile, rail on tablet) is no longer rendered — the viewer fills the entire screen including the area previously occupied by the nav suite

#### Scenario: Dismiss returns to the same PostDetail screen

- **WHEN** the user dismisses the viewer (close button, swipe-down, back press)
- **THEN** the outer Navigator pops the viewer; `MainShell` re-renders with its inner back stack intact, and the `PostDetailScreen` the user tapped from is visible at the same scroll position

### Requirement: Screenshot test harness covers the viewer's view modes

The capability SHALL maintain a screenshot-test harness under `feature/mediaviewer/impl/src/screenshotTest/` whose baselines cover at minimum: `Loading`, `Loaded(single)`, `Loaded(multi)` with chrome visible, `Error`, and `Loaded` with the alt-text sheet open. The `Loading`, `Loaded(single)`, `Loaded(multi)`, and `Error` fixtures MUST each be captured under both `NubecitaTheme(darkTheme = false)` and `NubecitaTheme(darkTheme = true)` — the alt-sheet fixture may be light-only.

#### Scenario: Loaded multi-image fixture in both themes

- **WHEN** `./gradlew :feature:mediaviewer:impl:validateDebugScreenshotTest` runs
- **THEN** at least two snapshot files exist that differ only in `darkTheme` parameter, both showing the multi-image `Loaded` viewer with chrome visible (close button, "1 / 3" indicator, ALT badge), and any drift in either fails the validation
