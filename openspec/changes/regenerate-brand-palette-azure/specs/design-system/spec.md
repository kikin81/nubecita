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

### Requirement: `primaryContainer` MUST NOT be placed adjacent to `secondaryContainer`

Feature code MUST NOT render a `primaryContainer` fill immediately adjacent to a
`secondaryContainer` fill. The two separate at 1.00:1 in light and 1.01:1 in dark —
they are indistinguishable.

The cause is structural rather than incidental to this palette: Material 3 assigns
dark `primary` and dark `secondary` the same tonal stop (80), so the entire
separation between the accent families is hue. Hue-only separation disappears for
a viewer with deuteranopia and degrades on a cold-calibrated display.

Side-by-side tonal affordances MUST therefore pair a **filled** accent role with a
**container** role — for example a `primary` filled button beside a
`secondaryContainer` tonal button, which separates at 4.97:1 in light and 5.49:1
in dark.

This rule is enforced by code review, following the precedent set for the reserved
`surfaceDim` / `surfaceBright` / `surfaceContainerLowest` tokens. No lint rule is
added.

#### Scenario: Two adjacent tonal buttons pair filled with container

- **WHEN** a screen renders two adjacent accent affordances, such as a Follow and a Message button
- **THEN** one SHALL use `primary` + `onPrimary` and the other SHALL use `secondaryContainer` + `onSecondaryContainer`, and they SHALL NOT both use `*Container` roles.

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

The error family is the one exception to hue generation. `Error40`, `Error50`,
`Error80` and `Error90` remain carried in `NubecitaPalette` — so every role is
still sourced from a single palette object — but they hold the Material 3 static
error colors and are NOT generated from the brand hues above. Error semantics must
stay recognisable across themes, and harmonising them toward the brand hue would
weaken that signal.

#### Scenario: Every role has a brand color

- **WHEN** any of the six `ColorScheme`s is instantiated
- **THEN** all of `primary`, `onPrimary`, `primaryContainer`, `onPrimaryContainer`, `secondary`, `onSecondary`, `secondaryContainer`, `onSecondaryContainer`, `tertiary`, `onTertiary`, `tertiaryContainer`, `onTertiaryContainer`, `background`, `onBackground`, `surface`, `onSurface`, `surfaceVariant`, `onSurfaceVariant`, `outline`, `outlineVariant`, `scrim`, `inverseSurface`, `inverseOnSurface`, `inversePrimary`, `surfaceDim`, `surfaceBright`, `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest` resolve to values derived from the brand tonal palette.

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
- **THEN** `MaterialTheme.colorScheme.primary` SHALL NOT equal `Color(0xFF0A7AFF)`, which remains reserved for the launcher icon and splash background only.

### Requirement: `:designsystem` provides a `NubecitaLogomark` composable

`:designsystem/component/NubecitaLogo.kt` SHALL expose a public `@Composable fun NubecitaLogomark(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary)` that renders the brand cloud-only mark (no wordmark) backed by the `nubecita_logomark.xml` vector drawable.

The vector drawable SHALL be a single-color rendering of the cloud silhouette ported from `openspec/references/design-system/assets/logomark-mono.svg` — 3 circles + 1 rounded rect, all with `android:fillColor="#FFFFFFFF"`. The composable SHALL apply `ColorFilter.tint(tint)` so the rendered color matches the `tint` parameter. The default tint of `MaterialTheme.colorScheme.primary` resolves to brand Sky-40 (`#0061A6`) under the static palette and to the user's wallpaper-derived primary under dynamic color.

The logomark's default tint therefore differs from the launcher icon and splash
background, which remain `#0A7AFF`. This is intentional: the launcher blue is a
fixed identity mark, in the same way `VerifiedBlue` is deliberately detached from
the theme, while the in-app logomark follows the active accent.

The composable SHALL set `contentDescription = stringResource(R.string.logomark_content_description)` (value: `"Nubecita"`) so screen readers announce the brand name when the mark is used as the sole content of a tappable container.

The intrinsic aspect of the underlying vector SHALL be 1:1 (square). Callers control absolute size via the `modifier` parameter (`Modifier.size(...)` or layout-driven sizing).

#### Scenario: Logomark renders with default tint under static palette

- **WHEN** `NubecitaTheme(dynamicColor = false) { NubecitaLogomark(modifier = Modifier.size(96.dp)) }` is composed
- **THEN** a 96dp × 96dp white-cloud image SHALL render tinted to brand Sky-40 (`#0061A6`)

#### Scenario: Logomark accepts a custom tint

- **WHEN** `NubecitaLogomark(tint = Color.White)` is composed inside `NubecitaTheme`
- **THEN** the cloud SHALL render in pure white regardless of the active palette

#### Scenario: Logomark exposes its accessible label

- **WHEN** TalkBack focuses on a `NubecitaLogomark` composable
- **THEN** TalkBack SHALL announce `"Nubecita"` (from `R.string.logomark_content_description`)
