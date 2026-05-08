## Context

`add-postdetail-m3-expressive-treatment` (nubecita-m28.5.2) shipped the post-detail screen's focus-image tap wiring as a deliberate placeholder: every tap dispatches `PostDetailEffect.NavigateToMediaViewer(postUri, imageIndex)`, but the destination route doesn't exist, so the screen's effect collector logs a Timber breadcrumb and surfaces a "Fullscreen viewer coming soon" Snackbar. That decision (m28.5.2 design Decision 4) was the right move at the time — it kept the feed-and-detail visual treatment shippable without coupling to an unspec'd viewer module — and it explicitly earmarked this change (`nubecita-e02`) as the destination's home.

The viewer is a small, focused capability: one route, one screen, one ViewModel, one MainShell entry. No new HTTP boundaries beyond an existing `PostRepository.getPost(uri)` call. The risk surface is concentrated in two places: gesture composition (paging, pinch-zoom, swipe-down dismiss must coexist without fighting each other) and CDN-URL handling (Bluesky thumbnails render in the feed; the viewer wants `@fullsize`). Both are addressed by leaning on the right library (`me.saket.telephoto:zoomable-image-coil3`) and a tiny, internal URL transform helper.

## Goals / Non-Goals

**Goals:**

- Wire `PostDetailScreen`'s `NavigateToMediaViewer` effect to a real fullscreen viewer destination. Remove the Snackbar fallback and its string resource. The `Timber.tag("PostDetailScreen") d "...nubecita-e02"` log line is removed in lockstep — the breadcrumb's lifetime ends with this change.
- Render the focus post's image set fullscreen with pinch-to-zoom, double-tap-zoom, pan-while-zoomed, swipe-between-images for multi-image posts, swipe-down-to-dismiss at min-zoom, and a tap-to-toggle chrome (close button, page indicator, alt-text badge).
- Keep gesture composition correct: paging only when the current page is at min-zoom; swipe-down dismiss only when min-zoom; tap toggles chrome without ever triggering pan/zoom.
- Stay deep-link-ready: the `MediaViewerRoute` is `@Serializable` with primitive fields, so process death and any future deep-link destination work doesn't require schema changes.
- Establish a screenshot-test contract for the viewer's three view modes (`Loading`, `Loaded(single)`, `Loaded(multi)`, `Error`) plus the alt-text sheet, captured in both `Light` and `Dark` themes.

**Non-Goals:**

- Feed PostCard image taps opening the viewer. The feed's contract (documented in `PostCardImageEmbed.kt:50-58`) deliberately bubbles taps to body-tap. File a follow-up bd issue on merge if the product wants to align with Bluesky's behavior there.
- Ancestor / reply image taps. m28.5.2 Decision 4 keeps these as no-op; this change does not relax that.
- Video viewer, image save / share actions in chrome, multi-image editing.
- Shared-element transition from the focus image into the viewer. Worth a follow-up; v1 uses the standard `NavDisplay` transition.
- Pinch-out-to-dismiss as a second dismiss gesture. Swipe-down at min-zoom is sufficient for v1.

## Decisions

### Decision 1: Use `me.saket.telephoto:zoomable-image-coil3` instead of hand-rolling pinch-to-zoom

**Choice:** Add the telephoto `zoomable-image-coil3` artifact to the version catalog and use its `ZoomableAsyncImage` Composable as the per-page leaf inside `HorizontalPager`. Telephoto's `ZoomableState` exposes the current zoom factor, which the screen reads to gate `HorizontalPager`'s `userScrollEnabled` and the swipe-down-dismiss `Modifier.draggable`.

**Why this over alternatives:**

- *Hand-roll a pinch-zoom Modifier* — the long tail (sub-sampling for 8000px-wide images on mid-range devices, fling-pan boundaries respecting the image's actual rendered bounds, double-tap-zoom focal point math, gesture conflict resolution against `HorizontalPager`) is a multi-day rabbit hole that telephoto already solves. Rejected on YAGNI grounds — we'd be re-deriving Saket's library.
- *Use Coil's bare `AsyncImage` with a `Modifier.transformable`* — works for the basic case but doesn't sub-sample. Bluesky CDN can return very large `@fullsize` images; without sub-sampling the bitmap allocation OOMs on lower-tier devices. Rejected.
- *Use telephoto's lower-level `Modifier.zoomable` + `SubSamplingImage`* — gives finer control but requires manually wiring the Coil image source. The `zoomable-image-coil3` artifact does exactly this composition for us. Rejected as unnecessary; if we ever need to load from a non-Coil source we can drop down then.

The telephoto coil3 artifact aligns with our catalog's Coil 3.4.0 (telephoto's coil3 surface tracks Coil 3.x since 0.13.0). No version-conflict resolution work expected.

**Risk addressed.** Telephoto is third-party with sub-1.0 versioning. Mitigation: the dependency is scoped to a single module (`:feature:mediaviewer:impl`) and a single Composable (`ZoomableAsyncImage`); a future swap to a different zoom library or to a hand-rolled implementation touches that one call site, not the rest of the project.

### Decision 2: Re-fetch the post via a new `:core:posts` `PostRepository`; don't pass the image list through the NavKey

**Choice:** `MediaViewerRoute` carries only `(postUri: String, imageIndex: Int)`. A new `:core:posts` module exposes `interface PostRepository { suspend fun getPost(uri: String): Result<PostUi> }`, backed by atproto-kotlin's `app.bsky.feed.getPosts` lexicon (single-post fetch — `uris = listOf(uri)` — no thread context). The `MediaViewerViewModel` constructor-injects `PostRepository`, calls `getPost(postUri)` on init, reads `PostUi.embed` (an `EmbedUi.Images` when the post carries images), and updates state. Coil's existing disk cache (already populated by the feed / post-detail's thumbnail render) makes thumbnail re-rendering instant; the `@fullsize` URL is a separate cache key and streams in.

**Why a new `:core:posts` module rather than reusing `:feature:postdetail:impl`'s `PostThreadRepository`:**

- *Reuse `PostThreadRepository`* — the repository is `internal` to `:feature:postdetail:impl`, so `:feature:mediaviewer:impl` can't depend on it without promoting it to a shared module. Promoting it would force the viewer to fetch ancestors and replies on every open even though it only needs the focus post — `getPostThread` is a heavier wire call than `getPosts`. Rejected on semantic-fit grounds.
- *Duplicate the `getPostThread` call inside `:feature:mediaviewer:impl`* — pragmatic but reintroduces the duplication that `:core:feed-mapping` was created to avoid. Same wire surface, two fetch sites. Rejected.
- *Extend `:core:posting`* — that module owns the *write* surface (creating posts, attachments, reply refs); naming a *read* surface alongside it would muddy the module's purpose. Rejected; `:core:posts` (read) and `:core:posting` (write) keep the verbs distinct.

`:core:posts`'s `PostRepository` projects via `:core:feed-mapping`'s shared `toPostUiCore` helper — same projection the feed and post-detail mappers use. No divergent local projection logic.

**Why this over alternatives:**

- *Pass `List<ImageUi>` through the NavKey* — would force `ImageUi` and its dependencies (`AspectRatio` if introduced, etc.) to become `@Serializable`, expanding the data-models contract for the benefit of one consumer. The serialized nav state grows linearly with the image set. The fullsize-URL story still has to be solved on the receiving end. Rejected on coupling grounds.
- *In-memory handoff via a singleton-scoped holder that PostDetail fills before navigating* — fragile across process death, hidden coupling, and not idiomatic with Nav 3's state-driven navigation. Rejected.

The re-fetch latency is bounded — we're hitting `PostRepository.getPost(uri)` which `getPostThread`'s caller already hits, with the URI's record being served from the same cache. First paint of the viewer renders a `Loading` spinner over black for one or two frames; the thumbnail flashes in from Coil cache; the `@fullsize` resolves shortly after. The viewer never bounces back to the post-detail screen on fetch failure — the user explicitly opened it, so we render an `Error` state with a retry affordance instead.

**Process death.** Because the NavKey carries only primitives, `kotlinx.serialization` round-trips it cleanly; the ViewModel's `init` re-runs on restoration and re-fetches. No `SavedStateHandle` plumbing needed beyond the assisted-inject route argument.

### Decision 3: `MediaViewerLoadStatus` is a sealed sum, not flat booleans

**Choice:** `MediaViewerState` carries a `loadStatus: MediaViewerLoadStatus` sealed interface with variants:

- `Loading` — no payload (centered spinner over black)
- `Loaded(images: ImmutableList<ImageUi>, currentIndex: Int, isChromeVisible: Boolean, isAltSheetOpen: Boolean)`
- `Error(error: UiText)` — sticky; renders a retry layout

**Why this over alternatives:**

- *Flat booleans (`isLoading: Boolean`, `images: ImmutableList<ImageUi>`, `error: UiText?`)* — these phases are mutually exclusive ("loading and loaded simultaneously" is invalid), and the per-Loaded fields (`currentIndex`, `isChromeVisible`, `isAltSheetOpen`) only make sense when `images` is populated. Flat booleans would require runtime invariants the type system could enforce. Per `CLAUDE.md`'s flat-vs-sealed rule, mutually exclusive view modes use a sealed status sum.
- *Wrap in `Async<T>`* — explicitly forbidden by `CLAUDE.md`. Per-screen sealed sums are the project's convention.

This matches `FeedLoadStatus`'s shape and the `add-postdetail-m3-expressive-treatment` `PostDetailLoadStatus` shape — same justification, same form.

### Decision 4: Tap-to-toggle chrome with 3-second auto-fade; `ALT` badge → `ModalBottomSheet`

**Choice:** Chrome (close button left, page indicator center "1 / 4", `ALT` badge right) is shown on entry, fades after 3 seconds, and toggles via single-tap on the image. Page changes set `isChromeVisible = true` and reset the timer. The `ALT` badge renders only when the current image's `altText != null`; tapping it opens a `ModalBottomSheet` showing the full alt text (scrollable for long descriptions). The sheet's `onDismissRequest` clears the `isAltSheetOpen` flag.

**Why this over alternatives:**

- *Always-visible chrome* — simpler to build but the gradient strips occlude tall portrait images and break the immersive feel that's the whole point of a fullscreen viewer. Rejected.
- *Auto-fade only (no tap-to-recall)* — leaves users hunting for the close button after 3s. Rejected; back press works but a visible close button is the primary affordance.
- *Always-visible alt text strip at the bottom* — competes for attention with the image; on long alt text, ellipsis-then-tap-for-more is a worse UX than a clearly-marked badge. Rejected.

The pattern matches Bluesky's own client (which uses an `ALT` badge → sheet). It's M3-native (`ModalBottomSheet`, `AnimatedVisibility`) and survives the screen-rotation / process-death paths cleanly because `isChromeVisible` and `isAltSheetOpen` are part of the `Loaded` state.

**Tap-vs-zoom gesture conflict.** Telephoto's `ZoomableAsyncImage` accepts `onClick` and `onLongClick` lambdas distinct from its zoom gestures, so the toggle wires into `onClick` cleanly. Double-tap is reserved for telephoto's double-tap-zoom; long-press is unbound (could be wired to a future "save image" action — not in this change).

### Decision 5: `HorizontalPager` always, even for single-image posts

**Choice:** Wrap the image set in `HorizontalPager(state = rememberPagerState(initialPage = route.imageIndex, pageCount = { state.images.size }))`, with `pageCount = 1` when the post has a single image. The pager's `userScrollEnabled` is bound to `currentZoomFactor <= 1f` so paging is disabled while zoomed.

**Why this over alternatives:**

- *Branch on `images.size == 1` and render `ZoomableAsyncImage` directly* — saves one Composable layer but doubles the number of code paths to test and screenshot. The pager renders a single page with negligible cost when `pageCount = 1`. Rejected.

The page indicator chrome ("1 / 4") is hidden when `state.images.size == 1` — the count is meaningless for a single image and the close button + ALT badge are the only chrome shown.

### Decision 6: `@fullsize` URL transform lives as an internal helper in `:feature:mediaviewer:impl`, not in `:data:models`

**Choice:** Add a tiny private helper inside `:feature:mediaviewer:impl`:

```kotlin
internal fun ImageUi.fullsizeUrl(): String =
    url.replace("@feed_thumbnail", "@fullsize")
        .ifBlank { url }  // defensive; replace shouldn't blank
```

If the URL doesn't carry the `@feed_thumbnail` segment (unexpected; logging a warning would be reasonable), we fall back to `url` unchanged. The helper is `internal`; if a second consumer ever needs fullsize URLs, promote it to `:data:models` then.

**Why this over alternatives:**

- *Add a `fullsizeUrl: String` field to `ImageUi`* — couples the data model to a Bluesky-specific URL transform, and forces every mapper / fixture / test to populate the field. The transform is deterministic from the existing `url` field; storing it twice is redundant. Rejected.
- *Do the transform inside `:core:feed-mapping`* — would require `:core:feed-mapping` to carry both `url` and `fullsizeUrl` (or pick one), and changes ripple through every consumer that depends on the existing `ImageUi.url` semantics. Rejected on the same redundancy grounds.

The transform is a CDN-implementation detail of Bluesky's blob serving (`…/<did>/<cid>@<size>`). If Bluesky ever changes the URL shape, this one helper updates; nothing else cares.

### Decision 7: Black background, no immersive-mode system-bar hiding

**Choice:** The viewer renders inside `MainShell`'s inner `NavDisplay` slot — same as `PostDetailScreen` — on a black `Surface(color = Color.Black)`. We do not flip the system bars to immersive (hidden) mode.

**Why this over alternatives:**

- *Hide system bars in immersive mode while viewer is open; restore on dismiss* — works but introduces a system-UI flicker on every dismiss (status bar slides back in over the post-detail screen) that the standard `NavDisplay` transition can't mask. The flicker is more disruptive than the bar's presence. Rejected for v1.
- *Edge-to-edge with the bars over a translucent black scrim* — the project already runs edge-to-edge at the activity level; the inner `NavDisplay` is naturally edge-to-edge inside `MainShell`'s scaffolding. The system bars sit over the black background and read as black-on-black, which is approximately the same effect as immersive without the flicker. This is what we ship.

If user feedback flags the bars as distracting, immersive can be revisited as a follow-up — the screen's scaffolding doesn't preclude it.

### Decision 8: Effect collector pattern for dismiss; no direct `LocalMainShellNavState` access from the ViewModel

**Choice:** `MediaViewerViewModel` emits `MediaViewerEffect.Dismiss` (no payload) when:
- The user swipes the image down past the dismiss threshold at min-zoom.
- The user presses the close button.
- A `BackHandler` collected at the screen level dispatches `OnBackPressed` → ViewModel emits `Dismiss`.

`MediaViewerScreen` collects `Dismiss` in a single `LaunchedEffect` and calls `LocalMainShellNavState.current.removeLast()`. The ViewModel never imports `LocalMainShellNavState` — same MVI-effect convention `PostDetailScreen` uses for its navigation effects, and same convention `CLAUDE.md`'s tab-internal navigation rule mandates.

**Why this over alternatives:**

- *Have the ViewModel inject the nav state holder* — explicitly forbidden by `CLAUDE.md`'s MVI rule (ViewModels never inject the nav state holder; `LocalMainShellNavState` is `CompositionLocal`-only).
- *Have the screen call `removeLast()` directly from the close-button `onClick` without going through an effect* — works but splits the dismiss surface across "VM-driven" (swipe / back) and "UI-driven" (close button) paths. Easier to reason about when all three converge on the same `Dismiss` effect.

## Risks / Trade-offs

- **Telephoto is third-party + sub-1.0.** Versioning is unstable. Mitigation: scoped to one module, one Composable; a swap touches that single call site. Renovate keeps the catalog version current and surfaces breaking changes as PRs.
- **Bluesky CDN URL shape assumption.** `@feed_thumbnail` → `@fullsize` is the transform observed today. If Bluesky introduces a different size token, the helper's fall-through (`url` unchanged) keeps the viewer functional but at thumbnail quality until the helper is updated. A unit test asserts the transform shape against representative URLs; failures surface fast.
- **Pager + zoom + dismiss gesture composition.** The three gestures must coexist. Mitigation: `HorizontalPager.userScrollEnabled` bound to telephoto's zoom factor (paging only at min-zoom); swipe-down `Modifier.draggable` early-returns when zoom factor > 1f. Both rules are unit-testable at the state-machine level and surface on the viewer's instrumented test for the multi-image-zoomed-pager path.
- **Process death during fetch.** If the user backgrounds the app while `Loading`, restoration re-runs `PostRepository.getPost(postUri)`. If the post has been deleted server-side between sessions, the `Error` variant surfaces with a retry that won't succeed; the user can dismiss back to `PostDetail` (which itself will surface its own error on the next fetch). This is acceptable v1 behavior.
- **Coil disk cache miss for `@fullsize`.** First-time viewing of any image will hit the network for the fullsize variant. Coil's existing image-loader configuration handles this transparently; the viewer renders the `@feed_thumbnail` (cached) until `@fullsize` resolves, then swaps. Telephoto's `ZoomableAsyncImage` plays nicely with Coil's placeholder behavior — no extra wiring needed.

## Acceptance gates

- Tapping a focus-post image in `PostDetailScreen` opens `MediaViewerScreen` at the tapped index. Manual: verified on both single-image and multi-image focus posts.
- Pinch-to-zoom + double-tap-zoom both work via telephoto's gesture stack. Manual: verified zoom factor goes above 1f and pan-while-zoomed stays within image bounds.
- At min-zoom, swipe-left/right pages between images of the same post. Above min-zoom, paging is disabled (telephoto pan absorbs the gesture). Manual: verified.
- Swipe-down at min-zoom past the dismiss threshold dismisses the viewer back to `PostDetailScreen`. Manual: verified.
- The `Timber.tag("PostDetailScreen") d "...nubecita-e02..."` log line, the `mediaViewerComingSoonMessage` local, and the `R.string.postdetail_snackbar_media_viewer_coming_soon` resource are removed. Repo grep verifies.
- Tapping ancestor / reply images stays a no-op (m28.5.2 Decision 4 preserved).
- Screenshot suite: `MediaViewerScreen` rendered as `Loading` / `Loaded(single)` / `Loaded(multi)` / `Error` / alt-sheet-open, captured in both `Light` and `Dark` themes. Pager gesture and zoom interactions are not screenshot-testable; they're verified by an instrumented `androidTest` that drives the pager and dismisses via back press.
- Unit tests: `MediaViewerViewModelTest` covers init load, page change updates `currentIndex`, alt-sheet open/close, error → retry → success, and the dismiss-effect emission paths (swipe, close, back).
- `PostDetailViewModelTest` regression: the existing "OnFocusImageClicked emits NavigateToMediaViewer with the focus URI and image index" test stays green; a new screen-level test asserts the effect collector now invokes the hoisted callback (instead of the Snackbar dispatch the previous test asserted).
