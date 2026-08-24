# design-system Specification

## Purpose
The brand's Material 3 Expressive design system: theming, tokens, and the
shared components every feature renders through.

Owns `NubecitaTheme` as the single entry point for brand styling (color scheme,
typography, shape, motion, and the extended token set), the `AppTheme` rendering
identity the composition root passes it, and the canonical component library —
`PostCard` and its embeds, list groups, avatars, shimmer, and the Material
Symbols icon subset.
## Requirements
### Requirement: NubecitaTheme is the single entry point for brand styling

The app MUST expose a single `@Composable fun NubecitaTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit)` at `net.kikin.nubecita.designsystem.NubecitaTheme`. Feature code MUST wrap its UI content with `NubecitaTheme { ... }` and MUST NOT call `MaterialTheme` or `MaterialExpressiveTheme` directly — those are implementation details of this module.

The module MUST additionally expose `enum class AppTheme { Dynamic, Light, Dark }` and a `@Composable fun NubecitaTheme(appTheme: AppTheme, content: @Composable () -> Unit)` overload that resolves an `AppTheme` to the `darkTheme` / `dynamicColor` pair and delegates to the two-argument overload. `AppTheme` is the theme-identity type the composition root passes; the resolution table is:

| `AppTheme` | `darkTheme` | `dynamicColor` |
|---|---|---|
| `Dynamic` | `isSystemInDarkTheme()` | `true` |
| `Light` | `false` | `false` |
| `Dark` | `true` | `false` |

`:designsystem` MUST NOT depend on `:core:preferences` — the persisted `ThemePreference` is a storage type, and mapping it to `AppTheme` is the composition root's job. The two-argument overload's contract is unchanged, so `@Preview` and screenshot-test call sites that pass `dynamicColor = false` continue to work untouched.

#### Scenario: Composition root wires the theme

- **WHEN** `MainActivity.onCreate` calls `setContent { ... }`
- **THEN** the outermost composable inside is `NubecitaTheme(appTheme = ...) { ... }` with the `AppTheme` derived from the stored theme preference, and all descendants read brand tokens via `MaterialTheme.*` without re-importing them.

#### Scenario: Dynamic color opt-out

- **WHEN** `NubecitaTheme(dynamicColor = false) { ... }` is composed on an Android 12+ device
- **THEN** the brand palette (Sky / Lagoon / Orchid / Neutral) is used instead of wallpaper-derived tones, and `MaterialTheme.colorScheme.primary` equals the brand Sky-40 (`#0061A6`) in light mode.

#### Scenario: Dynamic color default-on

- **WHEN** `NubecitaTheme { ... }` is composed without an explicit `dynamicColor` argument on an Android 12+ device — or equivalently `NubecitaTheme(appTheme = AppTheme.Dynamic) { ... }`
- **THEN** `MaterialTheme.colorScheme` is sourced from `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)` and brand colors are NOT visible.

#### Scenario: Dynamic color on pre-Android-12

- **WHEN** `NubecitaTheme { ... }` is composed on an Android 11 or earlier device, regardless of the `dynamicColor` argument
- **THEN** the brand palette is used (the dynamic color API isn't available) and `MaterialTheme.colorScheme.primary` equals the brand Sky-40.

#### Scenario: AppTheme.Dark forces the dark brand scheme

- **WHEN** `NubecitaTheme(appTheme = AppTheme.Dark) { ... }` is composed on an Android 12+ device whose OS is in light mode
- **THEN** the brand dark palette is used, no `dynamic*ColorScheme` call is made, and the result is identical to `NubecitaTheme(darkTheme = true, dynamicColor = false) { ... }`.

#### Scenario: AppTheme.Light forces the light brand scheme

- **WHEN** `NubecitaTheme(appTheme = AppTheme.Light) { ... }` is composed on a device whose OS is in dark mode
- **THEN** the brand light palette is used and the result is identical to `NubecitaTheme(darkTheme = false, dynamicColor = false) { ... }`.

#### Scenario: Contrast and motion handling is shared by both overloads

- **WHEN** any `AppTheme` is composed on a device with a high contrast level or with animators disabled
- **THEN** the high-contrast brand scheme and the reduced motion scheme are applied exactly as they are through the two-argument overload — the overload adds no branch of its own.

### Requirement: Every Material 3 color role is populated from the brand palette

The six `ColorScheme`s (light, light-medium-contrast, light-high-contrast, dark, dark-medium-contrast, dark-high-contrast) exposed by the module MUST populate every Material 3 color role from the Sky / Lagoon / Orchid / Neutral / NeutralVariant tonal palette defined in `openspec/references/design-system/colors_and_type.css`. No Material-default (e.g., the stock `Purple40`) color MUST remain reachable via `MaterialTheme.colorScheme.*`.

The five tonal palettes are generated from HCT hue and chroma coordinates, so every
stop is reproducible rather than hand-picked:

| Palette | Role | HCT hue | HCT chroma |
| --- | --- | --- | --- |
| Sky | primary | 255 | 72 |
| Lagoon | secondary | 215 | 40 |
| Orchid | tertiary | 318 | 45 |
| Neutral | surfaces | 255 | 5 |
| NeutralVariant | outlines | 250 | 9 |

The twelve **fixed** accent roles — `primaryFixed`, `primaryFixedDim`,
`onPrimaryFixed`, `onPrimaryFixedVariant` and their secondary and tertiary
equivalents — MUST also be populated from the brand ramps, at the Material 3
mapping: `*Fixed` = tone 90, `*FixedDim` = tone 80, `on*Fixed` = tone 10,
`on*FixedVariant` = tone 30. By definition these hold the same value in light and
dark.

Before this change none of the twelve was assigned, so
`lightColorScheme()` / `darkColorScheme()` supplied their defaults from
`ColorLightTokens` / `ColorDarkTokens` — the stock Material baseline palette. The
requirement above was therefore not met: `MaterialTheme.colorScheme.primaryFixed`
resolved to a baseline purple. No Material 3 component reads these roles today, so
nothing rendered incorrectly, but the value was reachable by any feature that
referenced it.

The error family is the one exception to hue generation. `Error40`, `Error50`,
`Error80` and `Error90` remain carried in `NubecitaPalette` — so every role is
still sourced from a single palette object — but they hold the Material 3 static
error colors and are NOT generated from the brand hues above. Error semantics must
stay recognisable across themes, and harmonising them toward the brand hue would
weaken that signal.

#### Scenario: Every role has a brand color

- **WHEN** any of the six `ColorScheme`s is instantiated
- **THEN** all of `primary`, `onPrimary`, `primaryContainer`, `onPrimaryContainer`, `secondary`, `onSecondary`, `secondaryContainer`, `onSecondaryContainer`, `tertiary`, `onTertiary`, `tertiaryContainer`, `onTertiaryContainer`, `background`, `onBackground`, `surface`, `onSurface`, `surfaceVariant`, `onSurfaceVariant`, `outline`, `outlineVariant`, `scrim`, `inverseSurface`, `inverseOnSurface`, `inversePrimary`, `surfaceDim`, `surfaceBright`, `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest` resolve to values derived from the brand tonal palette.

#### Scenario: No fixed accent role falls back to a Material baseline default

- **WHEN** any of the six `ColorScheme`s is instantiated
- **THEN** each of `primaryFixed`, `primaryFixedDim`, `onPrimaryFixed`, `onPrimaryFixedVariant`, `secondaryFixed`, `secondaryFixedDim`, `onSecondaryFixed`, `onSecondaryFixedVariant`, `tertiaryFixed`, `tertiaryFixedDim`, `onTertiaryFixed` and `onTertiaryFixedVariant` SHALL resolve to a value from the brand tonal palette, and SHALL NOT equal the corresponding `ColorLightTokens` / `ColorDarkTokens` baseline value.

#### Scenario: Error roles are populated from the static error family

- **WHEN** any of the six `ColorScheme`s is instantiated
- **THEN** `error`, `onError`, `errorContainer` and `onErrorContainer` resolve to the Material 3 static error colors carried in `NubecitaPalette`, NOT to values generated from the brand hues, and NOT to any stock Material default left unassigned.

#### Scenario: Light-mode primary matches the CSS token

- **WHEN** `NubecitaTheme(darkTheme = false, dynamicColor = false)` is composed
- **THEN** `MaterialTheme.colorScheme.primary` equals `Color(0xFF0061A6)` — the CSS `--sky-40`.

#### Scenario: Dark-mode primary matches the CSS token

- **WHEN** `NubecitaTheme(darkTheme = true, dynamicColor = false)` is composed
- **THEN** `MaterialTheme.colorScheme.primary` equals `Color(0xFFA0C9FF)` — the CSS `--sky-80`, matching the dark-mode role mapping in `colors_and_type.css`.

#### Scenario: Brand hue is no longer the Bluesky accent

- **WHEN** the light `ColorScheme` is instantiated
- **THEN** `MaterialTheme.colorScheme.primary` SHALL NOT equal `Color(0xFF0A7AFF)`, which is reserved for the fixed identity surfaces enumerated in the `LauncherBlue` requirement below.

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

### Requirement: Iconography uses Material Symbols Rounded via `NubecitaIcon`

The system SHALL render every in-app icon through the `NubecitaIcon` composable in `:designsystem` (`net.kikin.nubecita.designsystem.icon.NubecitaIcon`). Glyph identity is supplied by the typed `NubecitaIconName` enum; visual variants are controlled by the `filled: Boolean` (FILL axis), `weight: Int = 400` (wght), `grade: Int = 0` (GRAD), and `opticalSize: Dp = 24.dp` (opsz) parameters. The font asset MUST be the subsetted `MaterialSymbolsRounded[FILL,GRAD,opsz,wght].ttf` at `R.font.material_symbols_rounded`, produced by `scripts/update_material_symbols.sh` from the codepoints declared in `NubecitaIconName`. Direct use of `androidx.compose.material.icons.*` (the deprecated `material-icons-extended` library) is forbidden in production source.

#### Scenario: Active/inactive state collapses to the FILL axis

- **GIVEN** a navigation tab with active and inactive states
- **WHEN** the tab renders its icon
- **THEN** a single `NubecitaIcon(name = NubecitaIconName.X, filled = isActive, …)` site SHALL render both states (no `if (active) Filled else Outlined` ternary against two different glyph identities)

#### Scenario: Directional icons opt into RTL mirroring

- **GIVEN** a directional icon (back arrow, reply chevron, etc.)
- **WHEN** the icon renders in an RTL locale
- **THEN** the call site SHALL apply `Modifier.mirror()` from `:designsystem`'s icon package; the modifier is a no-op in LTR

#### Scenario: Adding a new glyph requires an enum entry

- **WHEN** a feature requires a Material Symbols glyph not currently in `NubecitaIconName`
- **THEN** the contributor SHALL add a new enum entry (one line: `NewName("\uXXXX"),` with the upstream codepoint), then re-run `./scripts/update_material_symbols.sh` so the shipped font picks up the glyph; no inline-codepoint usage of `NubecitaIcon` is supported

#### Scenario: Material Icons library is not a runtime dependency

- **WHEN** the project's module `build.gradle.kts` files are inspected
- **THEN** none SHALL declare `androidx.compose.material:material-icons-extended` (or any artifact under `androidx.compose.material.icons.*`); the version-catalog entry MUST also be absent

### Requirement: `:designsystem` provides a `NubecitaLogomark` composable

`:designsystem/component/NubecitaLogo.kt` SHALL expose a public `@Composable fun NubecitaLogomark(modifier: Modifier = Modifier, tint: Color = Color.Unspecified)` that renders the brand cloud mark with bow (no wordmark), backed by `LogoImageVector` — a Compose `ImageVector` port of the mark, held in `:designsystem/component/LogoImageVector.kt`. Its intrinsic size SHALL be 72dp × 72dp.

The mark SHALL be multi-color by default: a white cloud body, a pink bow
(`#F7AAC9` / `#E36DA0`), and two identity-blue stroke accents sourced from
`NubecitaPalette.LauncherBlue`.

The `tint` parameter SHALL be honoured only when specified: the composable SHALL
apply `ColorFilter.tint(tint)` when `tint.isSpecified` and SHALL apply no color
filter otherwise. `Color.Unspecified` therefore means "render multi-color", which
is legible only against a contrasting or branded background. Against a
low-contrast surface — notably the near-white light theme background — a caller
MUST pass an explicit `tint`, or the white cloud body renders invisible.

Call sites choose the tint by what the mark *means* at that site:

| Site | Tint | Why |
| --- | --- | --- |
| In-app splash placeholder | `NubecitaPalette.LauncherBlue` | Brand identity; must match the system splash background it hands off from |
| In-app chrome (e.g. onboarding) | `MaterialTheme.colorScheme.primary` | Follows the active theme, including wallpaper-derived color under `AppTheme.Dynamic` |
| Branded/contrasting background | omit (multi-color) | The full mark is legible there |

No call site may pass a tonal-ramp stop such as `Sky50` to express either meaning:
after this change `Sky50` is an ordinary ramp stop whose value follows the ramp.

This requirement replaces a stale description. The previous text specified a
default tint of `MaterialTheme.colorScheme.primary`, a backing
`nubecita_logomark.xml` vector drawable, and a single-color silhouette with every
path at `#FFFFFFFF`. None of the three matches the implementation, and no
`nubecita_logomark.xml` exists in the repository.

The composable SHALL set `contentDescription = stringResource(R.string.logomark_content_description)` (value: `"Nubecita"`) so screen readers announce the brand name when the mark is used as the sole content of a tappable container.

The intrinsic aspect of the underlying vector SHALL be 1:1 (square). Callers control absolute size via the `modifier` parameter (`Modifier.size(...)` or layout-driven sizing).

#### Scenario: Logomark renders with default tint under static palette

- **WHEN** `NubecitaTheme(dynamicColor = false) { NubecitaLogomark(modifier = Modifier.size(96.dp)) }` is composed
- **THEN** a 96dp × 96dp mark SHALL render with no `ColorFilter` applied — white cloud body, pink bow, and `LauncherBlue` stroke accents — because the default `tint` is `Color.Unspecified`, NOT a theme-derived color.

#### Scenario: Logomark accepts a custom tint

- **WHEN** `NubecitaLogomark(tint = Color.White)` is composed inside `NubecitaTheme`
- **THEN** the whole mark SHALL collapse to pure white regardless of the active palette

#### Scenario: In-app chrome tints the mark to the active accent

- **WHEN** `NubecitaLogomark(tint = MaterialTheme.colorScheme.primary)` is composed under `NubecitaTheme(dynamicColor = false)` in light mode
- **THEN** the mark SHALL collapse to brand Sky-40 (`#0061A6`), remaining legible against the near-white light surface where the untinted multi-color rendering would not be.

#### Scenario: Logomark exposes its accessible label

- **WHEN** TalkBack focuses on a `NubecitaLogomark` composable
- **THEN** TalkBack SHALL announce `"Nubecita"` (from `R.string.logomark_content_description`)

### Requirement: Logomark content-description string

`:designsystem/src/main/res/values/strings.xml` SHALL define a string resource used as the `contentDescription` for the brand-mark composable:

- `<string name="logomark_content_description">Nubecita</string>`

The string SHALL be `translatable="true"` (default). When the app gains localized resources for additional locales, the brand name MAY be transliterated per the conventions of that locale.

#### Scenario: String resolves to the brand name in the default locale

- **WHEN** `stringResource(R.string.logomark_content_description)` is read inside a Composable on a device set to the default locale
- **THEN** the call SHALL return `"Nubecita"`

### Requirement: `NubecitaIconName` exposes glyphs required by the notifications surface

`NubecitaIconName` SHALL include entries for the following Material Symbols glyphs:

- `AlternateEmail` (codepoint ``) — the `@` glyph
- `ExpandMore` (codepoint ``) — chevron-down
- `FormatQuote` (codepoint ``) — curly double-quote
- `Verified` (codepoint ``) — verified-badge mark

The existing `Notifications` entry's codepoint SHALL be corrected from `` (`notifications_none`) to `` (`notifications`) so the variable font's FILL axis renders the activity dot on FILL=1.

#### Scenario: New icons render via NubecitaIcon

- **WHEN** any of the new icon names is passed to `NubecitaIcon(name = …)`
- **THEN** the icon SHALL render correctly in both `filled = true` and `filled = false` states using the shipped subset font

#### Scenario: Notifications icon shows the activity dot when filled

- **WHEN** `NubecitaIcon(name = NubecitaIconName.Notifications, filled = true)` is rendered
- **THEN** the rendered glyph SHALL be the canonical filled bell with the activity dot (codepoint `` with FILL=1)

### Requirement: Material Symbols subset font is regenerated after adding new icons

After adding entries to `NubecitaIconName`, the `./scripts/update_material_symbols.sh` script SHALL be re-run so the subset font under `designsystem/src/main/res/font/` includes the new glyphs. The committed font file SHALL include all codepoints referenced by `NubecitaIconName`.

#### Scenario: Unit test guards codepoint validity

- **WHEN** `./gradlew :designsystem:testDebugUnitTest` runs
- **THEN** `NubecitaIconNameTest.every_codepoint_isASingleScalar` SHALL pass for every entry, confirming each codepoint is a single Unicode scalar value

### Requirement: `NotificationReasonIcon` composable maps `NotificationReason` to icon + tint

`:designsystem` SHALL expose a `NotificationReasonIcon(reason: NotificationReason, modifier: Modifier = Modifier)` composable that renders the correct glyph + tint pair for each reason. The mapping SHALL be:

| Reason | Icon | Tint |
|---|---|---|
| `Like`, `LikeViaRepost` | `Favorite` (filled) | extended `likeAccent` token (or `colorScheme.error` fallback) |
| `Repost`, `RepostViaRepost` | `Repeat` | extended `repostAccent` token (or `colorScheme.tertiary` fallback) |
| `Follow`, `ContactMatch`, `StarterpackJoined` | `PersonAdd` | `colorScheme.primary` |
| `Reply` | `Reply` | `colorScheme.onSurfaceVariant` |
| `Mention` | `AlternateEmail` | `colorScheme.onSurfaceVariant` |
| `Quote` | `FormatQuote` | `colorScheme.onSurfaceVariant` |
| `Verified` | `Verified` (filled) | `colorScheme.primary` |
| `Unverified` | `Verified` (unfilled) | `colorScheme.onSurfaceVariant` |
| `SubscribedPost` | `Article` | `colorScheme.onSurfaceVariant` |
| `Unknown` | `Notifications` (unfilled) | `colorScheme.onSurfaceVariant` |

The composable SHALL be exhaustive over `NotificationReason` so adding a new enum value SHALL produce a compile error in `:designsystem` until the mapping is updated.

#### Scenario: Like reason renders the heart with like-accent tint

- **WHEN** `NotificationReasonIcon(reason = NotificationReason.Like)` is rendered
- **THEN** the icon SHALL be the filled `Favorite` glyph tinted with the `likeAccent` extended token

#### Scenario: Adding a new reason fails compilation until mapped

- **WHEN** a new value is added to `NotificationReason` and `NotificationReasonIcon` is rebuilt without an updated mapping
- **THEN** the Kotlin compiler SHALL flag a non-exhaustive `when` expression in `NotificationReasonIcon`'s implementation

### Requirement: `NotificationReasonIcon` ships `@Preview` and screenshot tests

`:designsystem` SHALL include a `@Preview`-annotated showcase composable rendering `NotificationReasonIcon` for every `NotificationReason` value, plus a corresponding `@PreviewTest`. Baselines SHALL be committed under `designsystem/src/screenshotTestDebug/reference/`.

#### Scenario: Showcase preview renders all reasons

- **WHEN** the design-system screenshot test job runs
- **THEN** the `NotificationReasonIcon` showcase SHALL render at least one row per `NotificationReason` value and match the committed baseline

### Requirement: `PostCard` accepts `connectAbove` / `connectBelow` parameters

`PostCard` SHALL accept two new `Boolean` parameters: `connectAbove: Boolean = false` and `connectBelow: Boolean = false`, defaulted to `false`. When either flag is `true`, `PostCard`'s root `Modifier` chain SHALL apply `Modifier.threadConnector(connectAbove, connectBelow, color = MaterialTheme.colorScheme.outlineVariant)` from `:designsystem/component/ThreadConnector.kt`. When both flags are `false` (the default), no `threadConnector` modifier SHALL be applied.

This unblocks `nubecita-m28.2` Section A's "PostCard integration" sub-scope which was deferred from PR #77's primitives-only landing.

#### Scenario: PostCard with both flags false is unchanged

- **WHEN** `PostCard(post = ..., connectAbove = false, connectBelow = false)` is composed
- **THEN** the rendered output is pixel-identical to the pre-change `PostCard(post = ...)` — no threadConnector applied

#### Scenario: PostCard with connectAbove + connectBelow draws full connector

- **WHEN** `PostCard(post = ..., connectAbove = true, connectBelow = true)` is composed
- **THEN** the rendered output applies `Modifier.threadConnector(connectAbove = true, connectBelow = true, color = MaterialTheme.colorScheme.outlineVariant)` to the post's outer container, drawing connector lines above and below the avatar

### Requirement: `:designsystem` provides a `ThreadCluster` composable

`:designsystem/component/ThreadCluster.kt` SHALL expose a public `@Composable fun ThreadCluster(...)` that renders a feed-level reply cluster: root post on top, optional `ThreadFold` between root and parent, parent post, leaf post — joined by avatar-gutter connector lines.

The signature SHALL be (parameter order per the project's Compose convention used by `PostCard` and `ThreadFold` — required params first, then `modifier`, then other defaulted params, then trailing lambdas):

```kotlin
@Composable
fun ThreadCluster(
    root: PostUi,
    parent: PostUi,
    leaf: PostUi,
    modifier: Modifier = Modifier,
    callbacks: PostCallbacks = PostCallbacks.None,
    hasEllipsis: Boolean = false,
    leafVideoEmbedSlot: (@Composable (EmbedUi.Video) -> Unit)? = null,
    leafQuotedVideoEmbedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? = null,
    onFoldTap: () -> Unit = {},
)
```

Internal layout SHALL be a `Column`:

| Position | Composable | `connectAbove` | `connectBelow` | `videoEmbedSlot` |
|---|---|---|---|---|
| top | `PostCard(root)` | false | true | null |
| (when `hasEllipsis`) | `ThreadFold(onClick = onFoldTap)` | — | — | — |
| middle | `PostCard(parent)` | true | true | null |
| bottom | `PostCard(leaf)` | true | false | `leafVideoEmbedSlot` |

`PostCallbacks` SHALL be passed through to all three `PostCard`s. Tap targets on root, parent, leaf, and fold MAY all be no-ops in v1 (post-detail navigation lands later); the `onFoldTap` parameter exists so callers can wire it when post-detail is available without an API change.

#### Scenario: ThreadCluster without ellipsis renders three PostCards in a Column

- **WHEN** `ThreadCluster(root, parent, leaf, callbacks, hasEllipsis = false)` is composed
- **THEN** the rendered output is a `Column` containing `PostCard(root, connectBelow = true)` + `PostCard(parent, connectAbove = true, connectBelow = true)` + `PostCard(leaf, connectAbove = true)`
- **AND** no `ThreadFold` is rendered

#### Scenario: ThreadCluster with ellipsis inserts a ThreadFold between root and parent

- **WHEN** `ThreadCluster(root, parent, leaf, callbacks, hasEllipsis = true)` is composed
- **THEN** the rendered output is a `Column` containing `PostCard(root, connectBelow = true)` + `ThreadFold(onClick = onFoldTap)` + `PostCard(parent, connectAbove = true, connectBelow = true)` + `PostCard(leaf, connectAbove = true)`

#### Scenario: ThreadCluster collapses the parent slot when parent equals root

- **WHEN** `ThreadCluster(root, parent, leaf, callbacks, hasEllipsis = false)` is composed AND `parent.id == root.id` (i.e., the leaf is a direct reply to the root post — common for self-threads or any direct reply)
- **THEN** the rendered output is a `Column` containing `PostCard(root, connectBelow = true)` + `PostCard(leaf, connectAbove = true)` only — the `parent` slot is NOT rendered (rendering it would visually duplicate the root post)
- **AND** no `ThreadFold` is rendered

#### Scenario: ThreadCluster passes leaf-only video slot

- **WHEN** the caller supplies a non-null `leafVideoEmbedSlot`
- **THEN** the leaf `PostCard` receives the slot
- **AND** root + parent `PostCard` receive `videoEmbedSlot = null` (their video embeds, if any, render via the static-poster fallback in PostCard)

### Requirement: Fraunces variable font with `SOFT` axis is bundled and exposed via Typography

`:designsystem` SHALL ship the Fraunces variable font as a bundled font asset (`:designsystem/src/main/res/font/fraunces.ttf` or equivalent) and SHALL expose at least one Typography style that uses the variable font with the `SOFT` variable axis configurable. The display-name style used by the profile hero MUST set `SOFT = 70`. The font MUST be loaded via the standard Compose `FontFamily` API — no reflection, no manual `Typeface` construction. The font asset MUST NOT be downloaded at runtime; it MUST be embedded in the APK at build time.

#### Scenario: Display style uses Fraunces with `SOFT = 70`

- **WHEN** any consumer renders a `Text` with the profile-display-name style sourced from `:designsystem`'s Typography
- **THEN** the resolved `TextStyle` carries `FontFamily(Font(R.font.fraunces, FontVariation.Settings(FontVariation.Setting("SOFT", 70f), …)))`; the rendered glyph metrics differ measurably from the default-`SOFT` Fraunces rendering when verified on a real device

#### Scenario: Fraunces is bundled in the APK

- **WHEN** the debug APK is built and inspected (`./gradlew :app:assembleDebug` followed by `aapt2 dump resources`)
- **THEN** the Fraunces font file is present under `res/font/`; no network request is made to fetch the font during APK install or at first render

### Requirement: JetBrains Mono variable font is bundled and exposed via Typography

`:designsystem` SHALL ship JetBrains Mono as a bundled variable font asset and SHALL expose a monospace Typography style suitable for rendering user handles at 13 sp. The font MUST be loaded via the standard Compose `FontFamily` API and MUST be embedded in the APK at build time (no runtime download).

#### Scenario: Handle style uses JetBrains Mono at 13 sp

- **WHEN** any consumer renders a `Text` with the handle style sourced from `:designsystem`'s Typography
- **THEN** the resolved `TextStyle` carries `FontFamily(Font(R.font.jetbrains_mono, …))` and `fontSize = 13.sp`

### Requirement: `BoldHeroGradient` composable owns Palette extraction and avatarHue fallback

`:designsystem` SHALL ship a `BoldHeroGradient(banner: String?, avatarHue: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit)` composable (or equivalent surface API). The `banner` parameter is a nullable URL string — matching what `app.bsky.actor.defs#profileViewDetailed.banner` returns from the atproto SDK and what the existing `NubecitaAsyncImage(model: Any?)` Coil wrapper accepts. The composable MAY internally wrap the string into Coil's `ImageRequest.Builder` and is not required to expose a richer model parameter unless a future consumer surfaces. The composable MUST decode the `banner` image off the main thread via Coil's image-loader pipeline (no direct `BitmapFactory.decodeStream` in the design system or any consumer), pass the decoded bitmap to `androidx.palette.graphics.Palette.from(bitmap).generate()` on a background coroutine context, cache the resulting `Palette` keyed on the banner URL or blob `cid`, and render a 2-stop gradient derived from the cached palette. When `banner == null`, the gradient MUST be derived deterministically from `avatarHue` (an integer hue in `0..360`). The feature module that consumes `BoldHeroGradient` MUST NOT import `androidx.palette.*` or `androidx.compose.ui.graphics.Brush` for gradient construction — both are encapsulated by this composable.

#### Scenario: Palette extraction runs off the main thread

- **WHEN** `BoldHeroGradient` is composed with a non-null `banner` URL whose image has not been previously palette-extracted
- **THEN** the Palette extraction runs on `Dispatchers.Default` (or equivalent off-main dispatcher); the main thread is not blocked during decode + extraction; the composable initially renders the `avatarHue`-derived fallback gradient and swaps to the palette-derived gradient when extraction completes

#### Scenario: Palette result is cached per banner

- **WHEN** `BoldHeroGradient` is composed twice in succession with the same `banner` URL (e.g., navigating away from a profile and back)
- **THEN** the second composition retrieves the cached `Palette` synchronously on first composition; no re-decode of the banner bitmap occurs; the gradient renders without a fallback flicker

#### Scenario: Null banner uses avatarHue fallback deterministically

- **WHEN** `BoldHeroGradient` is composed with `banner = null` and `avatarHue = 217`
- **THEN** the rendered gradient is derived deterministically from `avatarHue = 217`; the same `avatarHue` always produces the same gradient; no `Palette` call is made

#### Scenario: Minimum-contrast adjustment for very-light banners

- **WHEN** `BoldHeroGradient` is composed with a banner whose extracted palette returns swatches with luminance above the contrast threshold needed for WCAG AA against white text overlays
- **THEN** the composable darkens the dominant stop of the gradient until contrast clears; the rendered gradient is dark enough to maintain AA contrast for white text overlays at the hero's name + handle positions

### Requirement: `ProfilePillTabs` composable wraps `PrimaryTabRow` with M3 Expressive pill chrome

`:designsystem` SHALL ship a `ProfilePillTabs(tabs: List<PillTab>, selectedTab: PillTab, onTabSelect: (PillTab) -> Unit, modifier: Modifier = Modifier)` composable that renders pill-shaped tabs. Each pill MUST be 36 dp tall. The active tab MUST have `MaterialTheme.colorScheme.primary` container fill and `onPrimary` content color; inactive tabs MUST have a transparent container and `onSurface` content color. Each tab's icon (when present) MUST render via `NubecitaIcon` with the `FILL` variable axis at 1 when active and 0 when inactive. The composable MUST be implemented as a thin wrapper around `androidx.compose.material3.PrimaryTabRow` (or equivalent M3 tab primitive) — no hand-rolled `Row` of buttons, no custom `Indicator` that draws outside the M3 vocabulary.

#### Scenario: Active tab renders with primary container fill and filled icon

- **WHEN** `ProfilePillTabs` is composed with `tabs = [Posts, Replies, Media]` and `selectedTab = Posts`
- **THEN** the rendered Posts pill has `Color = MaterialTheme.colorScheme.primary` as its container fill, the Posts icon renders with `FontVariation.Setting("FILL", 1f)` applied via `NubecitaIcon`, and the Replies + Media pills render with transparent container fills and `FILL = 0`

#### Scenario: Tab selection invokes onTabSelect with the new tab

- **WHEN** the user taps the Replies pill while Posts is currently active
- **THEN** `onTabSelect(Replies)` is invoked exactly once; the composable does NOT internally re-render with `selectedTab = Replies` until the parent passes the new `selectedTab` parameter (state hoisting is preserved)

### Requirement: PostCard renders multi-image embeds via `HorizontalMultiBrowseCarousel`

The `PostCard` composable in `:designsystem` SHALL conditionally swap its image-embed rendering branch based on the count of images in the embed. When `EmbedUi.Images.images.size > 1`, PostCard MUST delegate to `androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel` from the M3 carousel API. When `images.size == 1`, the existing single-image rendering path MUST be preserved byte-for-byte — the swap MUST NOT change the rendering of single-image posts in any consuming feature (feed, post detail, future profile / search / notifications surfaces).

The carousel MUST use M3's default `preferredItemWidth` token (do NOT attempt to clone the single-image embed's `fillMaxWidth() + heightIn(max = EMBED_HEIGHT)` sizing — single-image and carousel are not equivalent surfaces, and asking the carousel to "match the single-image preferred width" produces a sizing rule with no concrete target). Carousel slide aspect ratios MUST be per-slide (the carousel's default behavior); mixed-aspect-ratio posts (portrait + landscape in the same embed) MUST NOT be normalized via letterboxing.

The swap MUST live inside PostCard's existing image-embed branch — no new public composable, no new public API. PostCard's caller-facing signature MAY be extended with **additive, backwards-compatible** parameters (e.g. `onImageClick: (imageIndex: Int) -> Unit = {}` with a default no-op so existing call sites compile unchanged), but MUST NOT remove or change the meaning of any existing parameter. The post-detail feature requires the per-image-index click callback to dispatch its `NavigateToMediaViewer(imageIndex)` effect; consumers that don't pass the callback (the feed, future profile / search surfaces) keep their current behavior — the default no-op makes the swap a no-op for them.

#### Scenario: Multi-image post renders the carousel

- **WHEN** `PostCard` is composed with an `EmbedUi.Images` value whose `images.size == 3`
- **THEN** the resulting layout contains a `HorizontalMultiBrowseCarousel` rendering three slides, each loaded via the existing Coil image pipeline, and the carousel's snap and spring behavior is the M3 default

#### Scenario: Single-image post path is unchanged

- **WHEN** `PostCard` is composed with an `EmbedUi.Images` value whose `images.size == 1`
- **THEN** the rendered output matches the pre-change single-image PostCard byte-for-byte at the screenshot level — no carousel container, no slide chrome, no preferred-item-width sizing logic

#### Scenario: Existing call sites compile unchanged

- **WHEN** any consumer that did NOT pass `onImageClick` (FeedScreen, future profile / search surfaces) is recompiled against the updated PostCard
- **THEN** the call site compiles without modification — the new parameter has a default no-op value, and no behavioral change is observable in single-image OR multi-image posts in those surfaces (multi-image posts get the carousel rendering but tapping a slide is a no-op when no callback was passed)

#### Scenario: Post-detail wires the per-index callback

- **WHEN** `PostDetailScreen` composes the Focus PostCard
- **THEN** the call passes `onImageClick = { index -> /* dispatch NavigateToMediaViewer */ }`, and tapping a slide invokes the callback with the slide's index

#### Scenario: Mixed-aspect carousel does not letterbox

- **WHEN** `PostCard` renders a multi-image embed whose three images include one portrait and two landscape
- **THEN** the carousel slides size per-slide using the carousel's default sizing — no slide is letterboxed to match a tallest-or-widest target

### Requirement: Accent roles are fixed at the Material 3 tonal mapping with a contrast floor

The light and dark `ColorScheme`s MUST assign the primary, secondary and tertiary
accent roles at the tonal stops Material 3 specifies, and MUST NOT substitute a
lighter stop for visual vividness:

| Role | Light tonal stop | Dark tonal stop |
| --- | --- | --- |
| `primary` / `secondary` / `tertiary` | 40 | 80 |
| `on*` | 100 | 20 |
| `*Container` | 90 | 30 |
| `on*Container` | 10 | 90 |

Every foreground/background pair reachable through `MaterialTheme.colorScheme` in
any of the six schemes MUST meet WCAG 2.1 AA: at least **4.5:1** for text-bearing
pairs and at least **3:1** for `outline` against its surface.

This requirement exists because the palette it replaces assigned the light accents
to tonal stop 50, producing four failing pairs: `primary`/`onPrimary` at 4.01:1,
`secondary`/`onSecondary` at 3.90:1, `surface`/`primary` at 3.92:1 and
`surface`/`secondary` at 3.81:1 — all below the 4.5:1 minimum.

The accent-as-foreground-on-surface pairs are named explicitly below because they
are two of those four failures. A pair set covering only `on*` roles against their
own containers would miss them, and so would miss half the defect this requirement
exists to prevent.

Conformance MUST be enforced by a test that asserts the contrast property across
all six schemes, rather than by asserting individual hex values. A test that pins
hex literals cannot detect an accessibility regression introduced by a future
palette edit.

#### Scenario: Every on/container pair meets AA

- **WHEN** any of the six `ColorScheme`s is instantiated
- **THEN** each of the pairs `primary`/`onPrimary`, `primaryContainer`/`onPrimaryContainer`, `secondary`/`onSecondary`, `secondaryContainer`/`onSecondaryContainer`, `tertiary`/`onTertiary`, `tertiaryContainer`/`onTertiaryContainer`, `surface`/`onSurface`, `surface`/`onSurfaceVariant`, `inverseSurface`/`inverseOnSurface`, `inverseSurface`/`inversePrimary`, and every `surfaceContainer*`/`onSurface` pair SHALL have a WCAG 2.1 contrast ratio of at least 4.5:1.

#### Scenario: Accents are legible as foreground on the surface

- **WHEN** any of the six `ColorScheme`s is instantiated
- **THEN** each of `primary`, `secondary` and `tertiary` used as a foreground against `surface` SHALL have a WCAG 2.1 contrast ratio of at least 4.5:1, covering the accent-as-text and accent-as-icon usage that carries two of the four defects this requirement replaces.

#### Scenario: Outline meets the non-text threshold

- **WHEN** any of the six `ColorScheme`s is instantiated
- **THEN** `outline` against `surface` SHALL have a contrast ratio of at least 3:1.

#### Scenario: The contrast test fails on a regressed palette

- **WHEN** any accent role is reassigned to a tonal stop that breaks the floor above
- **THEN** `ColorSchemeTest` SHALL fail, naming the offending role pair and its measured ratio.

### Requirement: The dark surface ramp is deepened below the Material 3 canonical tone

The dark `ColorScheme` MUST place `surface` at HCT tone **3**, below the tone 6
that Material 3 specifies, and MUST widen the container steps so each depth tier
stays visually separable near black:

| Role | HCT tone |
| --- | --- |
| `surfaceContainerLowest` | 1 |
| `surface`, `surfaceDim` | 3 |
| `surfaceContainerLow` | 6 |
| `surfaceContainer` | 9 |
| `surfaceContainerHigh` | 14 |
| `surfaceContainerHighest` | 19 |
| `surfaceBright` | 26 |

This is a deliberate departure from the Material 3 specification, taken for
OLED power draw. It is NOT a spec-compliance fix: the palette it replaces already
sat at tone 5.9, which is Material 3's canonical tone 6. This requirement exists
so the deviation is not "corrected" back to tone 6 by a later reviewer.

The surface roles keep the depth-role contract recorded in
`docs/design-system/surface-roles.md` unchanged — only their tone values move.

#### Scenario: Dark surface sits below the canonical tone

- **WHEN** `NubecitaTheme(darkTheme = true, dynamicColor = false)` is composed
- **THEN** `MaterialTheme.colorScheme.surface` SHALL equal `Color(0xFF090B0E)`, and `surfaceContainerHighest` SHALL equal `Color(0xFF2C2E32)`.

#### Scenario: Depth tiers remain separable

- **WHEN** the dark `ColorScheme` is instantiated
- **THEN** each adjacent pair in the ramp `surface` → `surfaceContainerLow` → `surfaceContainer` → `surfaceContainerHigh` → `surfaceContainerHighest` SHALL differ by at least 3 HCT tones.

#### Scenario: Semantic accents survive the deeper surface

- **WHEN** the dark `ColorScheme` and `NubecitaSemanticColors` are both resolved
- **THEN** each of `likeAccent`, `repostAccent`, `supporterAccent`, `success` and `warning` SHALL have a contrast ratio of at least 4.5:1 against `surface`.

### Requirement: Adjacent accent affordances MUST pair one filled role with one container role

Two accent affordances rendered immediately adjacent MUST take their fills from
**different tiers**: exactly one from a filled accent role (`primary`, `secondary`
or `tertiary`) and exactly one from a container role (`primaryContainer`,
`secondaryContainer` or `tertiaryContainer`). Two filled roles together are
forbidden, and two container roles together are forbidden.

The cause is structural rather than incidental to this palette. Material 3 assigns
all three accent families the *same tonal stop* for a given role — filled roles sit
at tone 40 in light and 80 in dark, container roles at 90 and 30 — so any two roles
from the same tier differ **only in hue**. Every same-tier pairing measures ~1:1:

| Same tier — forbidden | Light | Dark |
| --- | --- | --- |
| `primary` / `secondary` | 1.00:1 | 1.01:1 |
| `primary` / `tertiary` | 1.00:1 | 1.00:1 |
| `secondary` / `tertiary` | 1.00:1 | 1.00:1 |
| `primaryContainer` / `secondaryContainer` | 1.00:1 | 1.01:1 |
| `primaryContainer` / `tertiaryContainer` | 1.00:1 | 1.00:1 |
| `secondaryContainer` / `tertiaryContainer` | 1.01:1 | 1.01:1 |

Hue-only separation disappears for a viewer with deuteranopia and degrades on a
cold-calibrated display, and no choice of brand hues can fix it — the tones are
identical by construction.

Cross-tier pairings all separate acceptably, so which family takes the filled role
is free:

| Cross tier — permitted | Light | Dark |
| --- | --- | --- |
| `primary` / `secondaryContainer` | 4.97:1 | 5.49:1 |
| `secondary` / `primaryContainer` | 5.01:1 | 5.47:1 |
| `tertiary` / `secondaryContainer` | 4.99:1 | 5.50:1 |

This rule is enforced by code review, following the precedent set for the reserved
`surfaceDim` / `surfaceBright` / `surfaceContainerLowest` tokens. No lint rule is
added.

#### Scenario: Two adjacent tonal buttons pair across tiers

- **WHEN** a screen renders two adjacent accent affordances, such as a Follow and a Message button
- **THEN** exactly one SHALL draw its fill from a filled accent role (`primary`, `secondary` or `tertiary`) with its matching `on*`, and exactly one from a `*Container` role with its matching `on*Container`. They SHALL NOT both be filled roles, and SHALL NOT both be container roles. Which family takes the filled role is a per-screen decision — `primary` + `secondaryContainer` and `secondary` + `primaryContainer` both satisfy this.

#### Scenario: Same-tier pairings are rejected in review

- **WHEN** a change places two filled accent roles adjacent, or two `*Container` roles adjacent
- **THEN** review SHALL reject it, citing the ~1:1 measured separation — the two roles share a tonal stop and differ only in hue.

#### Scenario: Adjacent fills are separable regardless of which pairing is chosen

- **WHEN** any two accent affordances are rendered adjacent to one another
- **THEN** their fill colors SHALL have a WCAG 2.1 contrast ratio of at least 3:1 against each other, which is the property the pairing rule exists to guarantee.

### Requirement: `tertiary` is reserved for auxiliary, non-critical surfaces

`tertiary` and `tertiaryContainer` MUST be used only for auxiliary elements —
badges, mention chips, auxiliary tags, and similar decoration. They MUST NOT carry
a screen's primary action, its selection state, or any control whose meaning
depends on being noticed.

The constraint is quantitative, not stylistic: in the dark scheme `tertiary`
carries HCT chroma 43 against `primary`'s 37, making it the most saturated of the
three accent families. Using it for load-bearing UI puts three competing accent
hues into a single post card.

#### Scenario: Tertiary carries decoration only

- **WHEN** a feature surface uses `tertiary` or `tertiaryContainer`
- **THEN** the element SHALL be auxiliary — a badge, mention chip, or tag — and the screen's primary action SHALL use `primary` or `primaryContainer`.

### Requirement: The brand identity blue is a fixed constant, separate from the primary ramp

`#0A7AFF` MUST be exposed as `NubecitaPalette.LauncherBlue`, a fixed brand
constant that is NOT a tonal-ramp stop and NOT derived from any `ColorScheme`.
Every surface that carries the brand identity — as opposed to the active accent —
MUST source its color from it:

- the logomark's stroke accents in `LogoImageVector`
- the in-app splash placeholder logomark, which must keep matching the system
  splash window background it hands off from
- the launcher icon and `windowSplashScreenBackground`, via the
  `brand_sky_blue` resource holding the same literal

Before this change these surfaces referenced `NubecitaPalette.Sky50`, which held
`#0A7AFF` only by coincidence of the old ramp. Regenerating the ramp moves tone 50
to a different blue, so the identity role MUST NOT remain attached to a ramp stop.
`LauncherBlue` follows the precedent of `VerifiedBlue`: a deliberately
theme-detached constant, unaffected by light/dark, contrast level, or dynamic color.

In-app chrome that merely displays the mark — such as the onboarding logomark —
MUST NOT use `LauncherBlue`, and SHALL take `NubecitaLogomark`'s default tint of
`MaterialTheme.colorScheme.primary` so it follows the active theme, including
wallpaper-derived color under `AppTheme.Dynamic`.

#### Scenario: Identity blue survives a palette regeneration

- **WHEN** the brand tonal palette is regenerated to new HCT coordinates
- **THEN** `NubecitaPalette.LauncherBlue` SHALL still equal `Color(0xFF0A7AFF)`, and SHALL equal the `brand_sky_blue` resource value used by the launcher icon and system splash.

#### Scenario: The identity blue is not a ramp stop

- **WHEN** the Sky tonal ramp is regenerated
- **THEN** no identity surface SHALL reference `NubecitaPalette.Sky50`, and `Sky50` SHALL carry no identity meaning — it is an ordinary stop whose value follows the ramp.

#### Scenario: In-app splash placeholder matches the system splash

- **WHEN** the system splash hands off to the `Splash` route
- **THEN** the placeholder logomark SHALL render in `LauncherBlue`, producing no visible color change across the handoff.

#### Scenario: Onboarding logomark follows the theme

- **WHEN** the onboarding screen is composed under `AppTheme.Dynamic` on an Android 12+ device
- **THEN** its logomark SHALL render in the wallpaper-derived `MaterialTheme.colorScheme.primary`, NOT in `LauncherBlue`.
