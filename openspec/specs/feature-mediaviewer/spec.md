# feature-mediaviewer Specification

## Purpose

The fullscreen image viewer: how it resolves a post's images, pages between
them, handles zoom and dismiss gestures, surfaces alt text, and saves the
current image to the device gallery.

Saving itself is specified by `image-save-to-gallery`; this capability covers
only the viewer's side of it — the affordance, its outcome reporting, and when
it is hidden.
## Requirements
### Requirement: `MediaViewerRoute` is the canonical NavKey for the fullscreen image viewer

The system SHALL expose `net.kikin.nubecita.feature.mediaviewer.api.MediaViewerRoute(postUri: String, imageIndex: Int)` as the only `androidx.navigation3.runtime.NavKey` that navigates to the fullscreen image viewer. Both fields MUST be primitives: `postUri` as a plain `String` and `imageIndex` as a zero-based `Int`. The route MUST live in `:feature:mediaviewer:api`.

#### Scenario: Post-detail focus-image tap navigates via MediaViewerRoute

- **WHEN** the user taps an image inside the focus post in `PostDetailScreen` (single-image or per-carousel-slide)
- **THEN** `PostDetailViewModel` emits `PostDetailEffect.NavigateToMediaViewer(postUri, imageIndex)` and the screen's collector calls `LocalMainShellNavState.current.add(MediaViewerRoute(postUri = postUri, imageIndex = imageIndex))` — no other `NavKey` type is constructed for this transition

#### Scenario: NavKey carries primitive fields

- **WHEN** `MediaViewerRoute` is serialized via the `kotlinx.serialization` Nav 3 surface
- **THEN** the encoded form is two primitive fields (a string and an int); no nested wrapper appears in the persisted nav state, so process death and any future deep-link routing round-trip the route cleanly

### Requirement: `MediaViewerViewModel` state machine has a sealed load-status sum

The system SHALL expose `MediaViewerViewModel` extending `MviViewModel<MediaViewerState, MediaViewerEvent, MediaViewerEffect>`. `MediaViewerState` MUST carry a `loadStatus: MediaViewerLoadStatus` sealed sum with variants: `Loading`, `Loaded(images: ImmutableList<ImageUi>, currentIndex: Int, isChromeVisible: Boolean, isAltSheetOpen: Boolean)`, and `Error(error: UiText)`. The state MUST NOT use a flat `isLoading: Boolean`.

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

The viewer SHALL fetch the post's image set via `:core:posts`'s `PostRepository.getPost(uri)` using `(postUri, imageIndex)` from the NavKey. The ViewModel MUST NOT receive the image list through the NavKey. If fetching fails or the post embed is not `EmbedUi.Images`, the viewer MUST render `Error` with a retry affordance or error message rather than an empty pager.

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

The viewer SHALL render each page directly from `ImageUi.url`. Because the `:core:feed-mapping` projection (`toImageUiList`) maps `image.fullsize.raw` into `ImageUi.url`, the URL is already the fullsize CDN variant, and no per-page URL transform SHALL be applied at this layer.

#### Scenario: ZoomableAsyncImage receives ImageUi.url unchanged

- **WHEN** the viewer's `LoadedState` renders a page for `image: ImageUi`
- **THEN** the `model` parameter passed to `ZoomableAsyncImage` is exactly `image.url` — no transform, no swap, no helper interposed

### Requirement: Pinch-to-zoom + paging + swipe-down dismiss compose without conflicts

The viewer screen SHALL render each page via `ZoomableAsyncImage` in `HorizontalPager`. `HorizontalPager.userScrollEnabled` MUST be disabled when `currentZoomFactor > 1f`. The vertical swipe-down dismiss `Modifier.draggable` MUST be enabled only when at minimum zoom. Single tap MUST dispatch `MediaViewerEvent.OnTapImage`, while double-tap is reserved for telephoto's zoom.

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

The viewer SHALL render overlay chrome containing a close button, page indicator (if `images.size > 1`), and ALT badge (if `altText != null`). Chrome MUST be visible on entry and auto-fade after 3 seconds of inactivity. Tapping the image MUST toggle `isChromeVisible`. Tapping the ALT badge MUST open a `ModalBottomSheet` displaying full alt text. Chrome MUST use Compose `AnimatedVisibility` and `ModalBottomSheet`.

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

The `:feature:mediaviewer:impl` module MUST provide an `@OuterShell`-qualified `EntryProviderInstaller` registering `MediaViewerRoute` in the outer `NavDisplay`. This ensures the viewer escapes `MainShell`'s navigation chrome. Back navigation MUST pop the outer navigator, restoring `MainShell` state. The entry provider MUST resolve `MediaViewerViewModel` via Hilt assisted injection and bind dismiss to `navigator.goBack()`.

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

### Requirement: The viewer offers a save action for the image currently on screen

The fullscreen viewer SHALL present a save action in its chrome. The action SHALL apply to the image on the current page — never to the whole post's image set — so a multi-image post needs no disambiguating picker.

#### Scenario: Saving acts on the current page

- **GIVEN** a post with several images is open in the viewer
- **WHEN** the user pages to the third image and activates save
- **THEN** the third image is the one saved

#### Scenario: The save action follows the chrome

- **GIVEN** no save is in flight
- **WHEN** the viewer's chrome is hidden by the auto-fade timer
- **THEN** the save action is hidden with it
- **AND** it reappears when the chrome is restored

### Requirement: The viewer reports the outcome of a save

The viewer SHALL tell the user whether a save succeeded or failed. Failure messages SHALL distinguish the reason reported by the save capability rather than showing one generic message for every cause.

#### Scenario: A successful save is confirmed

- **WHEN** a save succeeds
- **THEN** the viewer shows a confirmation

#### Scenario: A failed save is reported with its cause

- **WHEN** a save fails because the image could not be retrieved
- **THEN** the viewer shows a message describing that cause
- **AND** the message differs from the one shown when the gallery write fails

### Requirement: The save action is absent where the device cannot save

Where the save capability reports itself unsupported, the viewer SHALL omit the save action entirely rather than presenting it disabled or failing on activation. A control that cannot ever work is worse than no control.

#### Scenario: An unsupported device shows no save action

- **GIVEN** the save capability reports itself unsupported
- **WHEN** the viewer renders its chrome
- **THEN** no save action is present

### Requirement: A save in progress is visible and cannot be re-triggered

While a save is in flight the viewer SHALL indicate progress and SHALL ignore further activations, so an impatient double-tap cannot write the same image twice.

#### Scenario: A second activation during a save is ignored

- **GIVEN** a save is in flight
- **WHEN** the user activates save again
- **THEN** no second save is started

#### Scenario: Progress is visible while saving

- **WHEN** a save is in flight
- **THEN** the viewer indicates that work is in progress

#### Scenario: The chrome does not auto-fade out from under a save in progress

- **GIVEN** a save is in flight
- **AND** the progress indication is presented within the chrome
- **WHEN** the chrome auto-fade timer would otherwise elapse
- **THEN** the chrome remains visible
- **AND** the auto-fade resumes once the save completes

#### Scenario: The indicator clears on completion

- **WHEN** an in-flight save finishes, whether it succeeded or failed
- **THEN** the progress indication is cleared
- **AND** the save action becomes activatable again

### Requirement: The presenter emits save outcomes without referencing platform resources

Consistent with the viewer's existing error handling, the presenter SHALL emit the save outcome as a typed value and leave the choice of user-facing wording to the screen. The presenter MUST NOT resolve strings itself.

#### Scenario: The outcome effect carries a type, not a message

- **WHEN** the presenter emits a save outcome
- **THEN** the emitted value identifies the outcome by type
- **AND** it carries no user-facing string
