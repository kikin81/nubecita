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
