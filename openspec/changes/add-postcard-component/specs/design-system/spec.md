## ADDED Requirements

### Requirement: PostCard is the canonical post-rendering composable

The module MUST expose `@Composable fun PostCard(post: PostUi, callbacks: PostCallbacks = PostCallbacks(), modifier: Modifier = Modifier)` at `net.kikin.nubecita.designsystem.component.PostCard`. Every screen in the app that lists or focuses on a single Bluesky post (home feed, profile timeline, search results, notifications, thread view, post detail) MUST consume this composable; ad-hoc per-screen post-rendering composables MUST NOT be introduced.

PostCard is stateless — it owns no `remember`-d state beyond what's needed for sub-component memoization (e.g., `rememberBlueskyAnnotatedString`'s memo). Callers retain ownership of viewer state (`isLikedByViewer`, `isRepostedByViewer`) on their own `PostUi` instances and re-emit a new `PostUi` when state changes.

PostCard is **loaded-state-only**. It MUST NOT accept a `status`, `state`, or `isLoading` parameter; loading is rendered by the separate `PostCardShimmer()` composable that the host substitutes instead, and error / empty states are owned by the host screen at the list level (see `design.md` Decision 9 for the full state-ownership table).

#### Scenario: A feed list cell uses PostCard

- **WHEN** the feed screen's `LazyColumn` renders an item
- **THEN** the only post-rendering composable invoked is `PostCard(post = ..., callbacks = ...)` and no ad-hoc post-shaped layout is built inline.

#### Scenario: Toggling like fires the callback without local state

- **WHEN** a user taps the like icon on a PostCard whose `post.viewer.isLikedByViewer == false`
- **THEN** `callbacks.onLike(post)` fires exactly once, and the visual liked-state of the card does NOT flip until the host VM produces a new `PostUi` with `viewer.isLikedByViewer == true` (PostCard reads only what's in `post`).

### Requirement: PostCard renders facet-styled body text via the upstream helper

PostCard's body text MUST be rendered by passing `post.text` and `post.facets` to `io.github.kikin81.atproto.compose.material3.rememberBlueskyAnnotatedString(text, facets)` and binding the result into a `Text(annotated, ...)` call. PostCard MUST NOT re-implement facet rendering, MUST NOT call into `appendBlueskyText` or `buildBlueskyAnnotatedString` directly, and MUST NOT pre-format the text outside the composition (the upstream helper reads `MaterialTheme.colorScheme.primary` for link styling — pre-formatting loses dynamic-color reactivity).

#### Scenario: A post with a mention renders the mention as a styled span

- **WHEN** PostCard renders a `PostUi` whose `text == "Hello @alice.bsky.social!"` and whose `facets` contains a single `Facet` with one `FacetMention` feature spanning the mention
- **THEN** the rendered `Text` displays "Hello @alice.bsky.social!" with the mention range styled as `MaterialTheme.colorScheme.primary` and tappable via the standard Compose `LinkInteractionListener` (or, for mentions, via a `getStringAnnotations(ANNOTATION_TAG_MENTION, ...)` lookup if the host wires custom click handling).

#### Scenario: A post with no facets renders plain text

- **WHEN** PostCard renders a `PostUi` whose `facets.isEmpty()`
- **THEN** the rendered `Text` shows the raw `post.text` with body-large typography and no styled spans.

#### Scenario: Theme color change recomposes the styled spans

- **WHEN** the host app toggles dark mode (or the user changes the system Material You wallpaper)
- **THEN** the link-styled facet ranges in every visible PostCard recompose with the new `MaterialTheme.colorScheme.primary` value automatically, with no host intervention.

### Requirement: PostCard's embed slot dispatches on the sealed `EmbedUi` type

PostCard MUST contain an embed slot that uses a `when (post.embed)` expression to dispatch to one of the supporting embed composables. The dispatch MUST be exhaustive over `EmbedUi.Empty`, `EmbedUi.Images`, and `EmbedUi.Unsupported`:

- `EmbedUi.Empty` → no embed slot rendered
- `EmbedUi.Images` → `PostCardImageEmbed(items = embed.items)` (1–4 images, see separate requirement)
- `EmbedUi.Unsupported` → `PostCardUnsupportedEmbed(typeUri = embed.typeUri)`

When new embed variants land in future changes (e.g., `EmbedUi.External` per `nubecita-aku`, `EmbedUi.Record` per `nubecita-6vq`), this dispatch MUST be extended in the same change that adds the variant — the sealed type makes this a compile error otherwise.

#### Scenario: Image embed renders the supporting composable

- **WHEN** PostCard renders a `PostUi` whose `embed is EmbedUi.Images` with two image items
- **THEN** `PostCardImageEmbed` is invoked exactly once with the `items` list, and the composable lays out the two images per the image-embed sub-requirement.

#### Scenario: Unsupported embed renders the deliberate-degradation chip

- **WHEN** PostCard renders a `PostUi` whose `embed is EmbedUi.Unsupported(typeUri = "app.bsky.embed.video")`
- **THEN** `PostCardUnsupportedEmbed` is invoked, rendering a small `surfaceContainerHighest` chip with secondary-text label "Unsupported embed: video" — derived from the lexicon URI via a friendly-name mapping (`app.bsky.embed.video` → `"video"`, `app.bsky.embed.external` → `"link card"`, `app.bsky.embed.record` → `"quoted post"`, `app.bsky.embed.recordWithMedia` → `"quoted post with media"`, anything unknown → `"embed"`). No error styling, no error icon — this is deliberate degradation, not a failure. (Earlier draft of this spec gated the URI reveal on `BuildConfig.DEBUG`; switched to the always-on friendly mapping to avoid forcing `buildConfig = true` in `:designsystem` for one debug string and to give end users a readable label rather than a lexicon path.)

### Requirement: PostCardImageEmbed lays out 1–4 images with deterministic geometry

The module MUST expose `@Composable fun PostCardImageEmbed(items: ImmutableList<ImageUi>, modifier: Modifier = Modifier)` at `net.kikin.nubecita.designsystem.component.PostCardImageEmbed`. The composable renders an image grid sized to a fixed maximum height (180dp baseline, matching the reference UI kit's `PostCard.kt`):

- 1 image: full-width, max-height-clamped, `ContentScale.Crop`
- 2 images: side-by-side equal columns, full-width
- 3 images: one full-height column on the left, two stacked half-height on the right
- 4 images: 2×2 grid

Every image cell MUST consume `NubecitaAsyncImage(model = item.url, contentDescription = item.altText, ...)` so placeholder/error/crossfade come from the design-system's standardized Coil wrapper. Cells MUST clip to `RoundedCornerShape(16.dp)`.

#### Scenario: Single-image post renders one full-width image

- **WHEN** `PostCardImageEmbed(items = persistentListOf(ImageUi(url = "...", altText = "...")))` is composed
- **THEN** the layout is one Box with `fillMaxWidth().heightIn(max = 180.dp).clip(RoundedCornerShape(16.dp))` containing one `NubecitaAsyncImage` with `ContentScale.Crop`.

#### Scenario: Two-image post renders side-by-side

- **WHEN** `PostCardImageEmbed(items = persistentListOf(image1, image2))` is composed
- **THEN** the layout is a `Row` with two equal-weight columns, each containing a `NubecitaAsyncImage` clipped to rounded corners.

### Requirement: PostCard exposes interaction callbacks via a `PostCallbacks` data class

PostCard's interaction surface MUST be expressed as a single `data class PostCallbacks` parameter, not as flat lambda parameters on the composable signature. The data class MUST default each callback to a no-op so previews and tests can construct `PostCallbacks()` with no arguments.

```kotlin
data class PostCallbacks(
    val onTap: (PostUi) -> Unit = {},
    val onAuthorTap: (AuthorUi) -> Unit = {},
    val onLike: (PostUi) -> Unit = {},
    val onRepost: (PostUi) -> Unit = {},
    val onReply: (PostUi) -> Unit = {},
    val onShare: (PostUi) -> Unit = {},
)
```

#### Scenario: A preview composes PostCard with no callbacks

- **WHEN** a `@Preview` calls `PostCard(post = previewPost)`
- **THEN** the composable compiles and renders without warnings; `callbacks` defaults to `PostCallbacks()` and every interaction is a no-op.

#### Scenario: A host wires real callbacks once per screen

- **WHEN** `FeedScreen` composes `LazyColumn { items(posts) { post -> PostCard(post, callbacks = remember { PostCallbacks(onTap = { ... }, ...) }) } }`
- **THEN** the same `PostCallbacks` instance is reused across all visible items, per Compose's stability rules.

### Requirement: `Modifier.shimmer()` provides a reusable animated loading-state placeholder

The module MUST expose `@Composable fun Modifier.shimmer(durationMillis: Int = 1500): Modifier` at `net.kikin.nubecita.designsystem.component.shimmer`. The modifier MUST paint an animated horizontal linear-gradient brush via `drawWithCache { onDrawBehind { ... } }`, cycling through three theme-derived colors sourced from `MaterialTheme.colorScheme` (`surfaceContainerHighest` → `surfaceContainerHigh` → `surfaceContainerHighest`) driven by `rememberInfiniteTransition`. The modifier MUST follow light/dark/dynamic-color theme switches automatically without consumer wiring.

The module MUST NOT depend on any third-party shimmer/placeholder library (Accompanist's `accompanist-placeholder-material` is deprecated; Material 3's experimental `Modifier.placeholder` is removed). The implementation uses only stable Compose UI primitives (`rememberInfiniteTransition`, `drawWithCache`, `Brush.linearGradient`).

#### Scenario: Applying shimmer to any composable

- **WHEN** a developer writes `Box(modifier = Modifier.size(40.dp).clip(CircleShape).shimmer())`
- **THEN** the Box renders an animated linear-gradient brush that cycles continuously, clipped to the circular shape, with colors sourced from the active `MaterialTheme.colorScheme`.

#### Scenario: Theme switch updates shimmer colors

- **WHEN** the host app toggles dark mode while shimmered placeholders are visible
- **THEN** every `Modifier.shimmer()` instance recomposes with the new theme's `surfaceContainerHighest` / `surfaceContainerHigh` colors automatically.

#### Scenario: Shimmer animation runs at composition lifecycle

- **WHEN** a composable using `Modifier.shimmer()` enters composition
- **THEN** `rememberInfiniteTransition` starts the animation; when the composable leaves composition (e.g., scrolls offscreen in a `LazyColumn`) the transition is cancelled per Compose's standard lifecycle handling.

### Requirement: PostCardShimmer renders a PostCard-shaped loading skeleton

The module MUST expose `@Composable fun PostCardShimmer(modifier: Modifier = Modifier)` at `net.kikin.nubecita.designsystem.component.PostCardShimmer`. The composable MUST assemble a skeleton layout that geometrically mirrors `PostCard`:

- A 40dp circular placeholder for the avatar (matches `NubecitaAvatar`'s default size)
- Two staggered text bars: one shorter (representing display name + handle, ~60% width), one full-width (body text, second line ~40% width)
- An optional image-shaped placeholder (180dp tall, matching `PostCardImageEmbed`'s baseline geometry)
- Four small action-row dots evenly spaced

Every shape MUST have `Modifier.shimmer()` applied. The composable MUST be stateless and trivially previewable.

The feed screen and other post-listing screens MUST consume `PostCardShimmer` (not `PostCard` with placeholder data) for loading-state rendering. PostCard itself MUST remain loaded-state-only — it does not own a loading variant.

#### Scenario: Feed loading list renders shimmer placeholders

- **WHEN** the feed screen's `LazyColumn` is in initial-load state and renders placeholder slots
- **THEN** each placeholder is `PostCardShimmer()` (typically 6–8 instances, matching the visible viewport), and once data arrives the same `LazyColumn` swaps the shimmers for `PostCard` instances.

#### Scenario: PostCardShimmer renders without arguments

- **WHEN** a `@Preview` calls `PostCardShimmer()`
- **THEN** the composable renders the full skeleton (avatar circle + text bars + action dots) at PostCard's default geometry, with all shapes shimmering.

### Requirement: PostCard ships @Preview variants exercising every visual state

The PostCard source file MUST include `@Preview` composables (decorated with `@PreviewParameter` or as separate `private fun` previews) covering at least the following visual states. Each preview MUST be wrapped in `NubecitaTheme { ... }` and labeled via `@Preview(name = "...")`:

- Empty body, no embed, zero stats — a minimal "just posted" state
- Typical post: short body text, no embed, mid-range stats, viewer-not-following author
- Post with a single image embed
- Post with an Unsupported embed (e.g., `typeUri = "app.bsky.embed.video"`)
- Post with `repostedBy = "Alice Chen"` showing the kicker line
- Post with mention + link facets so the AnnotatedString styling is visible in the preview

Previews exist as compositional smoke tests — they MUST render in Studio's preview pane without throwing, even though `rememberBlueskyAnnotatedString` doesn't actually fetch network resources during preview.

#### Scenario: Studio preview pane renders all variants

- **WHEN** a developer opens `PostCard.kt` in Android Studio with the preview pane visible
- **THEN** every `@Preview` listed above renders without exceptions and visually matches the design-spec layout.
