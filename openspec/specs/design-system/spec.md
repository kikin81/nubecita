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
