## ADDED Requirements

### Requirement: Image tap dispatches a media-viewer navigation effect

The screen SHALL emit a `PostDetailEffect.NavigateToMediaViewer(postUri: String, imageIndex: Int)` effect on every image tap inside the focus post and inside the multi-image carousel slides. The screen's effect collector MUST handle the effect by invoking a hoisted callback `onNavigateToMediaViewer: (postUri: String, imageIndex: Int) -> Unit` — matching the existing `onNavigateToPost` / `onNavigateToAuthor` shape — which the entry block in `PostDetailNavigationModule` wires as `LocalMainShellNavState.current.add(MediaViewerRoute(postUri = postUri, imageIndex = imageIndex))`.

The previously-shipped Snackbar-acknowledged-no-op fallback (the `Fullscreen viewer coming soon` Snackbar branch and the `R.string.postdetail_snackbar_media_viewer_coming_soon` resource) MUST be removed. The corresponding `Timber.tag("PostDetailScreen")` debug log line referencing nubecita-e02 MUST be removed in lockstep — the breadcrumb's lifetime ends with this change. The "OnFocusImageClicked emits NavigateToMediaViewer with the focus URI and image index" ViewModel-level test stays unchanged (it tests the effect emission, which is unaffected).

#### Scenario: Image tap on focus single-image post emits effect

- **WHEN** the user taps the rendered image inside a single-image Focus post
- **THEN** `PostDetailEffect.NavigateToMediaViewer(postUri = post.uri, imageIndex = 0)` is sent through the screen's effect channel

#### Scenario: Image tap on carousel slide emits effect with slide index

- **WHEN** the user taps the second slide of a three-image Focus carousel
- **THEN** `PostDetailEffect.NavigateToMediaViewer(postUri = post.uri, imageIndex = 1)` is sent

#### Scenario: Effect collector invokes hoisted navigation callback

- **WHEN** the screen's `LaunchedEffect` collector receives `PostDetailEffect.NavigateToMediaViewer(postUri, imageIndex)`
- **THEN** the collector invokes `onNavigateToMediaViewer(postUri, imageIndex)` exactly once; no Snackbar is dispatched and no Timber breadcrumb is logged

#### Scenario: Entry block wires the callback to MainShell navigation

- **WHEN** `PostDetailNavigationModule`'s entry block constructs the `PostDetailScreen` Composable
- **THEN** the `onNavigateToMediaViewer` parameter is wired as `{ uri, index -> navState.add(MediaViewerRoute(postUri = uri, imageIndex = index)) }` where `navState` is `LocalMainShellNavState.current`; the `MediaViewerRoute` symbol is imported from `:feature:mediaviewer:api`

#### Scenario: Snackbar fallback string and Timber breadcrumb are removed

- **WHEN** the source tree of `:feature:postdetail:impl` is grepped for `media_viewer_coming_soon` and for the nubecita-e02 Timber tag string
- **THEN** zero matches are returned in committed source — both have been deleted as part of this change
