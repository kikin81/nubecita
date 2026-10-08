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

The app MUST expose `@Composable fun NubecitaTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit)` and overload `NubecitaTheme(appTheme: AppTheme, content: @Composable () -> Unit)` mapping `AppTheme` (`Dynamic`, `Light`, `Dark`). Feature code MUST wrap UI with `NubecitaTheme` and MUST NOT call `MaterialTheme` directly. `:designsystem` MUST NOT depend on `:core:preferences`.

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

The six `ColorScheme`s exposed by `:designsystem` MUST populate every Material 3 color role from the Sky / Lagoon / Orchid / Neutral / NeutralVariant palette, including the twelve fixed accent roles (`*Fixed`, `*FixedDim`, `on*Fixed`, `on*FixedVariant`). No baseline defaults may remain. The error family carries static Material 3 error tokens in `NubecitaPalette`.

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

`MaterialTheme.typography` MUST populate all 15 M3 type roles: display and headlineLarge use Fraunces (with `SOFT = 50f` on API 26+); headlineMedium, headlineSmall, title, body, and label roles use Roboto Flex; mono uses JetBrains Mono via `NubecitaTokens.typography.mono`. `bodyLarge` MUST use 17sp / 26sp line-height for enhanced reading.

#### Scenario: Display type uses Fraunces

- **WHEN** a composable reads `MaterialTheme.typography.displayLarge`
- **THEN** the returned `TextStyle.fontFamily` resolves to the Fraunces `FontFamily` (bundled as a Downloadable Google Font).

#### Scenario: Body-large uses the bumped size

- **WHEN** a composable reads `MaterialTheme.typography.bodyLarge`
- **THEN** `fontSize` is `17.sp` and `lineHeight` is `26.sp`.

#### Scenario: Mono type is available via extension

- **WHEN** a composable reads `MaterialTheme.typography` it does NOT find a mono role (M3 has none); a composable reading `MaterialTheme.extendedTypography.mono` resolves to a `TextStyle` with JetBrains Mono.

### Requirement: Shape roles map to the brand shape scale

`MaterialTheme.shapes` MUST populate M3's five shape roles (`extraSmall` to `extraLarge` with 4dp, 8dp, 12dp, 16dp, 28dp radii). Extended shapes `--shape-2xl` (36dp) and `--shape-full` (pill) MUST be exposed on `NubecitaTokens.extendedShape`. Button components default to pill shape via `ButtonDefaults`.

#### Scenario: Card shape uses the brand radius

- **WHEN** a composable reads `MaterialTheme.shapes.large`
- **THEN** it is a `RoundedCornerShape(16.dp)`.

#### Scenario: Pill shape is available

- **WHEN** a composable reads `MaterialTheme.extendedShape.pill`
- **THEN** it is `CircleShape` (a.k.a. pill / fully rounded).

### Requirement: Motion is governed by the brand spring system

`NubecitaMotion` MUST expose brand animation specs (`defaultSpatial`, `slowSpatial`, `bouncy`, `defaultEffects`, `defaultEmphasized`) and durations via `MaterialTheme.motion`. When system reduce-motion is active, `MaterialTheme.motion` MUST reactively return tween specs with `LinearEasing` and halved durations.

#### Scenario: Default spatial motion is a brand spring

- **WHEN** a composable reads `MaterialTheme.motion.defaultSpatial`
- **THEN** the returned `FiniteAnimationSpec<Float>` is a `spring()` with dampingRatio and stiffness tuned to the CSS `ease-spring-fast` overshoot (~300ms settle).

#### Scenario: Reduce-motion removes overshoot

- **WHEN** the device's "remove animations" accessibility setting is enabled and a composable reads `MaterialTheme.motion.defaultSpatial`
- **THEN** the returned `FiniteAnimationSpec<Float>` is a `tween` with `LinearEasing` and duration ≤ 150ms.

### Requirement: Extended tokens are reachable via MaterialTheme extension properties

:designsystem` MUST expose `@Composable @ReadOnlyComposable` extensions on `MaterialTheme`: `spacing` (`s0..s24`), `elevation` (`e1..e5`), `semanticColors` (success/warning), `motion`, `motionDurations`, `extendedShape` (36dp, pill, sheet), and `extendedTypography` (`mono`). These are provided via `LocalNubecitaTokens` at the `NubecitaTheme` root.

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

Roboto Flex and JetBrains Mono MUST be bundled as `.ttf` in `designsystem/src/main/res/font/`. Fraunces and Material Symbols Rounded MUST be declared as `GoogleFont` with certificate array in `font_certs.xml`. Every `GoogleFont` declaration MUST specify a fallback (`FontFamily.Serif` for Fraunces; system default for Material Symbols) for devices without Play Services.

#### Scenario: Body text renders instantly

- **WHEN** the app starts on a fresh install and the first composable renders `Text(..., style = MaterialTheme.typography.bodyLarge)`
- **THEN** the text renders in Roboto Flex immediately (no FOUT) because it's bundled.

#### Scenario: Display text may FOUT on first launch

- **WHEN** the app starts on a fresh install (no Fraunces cached by the provider) and a composable renders `Text(..., style = MaterialTheme.typography.displayLarge)`
- **THEN** the text may render briefly in `FontFamily.Serif` before swapping to Fraunces once the Downloadable Fonts provider returns.

### Requirement: Feature code MUST NOT hard-code theme values

Feature modules and UI composables MUST obtain colors, typography, shapes, spacing, elevation, and motion from `MaterialTheme.*`. Hard-coded color literals, duplicated dp dimensions, or inline TextStyles duplicating typography roles are prohibited in production UI code.

#### Scenario: Feature code reads tokens via MaterialTheme

- **WHEN** `git grep -nE 'Color\(0x[0-9A-Fa-f]+\)' app/src/main` is run (ignoring generated files)
- **THEN** the only matches are inside the `:designsystem` module or in `@Preview` scaffolding — never in feature production code paths.

### Requirement: PostCard is the canonical post-rendering composable

The module MUST expose stateless, loaded-state-only `@Composable fun PostCard(post: PostUi, callbacks: PostCallbacks = PostCallbacks(), modifier: Modifier = Modifier)`. All post-listing screens MUST render posts using this composable. It MUST NOT accept loading/error status parameters; loading is rendered by `PostCardShimmer()`.

#### Scenario: A feed list cell uses PostCard

- **WHEN** the feed screen's `LazyColumn` renders an item
- **THEN** the only post-rendering composable invoked is `PostCard(post = ..., callbacks = ...)` and no ad-hoc post-shaped layout is built inline.

#### Scenario: Toggling like fires the callback without local state

- **WHEN** a user taps the like icon on a PostCard whose `post.viewer.isLikedByViewer == false`
- **THEN** `callbacks.onLike(post)` fires exactly once, and the visual liked-state of the card does NOT flip until the host VM produces a new `PostUi` with `viewer.isLikedByViewer == true` (PostCard reads only what's in `post`).

### Requirement: PostCard renders facet-styled body text via the upstream helper

PostCard's body text MUST be rendered by passing `post.text` and `post.facets` to `rememberBlueskyAnnotatedString(text, facets)` and binding into `Text(annotated, ...)`. Facet rendering MUST NOT be re-implemented or pre-formatted outside composition.

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

PostCard MUST dispatch `when (post.embed)` exhaustively across `EmbedUi` variants: `Empty`, `Images` (`PostCardImageEmbed`), `Video` (`videoEmbedSlot`), `External` (`PostCardExternalEmbed`), `Record` (`PostCardQuotedPost`), `RecordUnavailable` (`PostCardRecordUnavailable`), `RecordWithMedia` (`PostCardRecordWithMediaEmbed`), and `Unsupported` (`PostCardUnsupportedEmbed`).

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

The module MUST expose `@Composable fun PostCardImageEmbed(items: ImmutableList<ImageUi>, modifier: Modifier = Modifier)`. It renders 1–4 images clamped to max-height 180dp (1 full, 2 columns, 3 split, 4 grid) using `NubecitaAsyncImage` clipped to `RoundedCornerShape(16.dp)`.

#### Scenario: Single-image post renders one full-width image

- **WHEN** `PostCardImageEmbed(items = persistentListOf(ImageUi(url = "...", altText = "...")))` is composed
- **THEN** the layout is one Box with `fillMaxWidth().heightIn(max = 180.dp).clip(RoundedCornerShape(16.dp))` containing one `NubecitaAsyncImage` with `ContentScale.Crop`.

#### Scenario: Two-image post renders side-by-side

- **WHEN** `PostCardImageEmbed(items = persistentListOf(image1, image2))` is composed
- **THEN** the layout is a `Row` with two equal-weight columns, each containing a `NubecitaAsyncImage` clipped to rounded corners.

### Requirement: PostCard exposes interaction callbacks via a `PostCallbacks` data class

PostCard's interactions MUST be passed via `data class PostCallbacks(val onTap: (PostUi) -> Unit = {}, val onAuthorTap: (AuthorUi) -> Unit = {}, val onLike: (PostUi) -> Unit = {}, val onRepost: (PostUi) -> Unit = {}, val onReply: (PostUi) -> Unit = {}, val onShare: (PostUi) -> Unit = {})`, defaulting all callbacks to no-op.

#### Scenario: A preview composes PostCard with no callbacks

- **WHEN** a `@Preview` calls `PostCard(post = previewPost)`
- **THEN** the composable compiles and renders without warnings; `callbacks` defaults to `PostCallbacks()` and every interaction is a no-op.

#### Scenario: A host wires real callbacks once per screen

- **WHEN** `FeedScreen` composes `LazyColumn { items(posts) { post -> PostCard(post, callbacks = remember { PostCallbacks(onTap = { ... }, ...) }) } }`
- **THEN** the same `PostCallbacks` instance is reused across all visible items, per Compose's stability rules.

### Requirement: `Modifier.shimmer()` provides a reusable animated loading-state placeholder

The module MUST expose `@Composable fun Modifier.shimmer(durationMillis: Int = 1500): Modifier` painting an animated linear-gradient brush cycling across theme container colors (`surfaceContainerHighest` and `surfaceContainerHigh`) via `rememberInfiniteTransition`. Third-party shimmer dependencies are prohibited.

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

The module MUST expose `@Composable fun PostCardShimmer(modifier: Modifier = Modifier)` mirroring `PostCard` geometry with `Modifier.shimmer()`: 40dp avatar circle, staggered text bars, optional 180dp embed box, and four action dots.

#### Scenario: Feed loading list renders shimmer placeholders

- **WHEN** the feed screen's `LazyColumn` is in initial-load state and renders placeholder slots
- **THEN** each placeholder is `PostCardShimmer()` (typically 6–8 instances, matching the visible viewport), and once data arrives the same `LazyColumn` swaps the shimmers for `PostCard` instances.

#### Scenario: PostCardShimmer renders without arguments

- **WHEN** a `@Preview` calls `PostCardShimmer()`
- **THEN** the composable renders the full skeleton (avatar circle + text bars + action dots) at PostCard's default geometry, with all shapes shimmering.

### Requirement: PostCard ships @Preview variants exercising every visual state

`PostCard.kt` MUST provide `@Preview` composables wrapped in `NubecitaTheme`: minimal empty post, typical post, single-image embed, Unsupported embed, repost kicker, and mention/link facets. Previews must render without exceptions in Android Studio.

#### Scenario: Studio preview pane renders all variants

- **WHEN** a developer opens `PostCard.kt` in Android Studio with the preview pane visible
- **THEN** every `@Preview` listed above renders without exceptions and visually matches the design-spec layout.

### Requirement: `RobotoFlexFontFamily` honors the variable `wght` axis via per-weight `FontVariation` declarations

`RobotoFlexFontFamily` MUST declare `Font` entries with `FontVariation.weight(N)` for supported weights (`Normal` 400, `Medium` 500, `SemiBold` 600, `Bold` 700) referencing `R.font.roboto_flex` to ensure correct rendering weights.

#### Scenario: bodyLarge renders at FontWeight.Normal visually distinct from titleMedium SemiBold

- **WHEN** a screenshot test renders two stacked `Text` composables — one styled `bodyLarge` (declared `FontWeight.Normal`) and one styled `titleMedium` (declared `FontWeight.SemiBold`) — with identical text content
- **THEN** the rendered glyphs SHALL show visibly different stroke weights — the SemiBold text SHALL be heavier than the Normal text

#### Scenario: Adding a new FontWeight to Type.kt requires a corresponding FontFamily entry

- **WHEN** a future change adds a `Type.kt` style declaring `FontWeight.ExtraBold` (800) on `RobotoFlexFontFamily`
- **THEN** that change MUST also add a `Font(resId = R.font.roboto_flex, weight = FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800)))` entry to `RobotoFlexFontFamily`, otherwise the new style SHALL fall back to the closest declared weight at the same heavy-default rendering this requirement was created to fix

### Requirement: `PostCard.AuthorLine` renders displayName + handle + timestamp in a single non-wrapping row with right-pinned timestamp

`PostCard.AuthorLine` MUST render displayName, handle, and relative timestamp on a single line. The display name takes intrinsic width, handle has `weight(1f, fill = false)` with ellipsis, a `Spacer(Modifier.weight(1f))` absorbs slack, and timestamp renders right-pinned at intrinsic width.

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

`PostCard` MUST accept optional `videoEmbedSlot: @Composable (EmbedUi.Video) -> Unit = {}`. When `embed is EmbedUi.Video`, it invokes this slot. `:designsystem` MUST NOT depend on feature modules; the host supplies video rendering.

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

`PostCardQuotedPost` MUST render inside a `Surface` (`surfaceContainerLow`, `12.dp` radius) with 32dp `NubecitaAvatar`, single-line non-wrapping author header, unconstrained `bodyMedium` text, and `QuotedEmbedSlot`. It renders no action row and is non-clickable.

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

`PostCardQuotedPost` MUST dispatch `when (quotedPost.embed)` exhaustively over `QuotedEmbedUi`: `Empty`, `Images`, `External` (non-clickable), `Video` (`quotedVideoEmbedSlot`), `QuotedThreadChip` ("View thread" chip), and `Unsupported`. Nested records are structurally excluded.

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

`PostCardRecordUnavailable` MUST render a chip in `surfaceContainerHighest` (`8.dp` radius) with label "Quoted post unavailable" across all `Reason` variants (`NotFound`, `Blocked`, `Detached`, `Unknown`).

#### Scenario: All four Reason values render identical copy

- **WHEN** `PostCardRecordUnavailable` is rendered four times with `Reason.NotFound`, `Reason.Blocked`, `Reason.Detached`, `Reason.Unknown` respectively
- **THEN** the rendered `Text` content is the string "Quoted post unavailable" in all four cases; the only deliberate variation is the input parameter, not the output

### Requirement: `PostCard` exposes a `quotedVideoEmbedSlot` lambda for host-supplied quoted-video render

`PostCard` MUST accept optional `quotedVideoEmbedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? = null` and forward it to `PostCardQuotedPost` when rendering `EmbedUi.Record`. Default null leaves quoted video blank.

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

`PostCardRecordWithMediaEmbed` MUST stack media above quoted card inside a `Column` separated by an 8dp `Spacer` without an outer container. It accepts `record`, `media`, `onExternalMediaTap`, `videoEmbedSlot`, and `quotedVideoEmbedSlot`. It is non-clickable.

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

Icons MUST render via `NubecitaIcon(name: NubecitaIconName, filled, weight, grade, opticalSize)` backed by font `R.font.material_symbols_rounded`. Directional icons apply `Modifier.mirror()` in RTL locales. Using `androidx.compose.material.icons.*` is prohibited.

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

`NubecitaLogomark(modifier, tint = Color.Unspecified)` SHALL render the brand mark backed by `LogoImageVector` (72dp × 72dp). It renders multi-color when tint is unspecified and applies `ColorFilter.tint(tint)` when specified, with `contentDescription = "Nubecita"`.

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

:designsystem` SHALL expose `NotificationReasonIcon(reason: NotificationReason, modifier: Modifier = Modifier)` mapping reasons exhaustively to glyph and tint (e.g. `Favorite` with like-accent for Like, `Repeat` with repost-accent for Repost).

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

`PostCard` SHALL accept `connectAbove: Boolean = false` and `connectBelow: Boolean = false`. When either is true, it applies `Modifier.threadConnector(connectAbove, connectBelow, color = outlineVariant)` to draw connector lines above/below the avatar gutter.

#### Scenario: PostCard with both flags false is unchanged

- **WHEN** `PostCard(post = ..., connectAbove = false, connectBelow = false)` is composed
- **THEN** the rendered output is pixel-identical to the pre-change `PostCard(post = ...)` — no threadConnector applied

#### Scenario: PostCard with connectAbove + connectBelow draws full connector

- **WHEN** `PostCard(post = ..., connectAbove = true, connectBelow = true)` is composed
- **THEN** the rendered output applies `Modifier.threadConnector(connectAbove = true, connectBelow = true, color = MaterialTheme.colorScheme.outlineVariant)` to the post's outer container, drawing connector lines above and below the avatar

### Requirement: `:designsystem` provides a `ThreadCluster` composable

`ThreadCluster(root, parent, leaf, modifier, callbacks, hasEllipsis, leafVideoEmbedSlot, leafQuotedVideoEmbedSlot, onFoldTap)` SHALL render a feed reply cluster in a `Column`: root `PostCard` (connectBelow), optional `ThreadFold`, parent `PostCard`, and leaf `PostCard` (connectAbove). If parent equals root, the parent slot collapses.

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

:designsystem` SHALL bundle the Fraunces variable font asset (`R.font.fraunces`) and expose a typography style with `SOFT = 70` for profile hero display names. The font MUST be embedded in the APK at build time.

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

`BoldHeroGradient(banner: String?, avatarHue: Int, modifier, content)` SHALL decode banner images off the main thread, extract a 2-stop gradient via `Palette`, cache results by URL/CID, and fallback to deterministic `avatarHue` gradients when banner is null.

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

`ProfilePillTabs(tabs, selectedTab, onTabSelect, modifier)` SHALL render 36dp pill tabs wrapping `PrimaryTabRow`. Active tabs use `primary` fill and `onPrimary` content with `FILL = 1` icon; inactive tabs use transparent fill, `onSurface`, and `FILL = 0`.

#### Scenario: Active tab renders with primary container fill and filled icon

- **WHEN** `ProfilePillTabs` is composed with `tabs = [Posts, Replies, Media]` and `selectedTab = Posts`
- **THEN** the rendered Posts pill has `Color = MaterialTheme.colorScheme.primary` as its container fill, the Posts icon renders with `FontVariation.Setting("FILL", 1f)` applied via `NubecitaIcon`, and the Replies + Media pills render with transparent container fills and `FILL = 0`

#### Scenario: Tab selection invokes onTabSelect with the new tab

- **WHEN** the user taps the Replies pill while Posts is currently active
- **THEN** `onTabSelect(Replies)` is invoked exactly once; the composable does NOT internally re-render with `selectedTab = Replies` until the parent passes the new `selectedTab` parameter (state hoisting is preserved)

### Requirement: PostCard renders multi-image embeds via `HorizontalMultiBrowseCarousel`

When `EmbedUi.Images.images.size > 1`, `PostCard` SHALL delegate to `HorizontalMultiBrowseCarousel` with M3 default `preferredItemWidth` and per-slide aspect ratios. When `images.size == 1`, the existing single-image rendering is preserved byte-for-byte.

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

Light and dark `ColorScheme`s MUST assign primary, secondary, and tertiary roles at M3 tonal stops (40/80 for accents, 100/20 for `on*`, 90/30 for container, 10/90 for `on*Container`). Reachable text pairs MUST meet WCAG 2.1 AA (≥ 4.5:1; outline ≥ 3:1).

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

The dark `ColorScheme` surface ramp MUST use deepened tones below M3 canonical stops (`surfaceContainerLowest` through `surfaceContainerHighest`) to increase depth and perceived contrast on OLED displays, maintaining minimum 3:1 contrast against outlines.

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

When two accent affordances appear adjacent in a layout, one MUST use a filled role (`primary`/`secondary`) and the other MUST use a container or tonal role (`surfaceContainer`/`tonalContainer`) to preserve clear visual hierarchy.

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

The `tertiary` color role and its containers SHALL be reserved exclusively for auxiliary, non-critical affordances and celebratory badges. They MUST NOT be used for primary call-to-action buttons or destructive actions.

#### Scenario: Tertiary carries decoration only

- **WHEN** a feature surface uses `tertiary` or `tertiaryContainer`
- **THEN** the element SHALL be auxiliary — a badge, mention chip, or tag — and the screen's primary action SHALL use `primary` or `primaryContainer`.

### Requirement: The brand identity blue is a fixed constant, separate from the primary ramp

Brand identity blue SHALL be declared as fixed constant `NubecitaPalette.LauncherBlue` (`#0A7AFF`), decoupled from the dynamic `primary` ramp to preserve brand recognition on app icons and launch surfaces across all themes.

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

### Requirement: Icon action controls drawn over media MUST use the shared overlay components

Feature modules MUST consume `:designsystem` overlay control components for any icon action control drawn over media, and MUST NOT declare bare, background-less `IconButton`s tinted `Color.White` over video or imagery.

#### Scenario: Feature module renders a control over video

- **WHEN** a feature module renders an interactive control on top of a video surface
- **THEN** it calls a `:designsystem` overlay control composable
- **AND** it declares no background, scrim, alpha, or blur of its own for that control

#### Scenario: No background-less overlay controls remain

- **WHEN** `:feature:videos:impl` and `:feature:videoplayer:impl` are inspected after adoption
- **THEN** every secondary interactive control drawn over media resolves to a `:designsystem` overlay
  component
- **AND** none renders a white-tinted icon with no backing treatment
- **AND** the primary play/pause transport button retains its filled primary button styling and morph interaction
