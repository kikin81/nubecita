# Regenerate the brand palette (Azure)

Tracked as bd `nubecita-bvff`.

## Why

Text and controls painted in the brand accent are hard to read in light mode. Three
pairs in the shipping light theme fall below the WCAG AA 4.5:1 minimum for normal
text — including a filled button whose white label sits at 4.01:1 on its own
background:

| Pair | Measured | Required |
| --- | --- | --- |
| `Sky50` primary as text/icon on light `surface` | 3.92:1 | 4.5:1 |
| `Sky50` filled button with white label | 4.01:1 | 4.5:1 |
| `Peach50` secondary on light `surface` | 3.81:1 | 4.5:1 |

The cause is a single deviation: light `primary` is assigned tonal stop **50**
where Material 3 specifies tonal stop **40**. Tone 50 is simply too light to carry
white text or to serve as body-weight text on a near-white surface.

Fixing the tone necessarily changes the accent's appearance, so the accessibility
fix and the palette refresh are the same change rather than two. That makes this
the right moment to also resolve a long-standing complaint about the palette
itself: the warm `Peach` secondary clashes with the cool `Sky` primary, and the
two compete for attention inside a single post card.

## What Changes

- **BREAKING (visual)** — The brand tonal palette is replaced. `Sky` (primary)
  moves off its violet lean to a truer blue at HCT hue 255; the warm `Peach`
  secondary is replaced by **`Lagoon`**, a cyan at hue 215; `Lilac` is replaced by
  **`Orchid`** at hue 318. Every accent role in all six `ColorScheme`s is
  regenerated from these ramps.
- Light-mode accent roles move from tonal stop 50 to the Material 3 stop **40**,
  which resolves all three AA failures. Every on/container pair in every scheme
  clears 4.5:1, and every `outline` clears 3:1.
- The dark surface ramp is deepened from HCT tone 6 to tone **3**, with the
  container steps widened so the depth tiers stay separable near black.
- Two new design-system rules are added, both derived from measurement rather than
  taste: `primaryContainer` may never be placed adjacent to `secondaryContainer`,
  and `tertiary` is reserved for auxiliary, non-critical surfaces.
- `ColorSchemeTest` stops asserting specific hex values and instead asserts the
  contrast **property** across all six schemes, so a future palette edit that
  breaks accessibility fails the build.

## Non-goals

- **Dynamic color is untouched.** `AppTheme.Dynamic` is the default and wins on
  API 31+, so this change is visible only to users who explicitly choose Light or
  Dark, and to Android 11 and earlier. Overriding wallpaper-derived surfaces was
  considered and rejected — Material You is the platform contract, and the brand
  palette is the opt-out, not the override.
- **The launcher icon and splash screen keep `#0A7AFF`.** The brand blue on the
  home screen and in the Play listing is a fixed identity mark, in the same way
  `VerifiedBlue` is deliberately detached from the theme. Repainting the icon is a
  store-facing decision and belongs to its own change.
- **No new theme option.** A fourth `AppTheme.Black` for OLED devices was
  considered and deferred; the deepened dark ramp serves that need without adding
  an enum entry, a Settings row, and its own translations.
- **The semantic accents do not change.** Like, repost, supporter, success and
  warning all clear 7.44:1 or better against the new dark surface, so they need no
  retuning.
- **No lint rule is added** for the two new design-system rules. They are recorded
  in `docs/design-system/surface-roles.md` and enforced by code review, matching
  the precedent set for the reserved `surfaceDim` / `surfaceBright` /
  `surfaceContainerLowest` tokens.

## Capabilities

### New Capabilities

None. This change modifies existing behavior rather than introducing a capability.

### Modified Capabilities

- `design-system`: The brand tonal palette named in the color-scheme requirements
  changes from Sky / Peach / Lilac to Sky / Lagoon / Orchid, the light and dark
  `primary` scenarios pin new values, a new requirement fixes the accent tonal
  stops at the Material 3 mapping with an explicit contrast floor, a new
  requirement sets the deepened dark surface ramp, and a new requirement records
  the container-adjacency and tertiary-usage rules.
- `app-theme-selection`: The scenario asserting that `Light` renders
  `MaterialTheme.colorScheme.primary` equal to `#0A7AFF` changes to the new
  light primary. Behavior of the theme axis itself is unchanged.

## Impact

**Code**

- `designsystem/src/main/kotlin/.../designsystem/Color.kt` — `NubecitaPalette`
  ramps and all six `ColorScheme` builders. This is the only production source
  file whose behavior changes; `Theme.kt` is untouched because the schemes already
  derive mechanically.
- `designsystem/src/test/kotlin/.../ColorSchemeTest.kt` — rewritten to
  property-based contrast assertions.
- `designsystem/src/test/kotlin/.../NubecitaThemeTest.kt` — updated expected values.

**Documentation and references**

- `openspec/references/design-system/colors_and_type.css` — the canonical token
  source named by the `design-system` spec; regenerated to match.
- `docs/design-system/surface-roles.md` — gains the two new rules.

**Screenshot baselines**

All 1,147 committed baselines change, across `:designsystem` (241) and nine
`:feature:*:impl` modules. This inverts the standing repo rule against committing
a whole-module regeneration: here every baseline legitimately changes, and the
risk is the opposite one — a baseline that silently pins the old accent would read
as a pass.

**Not affected**

`:app`, every `:core:*` module, the launcher icon and splash resources, the
dynamic-color code path, and `NubecitaSemanticColors`.

**Baseline deviation**

None. The change stays within the existing Compose / Material 3 Expressive
theming approach and introduces no new dependency; the palette is generated
offline with `@material/material-color-utilities` and the resulting values are
committed as literals, exactly as today.
