## Why

`add-postdetail-m3-expressive-treatment` (nubecita-m28.5.2) wired the post-detail screen's focus-image taps to a `PostDetailEffect.NavigateToMediaViewer(postUri, imageIndex)` effect, but the destination route never existed. The effect collector currently logs a Timber breadcrumb and surfaces a "Fullscreen viewer coming soon" Snackbar — the deliberate Decision-4 placeholder from that change. Until the viewer ships, every focus-image tap on `PostDetailScreen` produces the same brief acknowledgment with no actual navigation. This is the change that ships the destination.

The viewer is a fullscreen pinch-to-zoom image surface, paged horizontally across the focus post's image set, dismissed with a swipe-down or back press. Pinch-to-zoom is delivered by `me.saket.telephoto:zoomable-image-coil3` rather than a hand-rolled gesture stack — telephoto already handles the long tail (sub-sampling for huge images, fling-pan boundaries, double-tap-zoom, gesture conflicts with paging) that we'd otherwise re-derive. The Snackbar fallback in `PostDetailScreen.kt` and its companion string resource are removed once the viewer is wired.

## What Changes

### New module: `:core:posts`

- Apply the `nubecita.android.library` convention plugin (no Compose, no Hilt for the API). Hilt binding lives in a dedicated DI module inside `:core:posts` — same shape as `:core:auth`.
- Expose `interface PostRepository { suspend fun getPost(uri: String): Result<PostUi> }`. The default implementation calls atproto-kotlin's `app.bsky.feed.getPosts` lexicon (single-post request — `uris = listOf(uri)` — no thread context, no ancestors / replies). The response is projected to `PostUi` via `:core:feed-mapping`'s `toPostUiCore` shared helper. Errors mapped to `Result.failure(...)` with the same shape `:feature:postdetail:impl`'s `PostThreadRepository` uses.
- New, focused capability — fetch a single post by URI without thread context. Future consumers (deep-link landings, notifications resolving a referenced post, this viewer) all share the same surface. `:feature:postdetail:impl`'s existing `PostThreadRepository` stays as-is — different semantic surface (a thread, not a single post).

### New module: `:feature:mediaviewer:api`

- Apply the `nubecita.android.library` convention plugin. No Compose, no Hilt — NavKey-only module per the api/impl convention in `CLAUDE.md`.
- Expose a single type: `@Serializable data class MediaViewerRoute(val postUri: String, val imageIndex: Int) : NavKey`. The `postUri` is a plain `String` (matching `PostDetailRoute.postUri` and the rest of the project's NavKey shape — `AtUri` wrapping happens at the XRPC boundary inside the impl module).

### New module: `:feature:mediaviewer:impl`

- Apply the `nubecita.android.feature` convention plugin (library + compose + hilt + common feature deps).
- `MediaViewerScreen.kt` — fullscreen Compose surface. Black `Surface` filling the inner `NavDisplay`'s slot. `HorizontalPager` over the focus post's image list (single image still routes through the pager with `pageCount = 1`). Each page hosts a `ZoomableAsyncImage` from `me.saket.telephoto:zoomable-image-coil3` bound to the image's `@fullsize` CDN URL with `ContentScale.Fit`.
- `MediaViewerViewModel.kt` — extends `MviViewModel<MediaViewerState, MediaViewerEvent, MediaViewerEffect>`. Assisted-injected with the route, and constructor-injected with `:core:posts`'s `PostRepository`. On init, loads the post via `postRepository.getPost(postUri)`, reads the resulting `PostUi.embed` (a `EmbedUi.Images` when the post carries images), and updates state with the projected `ImmutableList<ImageUi>`. The ViewModel does not own the pager state — `HorizontalPager`'s `rememberPagerState` lives in the composable, with `OnPageChanged(index)` events reflecting the active page back into state for chrome rendering.
- `MediaViewerContract.kt` — `MediaViewerState`, `MediaViewerEvent`, `MediaViewerEffect` per the project's MVI convention. Status is a sealed sum (`Loading` / `Loaded` / `Error`) per the `CLAUDE.md` rule: these are mutually exclusive view modes.
- `MediaViewerNavigationModule.kt` — Hilt module contributing a `@Provides @IntoSet @MainShell EntryProviderInstaller` that registers `MediaViewerRoute` inside `MainShell`'s inner `NavDisplay`. Reads the assisted-inject Hilt bridge for the per-route ViewModel instance, mirroring `PostDetailNavigationModule`'s shape.
- `:app/build.gradle.kts` adds `implementation(project(":feature:mediaviewer:impl"))` so the multibinding picks up the new entry. `:app/build.gradle.kts` does NOT depend on `:api` directly — the `:impl` module's transitive `:api` dep is sufficient.

### Modified: `:feature:postdetail:impl` (the wiring + cleanup)

- `PostDetailScreen.kt` gains a hoisted callback `onNavigateToMediaViewer: (postUri: String, imageIndex: Int) -> Unit` matching the existing `onNavigateToPost` / `onNavigateToAuthor` shape. The current Timber log + Snackbar branch in the `LaunchedEffect` collector becomes a one-liner that delegates to the callback.
- `PostDetailNavigationModule.kt` wires the callback as `navState.add(MediaViewerRoute(postUri = uri, imageIndex = index))`. `:feature:postdetail:impl/build.gradle.kts` adds `implementation(project(":feature:mediaviewer:api"))`.
- The `mediaViewerComingSoonMessage` local + the `R.string.postdetail_snackbar_media_viewer_coming_soon` resource are deleted. The corresponding effect-collector unit test branch in `PostDetailViewModelTest` / `PostDetailScreen` flips from "Snackbar dispatched" to "callback invoked with the right (uri, index) pair".

### Library / version-catalog

- Add a new `telephoto` version key to `gradle/libs.versions.toml` and a `telephoto-zoomable-image-coil3` library alias resolving to `me.saket.telephoto:zoomable-image-coil3`. `:feature:mediaviewer:impl` is the only consumer.

## Capabilities

### New Capabilities

- `feature-mediaviewer`: The fullscreen image viewer surface — `:feature:mediaviewer:api` (`MediaViewerRoute(postUri, imageIndex)` NavKey) and `:feature:mediaviewer:impl` (the screen, ViewModel, navigation entry). First-class capability owning the viewer's state machine, navigation contract, gesture surface, and chrome behavior.
- `core-posts`: New `:core:posts` module hosting `interface PostRepository` with `suspend fun getPost(uri: String): Result<PostUi>` backed by atproto-kotlin's `app.bsky.feed.getPosts`. First consumer is the media viewer; future consumers (deep-link landings, notification post resolution) share the same surface. Different semantic surface from `:feature:postdetail:impl`'s `PostThreadRepository` (thread vs single post) — the two repos coexist.

### Modified Capabilities

- `feature-postdetail`: The image-tap effect now routes to a real destination via the new `onNavigateToMediaViewer` callback. The Decision-4 Snackbar fallback (the `Fullscreen viewer coming soon` branch + its string resource) is removed; the corresponding spec requirement scenario is updated from "Missing media-viewer route surfaces a snackbar" to "Image tap navigates to MediaViewerRoute".

## Impact

- **Affected modules**: NEW `:core:posts`, NEW `:feature:mediaviewer:api`, NEW `:feature:mediaviewer:impl`, `:feature:postdetail:impl` (callback hoisting + Snackbar removal + new dep on `:feature:mediaviewer:api`), `:app` (new `:impl` dep so the multibinding picks up the entry; aggregates `:core:posts` Hilt module via the `:feature:mediaviewer:impl` transitive graph), `gradle/libs.versions.toml` (new telephoto alias).
- **Affected specs**: `core-posts` (new), `feature-mediaviewer` (new), `feature-postdetail` (delta).
- **Dependencies**: `me.saket.telephoto:zoomable-image-coil3` (latest stable, Coil 3-aware). No transitive Coil version conflict — telephoto's coil3 artifact aligns with the catalog's Coil 3.4.0.
- **Behavior under feature flags / build variants**: none. The viewer is always-on once the route is wired.
- **Backwards compatibility**: no breaking changes. `PostDetailScreen`'s public surface gains an additional callback; the `:app`-side entry block in `PostDetailNavigationModule` is the only call site and is updated in lockstep. The Snackbar removal is observable behavior change — the user sees actual navigation instead of a transient acknowledgment, which is the goal.
- **Process death / deep-linking**: The route is `@Serializable` with primitive fields, so it survives process death and is ready for any future deep-link wiring without further schema work.

## Non-goals

- **Feed PostCard image taps opening the viewer.** The feed's contract (documented in `PostCardImageEmbed.kt:50-58`) deliberately leaves `onImageClick = null` so taps bubble up to the body-tap that opens post-detail. Rewiring that gesture is a separate, intentionally-scoped change — file a follow-up bd issue on merge.
- **Ancestor / reply image taps.** Decision 4 of `add-postdetail-m3-expressive-treatment` keeps these as no-op for v1; this change does not relax that decision.
- **Video viewer.** Bluesky's video embeds already render inline with controls; a fullscreen video surface is a separate capability.
- **Shared-element transition from the focus image into the viewer.** Worth a follow-up; v1 uses the standard `NavDisplay` transition. Same for pinch-out-to-dismiss.
- **Image save / share actions in the chrome.** Out of scope for the first cut; file follow-up if user feedback warrants.
- **Multi-image editing / selection.** Read-only viewer; no edit affordance.
