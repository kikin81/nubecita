# design-system Specification

## Purpose
TBD - created by archiving change add-designsystem-theme. Update Purpose after archive.
## Requirements
### Requirement: NubecitaTheme is the single entry point for brand styling

The app MUST expose a single `@Composable fun NubecitaTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit)` at `net.kikin.nubecita.designsystem.NubecitaTheme`. Feature code MUST wrap its UI content with `NubecitaTheme { ... }` and MUST NOT call `MaterialTheme` or `MaterialExpressiveTheme` directly — those are implementation details of this module.

#### Scenario: Composition root wires the theme

- **WHEN** `MainActivity.onCreate` calls `setContent { ... }`
- **THEN** the outermost composable inside is `NubecitaTheme { ... }` and all descendants read brand tokens via `MaterialTheme.*` without re-importing them.

#### Scenario: Dynamic color opt-out

- **WHEN** `NubecitaTheme(dynamicColor = false) { ... }` is composed on an Android 12+ device
- **THEN** the brand palette (Sky / Peach / Lilac / Neutral) is used instead of wallpaper-derived tones, and `MaterialTheme.colorScheme.primary` equals the brand Sky-50 (`#0A7AFF`) in light mode.

#### Scenario: Dynamic color default-on

- **WHEN** `NubecitaTheme { ... }` is composed without an explicit `dynamicColor` argument on an Android 12+ device
- **THEN** `MaterialTheme.colorScheme` is sourced from `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)` and brand colors are NOT visible.

#### Scenario: Dynamic color on pre-Android-12

- **WHEN** `NubecitaTheme { ... }` is composed on an Android 11 or earlier device, regardless of the `dynamicColor` argument
- **THEN** the brand palette is used (the dynamic color API isn't available) and `MaterialTheme.colorScheme.primary` equals the brand Sky-50.

### Requirement: Every Material 3 color role is populated from the brand palette

The six `ColorScheme`s (light, light-medium-contrast, light-high-contrast, dark, dark-medium-contrast, dark-high-contrast) exposed by the module MUST populate every Material 3 color role from the Sky / Peach / Lilac / Neutral / NeutralVariant tonal palette defined in `openspec/references/design-system/colors_and_type.css`. No Material-default (e.g., the stock `Purple40`) color MUST remain reachable via `MaterialTheme.colorScheme.*`.

#### Scenario: Every role has a brand color

- **WHEN** any of the six `ColorScheme`s is instantiated
- **THEN** all of `primary`, `onPrimary`, `primaryContainer`, `onPrimaryContainer`, `secondary`, `onSecondary`, `secondaryContainer`, `onSecondaryContainer`, `tertiary`, `onTertiary`, `tertiaryContainer`, `onTertiaryContainer`, `error`, `onError`, `errorContainer`, `onErrorContainer`, `background`, `onBackground`, `surface`, `onSurface`, `surfaceVariant`, `onSurfaceVariant`, `outline`, `outlineVariant`, `scrim`, `inverseSurface`, `inverseOnSurface`, `inversePrimary`, `surfaceDim`, `surfaceBright`, `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest` resolve to values derived from the brand tonal palette.

#### Scenario: Light-mode primary matches the CSS token

- **WHEN** `NubecitaTheme(darkTheme = false, dynamicColor = false)` is composed
- **THEN** `MaterialTheme.colorScheme.primary` equals `Color(0xFF0A7AFF)` — the CSS `--sky-50`.

#### Scenario: Dark-mode primary matches the CSS token

- **WHEN** `NubecitaTheme(darkTheme = true, dynamicColor = false)` is composed
- **THEN** `MaterialTheme.colorScheme.primary` equals `Color(0xFFA6C8FF)` — the CSS `--sky-80`, matching the dark-mode role mapping in `colors_and_type.css`.

### Requirement: Typography uses the brand font roster

The `Typography` exposed via `MaterialTheme.typography` MUST populate all 15 Material 3 type roles (`displayLarge`, `displayMedium`, `displaySmall`, `headlineLarge`, `headlineMedium`, `headlineSmall`, `titleLarge`, `titleMedium`, `titleSmall`, `bodyLarge`, `bodyMedium`, `bodySmall`, `labelLarge`, `labelMedium`, `labelSmall`). Font family assignments MUST be:

- `displayLarge`, `displayMedium`, `displaySmall`, `headlineLarge` → Fraunces (with `FontVariation.Setting("SOFT", 50f)` on API 26+)
- `headlineMedium`, `headlineSmall`, all `title*`, all `body*`, all `label*` → Roboto Flex
- Mono/fixed-width usage → exposed on `NubecitaTokens.typography.mono` using JetBrains Mono (not an M3 role)

`bodyLarge` MUST be 17sp / 26sp line-height (one step above M3's default 16/24) — the feed is Nubecita's primary reading surface and this bump materially helps long-session readability per the brand system.

#### Scenario: Display type uses Fraunces

- **WHEN** a composable reads `MaterialTheme.typography.displayLarge`
- **THEN** the returned `TextStyle.fontFamily` resolves to the Fraunces `FontFamily` (bundled as a Downloadable Google Font).

#### Scenario: Body-large uses the bumped size

- **WHEN** a composable reads `MaterialTheme.typography.bodyLarge`
- **THEN** `fontSize` is `17.sp` and `lineHeight` is `26.sp`.

#### Scenario: Mono type is available via extension

- **WHEN** a composable reads `MaterialTheme.typography` it does NOT find a mono role (M3 has none); a composable reading `MaterialTheme.extendedTypography.mono` resolves to a `TextStyle` with JetBrains Mono.

### Requirement: Shape roles map to the brand shape scale

`MaterialTheme.shapes` MUST populate M3's five shape roles (`extraSmall`, `small`, `medium`, `large`, `extraLarge`) with corner radii 4dp, 8dp, 12dp, 16dp, 28dp respectively — matching the CSS tokens `--shape-xs` through `--shape-xl`. The CSS `--shape-2xl` (36dp) and `--shape-full` (pill / `CircleShape`) MUST be exposed via `NubecitaTokens.extendedShape`.

M3 Expressive's `Button` defaults to a pill shape via `ButtonDefaults`; this module MUST NOT override that behavior. All buttons produced via M3's `Button` / `FilledTonalButton` / `OutlinedButton` / `TextButton` / `FloatingActionButton` composables SHOULD render as pill-shaped by default.

#### Scenario: Card shape uses the brand radius

- **WHEN** a composable reads `MaterialTheme.shapes.large`
- **THEN** it is a `RoundedCornerShape(16.dp)`.

#### Scenario: Pill shape is available

- **WHEN** a composable reads `MaterialTheme.extendedShape.pill`
- **THEN** it is `CircleShape` (a.k.a. pill / fully rounded).

### Requirement: Motion is governed by the brand spring system

A `NubecitaMotion` data class MUST expose the brand motion intent as Compose `FiniteAnimationSpec`s: `defaultSpatial`, `slowSpatial`, `bouncy`, `defaultEffects`, and `defaultEmphasized`. The standard variant uses spring/keyframes specs translated from the CSS `ease-spring-fast`, `ease-spring-slow`, and `ease-spring-bouncy` curves. Durations (`--dur-short-1..x-long-1`) MUST be exposed on `NubecitaTokens.motionDurations` in milliseconds.

`NubecitaMotion` MUST be reachable via `MaterialTheme.motion` (extension property reading from `LocalNubecitaTokens`). Material3's own `MotionScheme` is **not** customizable as of Compose BOM `2026.04.01` (it's an `internal` API), so this module does NOT attempt to configure `MaterialTheme.motionScheme`; feature code wanting brand-consistent motion passes `MaterialTheme.motion.*` specs explicitly to `animate*AsState`, `AnimatedVisibility`, etc.

When the Android runtime `AccessibilityManager` reports that animations should be removed (reduce-motion setting on), `MaterialTheme.motion` MUST return a `NubecitaMotion` whose specs are tween-based with `LinearEasing` and halved durations. The swap MUST be reactive — the value MUST update without app restart when the setting changes.

#### Scenario: Default spatial motion is a brand spring

- **WHEN** a composable reads `MaterialTheme.motion.defaultSpatial`
- **THEN** the returned `FiniteAnimationSpec<Float>` is a `spring()` with dampingRatio and stiffness tuned to the CSS `ease-spring-fast` overshoot (~300ms settle).

#### Scenario: Reduce-motion removes overshoot

- **WHEN** the device's "remove animations" accessibility setting is enabled and a composable reads `MaterialTheme.motion.defaultSpatial`
- **THEN** the returned `FiniteAnimationSpec<Float>` is a `tween` with `LinearEasing` and duration ≤ 150ms.

### Requirement: Extended tokens are reachable via MaterialTheme extension properties

The module MUST expose these extension properties on `MaterialTheme` (all `@Composable` and `@ReadOnlyComposable`):

- `MaterialTheme.spacing: NubecitaSpacing` — `s0, s1, s2, s3, s4, s5, s6, s7, s8, s10, s12, s16, s20, s24` (4pt scale, matching CSS `--s-*`)
- `MaterialTheme.elevation: NubecitaElevation` — `e1, e2, e3, e4, e5` as `Dp` for stock M3 `Surface(tonalElevation = ...)` / `Modifier.shadow(...)` usage
- `MaterialTheme.semanticColors: NubecitaSemanticColors` — `success, onSuccess, successContainer, onSuccessContainer, warning, onWarning` (not in M3 stock `ColorScheme`)
- `MaterialTheme.motion: NubecitaMotion` — brand motion specs (`defaultSpatial`, `slowSpatial`, `bouncy`, `defaultEffects`, `defaultEmphasized`)
- `MaterialTheme.motionDurations: NubecitaDurations` — `short1 = 50ms, short2 = 100ms, ..., xLong1 = 700ms`
- `MaterialTheme.extendedShape: NubecitaExtendedShape` — `extraExtraLarge = RoundedCornerShape(36.dp), pill = CircleShape, sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)`
- `MaterialTheme.extendedTypography: NubecitaExtendedTypography` — `mono` (`TextStyle` using JetBrains Mono)

Each backing `data class` MUST be a plain value container (no composables, no state), provided to the composition via `LocalNubecitaTokens` `CompositionLocal` at the `NubecitaTheme` root.

#### Scenario: Spacing is read ergonomically

- **WHEN** a composable calls `Modifier.padding(MaterialTheme.spacing.s4)`
- **THEN** the padding is 16.dp (the CSS `--s-4` token).

#### Scenario: Semantic color for success is available

- **WHEN** a composable reads `MaterialTheme.semanticColors.success` in light mode
- **THEN** it is `Color(0xFF006D3F)` — the CSS `--success-40` token.

#### Scenario: CompositionLocal is provided at theme root

- **WHEN** a composable inside `NubecitaTheme` reads `LocalNubecitaTokens.current`
- **THEN** the returned `NubecitaTokens` is non-null and contains all extended token groups.

#### Scenario: CompositionLocal fails loudly outside the theme

- **WHEN** a composable reads `MaterialTheme.spacing` outside a `NubecitaTheme` scope
- **THEN** it throws a descriptive `IllegalStateException` naming `NubecitaTheme` as the missing ancestor.

### Requirement: Font delivery uses hybrid bundle + downloadable strategy

Roboto Flex and JetBrains Mono MUST be bundled as `.ttf` resources under `designsystem/src/main/res/font/`. Fraunces and Material Symbols Rounded MUST be declared as `androidx.compose.ui.text.googlefonts.GoogleFont` via the Google Fonts provider, with the certificate array declared in `designsystem/src/main/res/values/font_certs.xml`. Every `GoogleFont` declaration MUST specify a fallback (`FontFamily.Serif` for Fraunces; system default for Material Symbols) for devices without Play Services.

#### Scenario: Body text renders instantly

- **WHEN** the app starts on a fresh install and the first composable renders `Text(..., style = MaterialTheme.typography.bodyLarge)`
- **THEN** the text renders in Roboto Flex immediately (no FOUT) because it's bundled.

#### Scenario: Display text may FOUT on first launch

- **WHEN** the app starts on a fresh install (no Fraunces cached by the provider) and a composable renders `Text(..., style = MaterialTheme.typography.displayLarge)`
- **THEN** the text may render briefly in `FontFamily.Serif` before swapping to Fraunces once the Downloadable Fonts provider returns.

### Requirement: Feature code MUST NOT hard-code theme values

Feature modules, activities, and UI composables MUST obtain all color, typography, shape, spacing, elevation, and motion values from `MaterialTheme.*` (including the extension properties defined by this module). They MUST NOT declare `Color(0xFF…)` literals, `…dp` constants that duplicate a spacing token, or `TextStyle(...)` with hard-coded font sizes that duplicate a Typography role.

Exceptions, narrowly scoped:
- Asset drawables / vector resources (XML-authored, not Compose) may reference `@color/…` resources.
- Previews' background colors for demonstration-only purposes may use direct `Color`.
- Screen-specific one-offs that don't fit any token (rare; require justification in a code comment).

#### Scenario: Feature code reads tokens via MaterialTheme

- **WHEN** `git grep -nE 'Color\(0x[0-9A-Fa-f]+\)' app/src/main` is run (ignoring generated files)
- **THEN** the only matches are inside the `:designsystem` module or in `@Preview` scaffolding — never in feature production code paths.

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

PostCard MUST contain an embed slot that uses a `when (post.embed)` expression to dispatch to one of the supporting embed composables. The dispatch MUST be exhaustive over `EmbedUi.Empty`, `EmbedUi.Images`, `EmbedUi.Video`, `EmbedUi.External`, `EmbedUi.Record`, `EmbedUi.RecordUnavailable`, `EmbedUi.RecordWithMedia`, and `EmbedUi.Unsupported`:

- `EmbedUi.Empty` → no embed slot rendered
- `EmbedUi.Images` → `PostCardImageEmbed(items = embed.items)` (1–4 images, see separate requirement)
- `EmbedUi.Video` → `videoEmbedSlot(embed)` (host-supplied; see separate requirement)
- `EmbedUi.External` → `PostCardExternalEmbed(uri, domain, title, description, thumbUrl, onTap = callbacks.onExternalEmbedTap)`
- `EmbedUi.Record` → `PostCardQuotedPost(quotedPost = embed.quotedPost, quotedVideoEmbedSlot)` (see separate requirement)
- `EmbedUi.RecordUnavailable` → `PostCardRecordUnavailable(reason = embed.reason)` (see separate requirement)
- `EmbedUi.RecordWithMedia` → `PostCardRecordWithMediaEmbed(record = embed.record, media = embed.media, onExternalMediaTap = callbacks.onExternalEmbedTap, videoEmbedSlot, quotedVideoEmbedSlot)` (see separate requirement)
- `EmbedUi.Unsupported` → `PostCardUnsupportedEmbed(typeUri = embed.typeUri)`

Future lexicon evolution that adds an `EmbedUi` variant MUST be handled in the same change that adds the variant — the sealed type makes this a compile error otherwise.

#### Scenario: Image embed renders the supporting composable

- **WHEN** PostCard renders a `PostUi` whose `embed is EmbedUi.Images` with two image items
- **THEN** `PostCardImageEmbed` is invoked exactly once with the `items` list, and the composable lays out the two images per the image-embed sub-requirement.

#### Scenario: Record embed renders the quoted-post composable

- **WHEN** PostCard renders a `PostUi` whose `embed is EmbedUi.Record` with a populated `QuotedPostUi`
- **THEN** `PostCardQuotedPost` is invoked exactly once with the quoted post data; the parent post's body text + author header continue to render above the quoted-post surface

#### Scenario: RecordUnavailable embed renders the unavailable chip

- **WHEN** PostCard renders a `PostUi` whose `embed is EmbedUi.RecordUnavailable(Reason.NotFound)`
- **THEN** `PostCardRecordUnavailable` is invoked with `reason = Reason.NotFound`; renders a small chip with copy "Quoted post unavailable" — the same copy is rendered for `Reason.Blocked`, `Reason.Detached`, and `Reason.Unknown` (single-stub per the design)

#### Scenario: RecordWithMedia embed renders the composite composable

- **WHEN** PostCard renders a `PostUi` whose `embed is EmbedUi.RecordWithMedia` carrying a resolved `Record` + `Images` media
- **THEN** `PostCardRecordWithMediaEmbed` is invoked exactly once with the record + media slots, the parent's `callbacks.onExternalEmbedTap` (for the case where the media is `External`), and both video slot lambdas. The composable lays media above the quoted card per its own requirement.

#### Scenario: Unsupported embed renders the deliberate-degradation chip

- **WHEN** PostCard renders a `PostUi` whose `embed is EmbedUi.Unsupported(typeUri = "app.bsky.embed.somethingNew")`
- **THEN** `PostCardUnsupportedEmbed` is invoked, rendering a small `surfaceContainerHighest` chip with secondary-text label derived from the lexicon URI via the existing friendly-name mapping. No error styling, no error icon — this is deliberate degradation, not a failure.

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

### Requirement: `RobotoFlexFontFamily` honors the variable `wght` axis via per-weight `FontVariation` declarations

`RobotoFlexFontFamily` MUST declare one `Font` entry per supported `FontWeight` referenced by `nubecitaTypography` in `Type.kt`. Each entry MUST pass `variationSettings = FontVariation.Settings(FontVariation.weight(N))` so the Compose text engine resolves the correct axis position when rendering. At minimum, the family MUST include entries for `FontWeight.Normal` (400), `FontWeight.Medium` (500), `FontWeight.SemiBold` (600), and `FontWeight.Bold` (700). All entries reference the same bundled `R.font.roboto_flex` variable .ttf — the FontVariation settings are what make the rendering differ.

#### Scenario: bodyLarge renders at FontWeight.Normal visually distinct from titleMedium SemiBold

- **WHEN** a screenshot test renders two stacked `Text` composables — one styled `bodyLarge` (declared `FontWeight.Normal`) and one styled `titleMedium` (declared `FontWeight.SemiBold`) — with identical text content
- **THEN** the rendered glyphs SHALL show visibly different stroke weights — the SemiBold text SHALL be heavier than the Normal text

#### Scenario: Adding a new FontWeight to Type.kt requires a corresponding FontFamily entry

- **WHEN** a future change adds a `Type.kt` style declaring `FontWeight.ExtraBold` (800) on `RobotoFlexFontFamily`
- **THEN** that change MUST also add a `Font(resId = R.font.roboto_flex, weight = FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800)))` entry to `RobotoFlexFontFamily`, otherwise the new style SHALL fall back to the closest declared weight at the same heavy-default rendering this requirement was created to fix

### Requirement: `PostCard.AuthorLine` renders displayName + handle + timestamp in a single non-wrapping row with right-pinned timestamp

`PostCard.AuthorLine` MUST render the author's display name, handle, and relative timestamp on exactly one visual line, regardless of handle length. The layout uses three separate `Text` composables in a `Row(verticalAlignment = Alignment.CenterVertically)`:

- The display name has `maxLines = 1` and `overflow = TextOverflow.Ellipsis`. It does NOT take a layout weight — it claims its intrinsic width up to the row's available space.
- The handle has `maxLines = 1`, `overflow = TextOverflow.Ellipsis`, and `Modifier.weight(1f, fill = false)` so it shrinks first when horizontal space is constrained.
- A `Spacer(Modifier.weight(1f))` between the handle and the timestamp absorbs all remaining horizontal space, pinning the timestamp to the row's trailing edge.
- The timestamp `Text` has `maxLines = 1` and no weight modifier — it renders at its intrinsic width on the right.

The string resources `postcard_handle` (formatted as `@%1$s`) and `postcard_relative_time` (the rendered duration string from the existing relative-time helper) replace the prior composite `postcard_handle_and_timestamp` resource.

#### Scenario: Long handle truncates with ellipsis instead of wrapping the timestamp

- **WHEN** a `PostCard` renders a post whose handle is `someverylonghandle.bsky.social` (30+ chars) and whose display name is `Alice Chen`
- **THEN** the row SHALL render on exactly one visual line: `Alice Chen   @someverylonghan…       5h` — the handle truncates with an ellipsis and the timestamp remains right-pinned

#### Scenario: Short handle leaves slack between handle and timestamp

- **WHEN** a `PostCard` renders a post whose handle is `alice.bsky.social` and display name is `Alice Chen`
- **THEN** the row SHALL render the full display name + full handle on the left, the full timestamp on the right, with the `Spacer(weight = 1f)` filling the gap between them

#### Scenario: Empty display name still pins timestamp right

- **WHEN** a `PostCard` renders a post whose `author.displayName` is the empty string (Bluesky permits this)
- **THEN** the row SHALL render the handle on the left and the timestamp on the right with no visual misalignment

### Requirement: `PostCard` exposes a `videoEmbedSlot` lambda for host-supplied video render

`PostCard` MUST accept an optional `videoEmbedSlot: @Composable (EmbedUi.Video) -> Unit = {}` parameter. When `EmbedSlot` dispatches and the post's embed is `EmbedUi.Video`, it MUST invoke the slot lambda with the video data; the default empty lambda renders nothing (so a `:designsystem`-only consumer that does NOT supply the slot sees no video render — preserving the v0 behavior where `EmbedUi.Video` would have fallen through). `:designsystem` MUST NOT import or depend on any feature module to render the video; the slot's body lives in `:feature:feed:impl` and is supplied by `FeedScreen` when it constructs PostCard.

`PostCard` adds NO new `PostCallbacks` lambdas for video. Card-body tap on a video card uses the existing `PostCallbacks.onTap(post)` to navigate to detail. Video-specific user gestures (mute / unmute) are handled directly by the host-supplied video composable (which lives in `:feature:feed:impl` and can call into the screen's `FeedVideoPlayerCoordinator` directly without crossing the `:designsystem` boundary). This avoids round-tripping per-card transient gestures through MVI.

#### Scenario: Default slot is no-op

- **WHEN** `PostCard` is invoked without supplying `videoEmbedSlot` and the post's `embed` is `EmbedUi.Video`
- **THEN** the embed slot region renders empty (no crash, no `Unsupported` chip, no fallback)

#### Scenario: Host-supplied slot renders the video composable

- **WHEN** `PostCard` is invoked with `videoEmbedSlot = { video -> PostCardVideoEmbed(video, post, coordinator) }` and the post's `embed` is `EmbedUi.Video`
- **THEN** `PostCardVideoEmbed` is invoked with the `EmbedUi.Video` instance; PostCard does NOT also render its own placeholder for the video region

#### Scenario: `:designsystem` does not depend on `:feature:feed:impl`

- **WHEN** `:designsystem`'s `build.gradle.kts` is inspected
- **THEN** there SHALL be no `implementation(project(":feature:feed:impl"))` (or any other feature module); the slot pattern keeps the dependency direction `:feature:feed:impl → :designsystem` and never the reverse

#### Scenario: `:designsystem` does not import FeedEvent

- **WHEN** the `:designsystem` source tree is searched for `import net.kikin.nubecita.feature.feed.impl.FeedEvent` (or any `feature.*` event type)
- **THEN** there SHALL be no match

### Requirement: `PostCardQuotedPost` renders a Bluesky `app.bsky.embed.record#viewRecord` at near-parent density

`PostCardQuotedPost` MUST be a public composable in `:designsystem` accepting a `QuotedPostUi` parameter, an optional `quotedVideoEmbedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? = null` parameter, and a `Modifier`. It MUST render the quoted post inside a `Surface` with `color = surfaceContainerLow` and `shape = RoundedCornerShape(12.dp)`.

The composable's layout, top to bottom:

- **Author row.** A `Row` with 8 dp horizontal arrangement: a 32 dp `NubecitaAvatar` (smaller than the parent post's 40 dp), then a single non-wrapping `Text` line carrying the display name + handle + a relative-time stamp (e.g. "Acyn @acyn.bsky.social · 4h"). Display name uses `labelLarge`; handle and timestamp use `bodySmall` + `onSurfaceVariant`. The line MUST NOT wrap; ellipsis truncates the handle when space is tight (display name + timestamp keep priority).
- **Body text.** The full `text` from `QuotedPostUi`, rendered in `bodyMedium`. NO `maxLines` cap — the quoted post is meant to be read in full per the official client's behavior.
- **Inner embed.** Dispatched via the internal `QuotedEmbedSlot` composable per the separate requirement below.

`PostCardQuotedPost` MUST NOT render an action row (no reply / repost / like / share affordances). It MUST NOT have a `Modifier.clickable` — v1 ships without a tap target. The `PostCallbacks.onQuotedPostTap` callback is intentionally NOT introduced in this change; tap-to-open lands together with the in-app post-detail destination in a separate bd issue.

#### Scenario: Avatar size is 32 dp

- **WHEN** `PostCardQuotedPost` is rendered with a non-null `quotedPost.author.avatarUrl`
- **THEN** the rendered avatar's size is exactly `32.dp` (not the 40 dp the parent `PostCard` author row uses)

#### Scenario: Body text has no maxLines cap

- **WHEN** `PostCardQuotedPost` is rendered with `quotedPost.text` of 800 characters
- **THEN** the body `Text` composable renders without `maxLines` truncation; ellipsis MUST NOT appear in the rendered output

#### Scenario: No action row is rendered

- **WHEN** the rendered `PostCardQuotedPost` composable's subtree is inspected
- **THEN** there SHALL be no `IconButton` or `PostStat` instance for reply / repost / like / share inside the quoted card's `Surface`

#### Scenario: Card surface is not clickable in v1

- **WHEN** the rendered `PostCardQuotedPost` composable's `Surface` modifier chain is inspected
- **THEN** there SHALL be no `Modifier.clickable` applied to the `Surface` — taps on the quoted card are deliberate no-ops in v1

### Requirement: `QuotedEmbedSlot` dispatches the quoted post's inner embed exhaustively over `QuotedEmbedUi`

`PostCardQuotedPost` MUST contain (or compose) an internal embed-dispatch slot that uses an exhaustive `when (quotedPost.embed)` expression over the `QuotedEmbedUi` sealed interface:

- `QuotedEmbedUi.Empty` → no embed rendered
- `QuotedEmbedUi.Images` → `PostCardImageEmbed(items = embed.items)` — same leaf composable the parent `EmbedSlot` uses
- `QuotedEmbedUi.External` → `PostCardExternalEmbed(uri, domain, title, description, thumbUrl, onTap = null)` — same leaf composable the parent `EmbedSlot` uses; passing a `null` `onTap` causes `PostCardExternalEmbed` to omit `Modifier.clickable` entirely so the inner card has no ripple, no tap target, no clickable semantics. v1 ships with no tap target on the quoted card; the outer card surface will own a tap target in a follow-up bd issue paired with the post-detail destination.
- `QuotedEmbedUi.Video` → `quotedVideoEmbedSlot?.invoke(embed)` — when the host did not supply a slot, no video is rendered (default-null behavior preserves `:designsystem`'s media-free preview / screenshot tests)
- `QuotedEmbedUi.QuotedThreadChip` → small surface-tile placeholder with copy "View thread" in `bodySmall` + `onSurfaceVariant` — same surface treatment as `PostCardRecordUnavailable`, different copy
- `QuotedEmbedUi.Unsupported` → `PostCardUnsupportedEmbed(typeUri = embed.typeUri)` — reuses the existing friendly-name mapping leaf composable

The `QuotedEmbedUi` dispatch MUST be exhaustive at compile time. Because `QuotedEmbedUi` deliberately excludes a `Record` variant (per the data-models spec), this dispatch site CAN NOT have an arm for nested record embeds — the recursion bound is structural, not runtime.

#### Scenario: Compile-time dispatch is exhaustive

- **WHEN** the source for `PostCardQuotedPost`'s `when (embed)` expression is inspected
- **THEN** every variant of `QuotedEmbedUi` is covered; there is no `else ->` branch (the sealed interface makes `else` redundant)

#### Scenario: Quoted thread chip renders "View thread"

- **WHEN** `PostCardQuotedPost` renders a `QuotedPostUi` whose `embed is QuotedEmbedUi.QuotedThreadChip`
- **THEN** a small surface-tile is rendered carrying `Text("View thread")` in `bodySmall` + `onSurfaceVariant`; no further descent into a doubly-quoted post occurs

#### Scenario: Default null video slot renders nothing

- **WHEN** `PostCardQuotedPost` is invoked without a `quotedVideoEmbedSlot` and the quoted post's `embed is QuotedEmbedUi.Video`
- **THEN** the inner embed region renders empty — no crash, no `Unsupported` chip, no fallback poster

### Requirement: `PostCardRecordUnavailable` renders the single-stub unavailable chip

`PostCardRecordUnavailable` MUST be a public composable in `:designsystem` accepting an `EmbedUi.RecordUnavailable.Reason` parameter and a `Modifier`. It MUST render a small chip with the same surface treatment as `PostCardUnsupportedEmbed` (`surfaceContainerHighest`, `RoundedCornerShape(8.dp)`, padded label) carrying the single piece of copy "Quoted post unavailable" regardless of `Reason`. The `Reason` argument is accepted for forward compatibility (per-variant copy upgrade) and for telemetry / debug consumers; v1 MUST NOT use it to vary the rendered copy or icon.

#### Scenario: All four Reason values render identical copy

- **WHEN** `PostCardRecordUnavailable` is rendered four times with `Reason.NotFound`, `Reason.Blocked`, `Reason.Detached`, `Reason.Unknown` respectively
- **THEN** the rendered `Text` content is the string "Quoted post unavailable" in all four cases; the only deliberate variation is the input parameter, not the output

### Requirement: `PostCard` exposes a `quotedVideoEmbedSlot` lambda for host-supplied quoted-video render

`PostCard` MUST accept an optional `quotedVideoEmbedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? = null` parameter, paralleling the existing `videoEmbedSlot` parameter. When `EmbedSlot` dispatches and the post's embed is `EmbedUi.Record` whose `quotedPost.embed is QuotedEmbedUi.Video`, the slot lambda MUST be invoked with the quoted-video data; a default `null` slot causes no quoted video to render (preserving `:designsystem`'s media-free preview path). `:designsystem` MUST NOT import or depend on any feature module to render the quoted video; the slot's body lives in `:feature:feed:impl` and is supplied by `FeedScreen` when it constructs PostCard.

`PostCard` MUST forward the `quotedVideoEmbedSlot` parameter to `PostCardQuotedPost` when dispatching `EmbedUi.Record`.

#### Scenario: Default slot is no-op for quoted videos

- **WHEN** `PostCard` is invoked without supplying `quotedVideoEmbedSlot` and the post's `embed is EmbedUi.Record` whose inner `embed is QuotedEmbedUi.Video`
- **THEN** the quoted card renders the author row + body text but the inner embed region is empty (no crash, no `Unsupported` chip)

#### Scenario: Host-supplied slot renders the quoted-video composable

- **WHEN** `PostCard` is invoked with `quotedVideoEmbedSlot = { qVideo -> PostCardVideoEmbed(quotedVideo = qVideo, postId = quotedPost.uri, coordinator) }`
- **THEN** the slot is invoked with the `QuotedEmbedUi.Video` instance; PostCard does not also render its own placeholder for the quoted-video region

#### Scenario: `:designsystem` does not depend on `:feature:feed:impl` for quoted video rendering

- **WHEN** `:designsystem`'s `build.gradle.kts` is inspected after this change lands
- **THEN** there SHALL still be no `implementation(project(":feature:feed:impl"))` dependency — the slot pattern keeps `:feature:feed:impl → :designsystem` as the only edge

### Requirement: `PostCardRecordWithMediaEmbed` renders a Bluesky `app.bsky.embed.recordWithMedia#view` as media-above-quote composition

`PostCardRecordWithMediaEmbed` MUST be a public composable in `:designsystem` accepting:

- `record: EmbedUi.RecordOrUnavailable` — either a resolved `Record` (carrying a `QuotedPostUi`) or a `RecordUnavailable` (with a `Reason`).
- `media: EmbedUi.MediaEmbed` — exactly one of `Images`, `Video`, or `External`.
- `modifier: Modifier = Modifier`.
- `onExternalMediaTap: ((uri: String) -> Unit)? = null` — invoked when `media is External` and the user taps it. Default null causes `PostCardExternalEmbed` to omit `Modifier.clickable` for the same null-tap-omits-clickable contract that 6vq established. The host (`PostCard.EmbedSlot`) MUST pass `callbacks.onExternalEmbedTap` for parent-feed rendering — the External media is a real top-level link card on the post and SHOULD open in Custom Tabs the same way a top-level `EmbedUi.External` does.
- `videoEmbedSlot: (@Composable (EmbedUi.Video) -> Unit)? = null` — invoked when `media is Video`. Same shape as `PostCard.videoEmbedSlot`; the host passes through its own slot. Default null leaves the video region empty (preview / screenshot tests).
- `quotedVideoEmbedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? = null` — forwarded to `PostCardQuotedPost` when `record is Record` and the inner `quotedPost.embed is Video`. Default null leaves nested-quoted-video region empty.

Layout (top to bottom):

- **Media region.** Renders the media at its native treatment via the existing leaf composable for that variant. NO surrounding `Surface` — adjacency to the quoted card below provides the visual grouping (matches the official Bluesky Android client's layout).
- **8 dp `Spacer`** between media and quoted card.
- **Quoted card.** Renders the record at its native treatment via either `PostCardQuotedPost` (resolved) or `PostCardRecordUnavailable` (unavailable chip).

The composable MUST NOT wrap its content in a `Surface` or other bounding container. The two regions stack as siblings inside a `Column`, separated only by the `Spacer`.

The composable MUST NOT have its own `Modifier.clickable`. Same tap-target deferral as `PostCardQuotedPost` — tap-to-open-PostDetail lands in a follow-up bd issue paired with the post-detail destination so wiring lands once.

The internal media-side `when` MUST be exhaustive over `EmbedUi.MediaEmbed` (no `else` branch). The internal record-side `when` MUST be exhaustive over `EmbedUi.RecordOrUnavailable` (no `else` branch). Both are sealed; future variant additions to either marker would surface as compile errors.

#### Scenario: Resolved record + Images media renders the composition

- **WHEN** `PostCardRecordWithMediaEmbed` is rendered with `record = EmbedUi.Record(quotedPost)` and `media = EmbedUi.Images(items)` carrying two images
- **THEN** the rendered subtree contains exactly one `PostCardImageEmbed` (above) and exactly one `PostCardQuotedPost` (below), separated by an 8 dp `Spacer`. NO surrounding `Surface` is present.

#### Scenario: RecordUnavailable + External media renders the unavailable chip + the link card

- **WHEN** `PostCardRecordWithMediaEmbed` is rendered with `record = EmbedUi.RecordUnavailable(Reason.NotFound)` and `media = EmbedUi.External(...)` and `onExternalMediaTap = lambda`
- **THEN** the rendered subtree contains exactly one `PostCardExternalEmbed` (above, with `onTap = lambda` so it's tappable) and exactly one `PostCardRecordUnavailable` (below, rendering the "Quoted post unavailable" chip)

#### Scenario: Default null onExternalMediaTap is non-clickable

- **WHEN** `PostCardRecordWithMediaEmbed` is rendered with `media = EmbedUi.External(...)` and `onExternalMediaTap = null`
- **THEN** the inner `PostCardExternalEmbed` is rendered without `Modifier.clickable` (no ripple, no tap target) per the leaf composable's null-tap contract

#### Scenario: Default null videoEmbedSlot leaves the media region empty

- **WHEN** `PostCardRecordWithMediaEmbed` is rendered with `media = EmbedUi.Video(...)` and `videoEmbedSlot = null`
- **THEN** the media region renders nothing — no crash, no `Unsupported` chip, no fallback poster. The quoted card below still renders.

#### Scenario: Composable is not clickable in v1

- **WHEN** the rendered `PostCardRecordWithMediaEmbed` composable's root modifier chain is inspected
- **THEN** there SHALL be no `Modifier.clickable` on the root; tap-to-open is deferred to a follow-up bd issue
