## ADDED Requirements

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

### Requirement: No two accent `*Container` fills may be rendered adjacent

Feature code MUST NOT render any two of `primaryContainer`, `secondaryContainer`
and `tertiaryContainer` immediately adjacent to one another. Measured across the
scheme, every such pairing is indistinguishable:

| Adjacent fills | Light | Dark |
| --- | --- | --- |
| `primaryContainer` / `secondaryContainer` | 1.00:1 | 1.01:1 |
| `primaryContainer` / `tertiaryContainer` | 1.00:1 | 1.00:1 |
| `secondaryContainer` / `tertiaryContainer` | 1.01:1 | 1.01:1 |

The cause is structural rather than incidental to this palette: Material 3 assigns
all three accent families the same tonal stop for a given role — 90 in light, 30 in
dark — so container fills differ *only* in hue. Hue-only separation disappears for
a viewer with deuteranopia and degrades on a cold-calibrated display, and no choice
of brand hues can fix it.

Side-by-side accent affordances MUST therefore pair a **filled** accent role with a
**container** role. Every such pairing separates acceptably, so the choice of which
family takes the filled role is free:

| | Light | Dark |
| --- | --- | --- |
| `primary` / `secondaryContainer` | 4.97:1 | 5.49:1 |
| `secondary` / `primaryContainer` | 5.01:1 | 5.47:1 |
| `tertiary` / `secondaryContainer` | 4.99:1 | 5.50:1 |

This rule is enforced by code review, following the precedent set for the reserved
`surfaceDim` / `surfaceBright` / `surfaceContainerLowest` tokens. No lint rule is
added.

#### Scenario: Two adjacent tonal buttons do not both use container roles

- **WHEN** a screen renders two adjacent accent affordances, such as a Follow and a Message button
- **THEN** they SHALL NOT both draw their fill from a `*Container` role; at least one SHALL use a filled accent role (`primary`, `secondary` or `tertiary`) with its matching `on*`. Which family takes the filled role is a per-screen decision — `primary` + `secondaryContainer` and `secondary` + `primaryContainer` both satisfy this.

#### Scenario: Adjacent fills are separable regardless of which pairing is chosen

- **WHEN** any two accent affordances are rendered adjacent to one another
- **THEN** their fill colours SHALL have a WCAG 2.1 contrast ratio of at least 3:1 against each other, which is the property the pairing rule exists to guarantee.

#### Scenario: Adjacent container fills are rejected in review

- **WHEN** a change places a `primaryContainer` surface directly beside a `secondaryContainer` surface
- **THEN** review SHALL reject it, citing the measured 1.00:1 separation.

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

## MODIFIED Requirements

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

### Requirement: `:designsystem` provides a `NubecitaLogomark` composable

`:designsystem/component/NubecitaLogo.kt` SHALL expose a public `@Composable fun NubecitaLogomark(modifier: Modifier = Modifier, tint: Color = Color.Unspecified)` that renders the brand cloud mark with bow (no wordmark), backed by `LogoImageVector` — a Compose `ImageVector` port of the mark, held in `:designsystem/component/LogoImageVector.kt`. Its intrinsic size SHALL be 72dp × 72dp.

The mark SHALL be multi-colour by default: a white cloud body, a pink bow
(`#F7AAC9` / `#E36DA0`), and two identity-blue stroke accents sourced from
`NubecitaPalette.LauncherBlue`.

The `tint` parameter SHALL be honoured only when specified: the composable SHALL
apply `ColorFilter.tint(tint)` when `tint.isSpecified` and SHALL apply no colour
filter otherwise. `Color.Unspecified` therefore means "render multi-colour", which
is legible only against a contrasting or branded background. Against a
low-contrast surface — notably the near-white light theme background — a caller
MUST pass an explicit `tint`, or the white cloud body renders invisible.

Call sites choose the tint by what the mark *means* at that site:

| Site | Tint | Why |
| --- | --- | --- |
| In-app splash placeholder | `NubecitaPalette.LauncherBlue` | Brand identity; must match the system splash background it hands off from |
| In-app chrome (e.g. onboarding) | `MaterialTheme.colorScheme.primary` | Follows the active theme, including wallpaper-derived colour under `AppTheme.Dynamic` |
| Branded/contrasting background | omit (multi-colour) | The full mark is legible there |

No call site may pass a tonal-ramp stop such as `Sky50` to express either meaning:
after this change `Sky50` is an ordinary ramp stop whose value follows the ramp.

This requirement replaces a stale description. The previous text specified a
default tint of `MaterialTheme.colorScheme.primary`, a backing
`nubecita_logomark.xml` vector drawable, and a single-colour silhouette with every
path at `#FFFFFFFF`. None of the three matches the implementation, and no
`nubecita_logomark.xml` exists in the repository.

The composable SHALL set `contentDescription = stringResource(R.string.logomark_content_description)` (value: `"Nubecita"`) so screen readers announce the brand name when the mark is used as the sole content of a tappable container.

The intrinsic aspect of the underlying vector SHALL be 1:1 (square). Callers control absolute size via the `modifier` parameter (`Modifier.size(...)` or layout-driven sizing).

#### Scenario: Logomark renders with default tint under static palette

- **WHEN** `NubecitaTheme(dynamicColor = false) { NubecitaLogomark(modifier = Modifier.size(96.dp)) }` is composed
- **THEN** a 96dp × 96dp mark SHALL render with no `ColorFilter` applied — white cloud body, pink bow, and `LauncherBlue` stroke accents — because the default `tint` is `Color.Unspecified`, NOT a theme-derived colour.

#### Scenario: Logomark accepts a custom tint

- **WHEN** `NubecitaLogomark(tint = Color.White)` is composed inside `NubecitaTheme`
- **THEN** the whole mark SHALL collapse to pure white regardless of the active palette

#### Scenario: In-app chrome tints the mark to the active accent

- **WHEN** `NubecitaLogomark(tint = MaterialTheme.colorScheme.primary)` is composed under `NubecitaTheme(dynamicColor = false)` in light mode
- **THEN** the mark SHALL collapse to brand Sky-40 (`#0061A6`), remaining legible against the near-white light surface where the untinted multi-colour rendering would not be.

#### Scenario: Logomark exposes its accessible label

- **WHEN** TalkBack focuses on a `NubecitaLogomark` composable
- **THEN** TalkBack SHALL announce `"Nubecita"` (from `R.string.logomark_content_description`)
