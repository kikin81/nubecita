# app-theme-selection Specification

## Purpose
How the user chooses the app's theme, and how that choice reaches the screen.

Covers the option set and what each option means, how the choice is persisted
and defaulted, how it is applied without a wrong-theme frame at cold start, how
it drives system bar appearance, and the Settings surface that presents it.

The axis is deliberately single: `Dynamic` / `Light` / `Dark` are mutually
exclusive, with no separate "use wallpaper colors" control, so a future custom
theme is one more entry in the same list rather than a second dimension. Two
types carry the choice — `ThemePreference` (`:core:preferences`) persists it and
`AppTheme` (`:designsystem`) renders it; see the `design-system` capability for
the rendering contract.
## Requirements
### Requirement: The app offers exactly three mutually-exclusive themes

The app MUST offer the user a single mutually-exclusive choice among three themes — `Dynamic`, `Light`, and `Dark` — modeled as `net.kikin.nubecita.designsystem.AppTheme`. Exactly one is active at any time; there is no separate "use wallpaper colors" control, and no combination of options is reachable.

Two distinct types carry this choice, and requirements below name whichever one they constrain: `AppTheme` (in `:designsystem`) is the **rendering** identity whose constants are `Dynamic` / `Light` / `Dark`; `ThemePreference` (in `:core:preferences`) is the **persisted** identity whose constants are `DYNAMIC` / `LIGHT` / `DARK`. The composition root maps the latter to the former.

- `Dynamic` MUST resolve to Material You wallpaper-derived color with light/dark following the OS setting.
- `Light` MUST resolve to the Nubecita brand palette in its light variant, regardless of the OS light/dark setting.
- `Dark` MUST resolve to the Nubecita brand palette in its dark variant, regardless of the OS light/dark setting.

The brand palette these options render is owned by the `design-system` capability.
`Dynamic` is unaffected by brand palette changes: it sources every role from
`dynamicLightColorScheme` / `dynamicDarkColorScheme` and no brand color is
reachable through it on API 31+.

#### Scenario: Dynamic follows the OS light/dark setting

- **WHEN** the active theme is `Dynamic` and the OS switches from light to dark mode
- **THEN** the app's color scheme switches from `dynamicLightColorScheme` to `dynamicDarkColorScheme` without a restart.

#### Scenario: Dark overrides an OS set to light

- **WHEN** the active theme is `Dark` and the OS is in light mode
- **THEN** the app renders the brand dark palette, and `MaterialTheme.colorScheme` is NOT sourced from any `dynamic*ColorScheme` call.

#### Scenario: Light overrides an OS set to dark

- **WHEN** the active theme is `Light` and the OS is in dark mode
- **THEN** the app renders the brand light palette, and `MaterialTheme.colorScheme.primary` equals the brand Sky-40 (`#0061A6`).

#### Scenario: Dynamic is unchanged by a brand palette change

- **WHEN** the active theme is `Dynamic` on an Android 12+ device and the brand palette is changed
- **THEN** no rendered color changes, because every role is wallpaper-derived.

#### Scenario: Dynamic degrades to the brand palette before Android 12

- **WHEN** the active theme is `Dynamic` on an Android 11 or earlier device
- **THEN** the brand palette is used (the dynamic color API is unavailable) and light/dark still follows the OS setting.

### Requirement: Dynamic is the default and unrecognized stored values fall back to it

`Dynamic` MUST be the default theme. An install that has never opened the Appearance screen MUST render as `Dynamic` — preserving the app's behavior before this change. A stored preference value that does not correspond to a known option MUST fall back to `Dynamic` rather than throwing.

#### Scenario: Fresh install defaults to Dynamic

- **WHEN** the app starts on an install whose theme preference has never been written
- **THEN** `UserPreferencesRepository.themePreference` emits `ThemePreference.DYNAMIC` and the app renders wallpaper-derived color following the OS.

#### Scenario: Legacy or corrupt stored value falls back

- **WHEN** the DataStore holds a theme string that no longer maps to a `ThemePreference` constant — including the pre-change `"SYSTEM"` value
- **THEN** the repository emits `ThemePreference.DYNAMIC`, no exception propagates, and the app renders normally.

### Requirement: The theme choice persists and applies immediately app-wide

Selecting a theme MUST persist it via `UserPreferencesRepository.setThemePreference` and MUST take effect across every screen immediately, with no confirmation step, no restart, and no navigation away from the Appearance screen.

#### Scenario: Selection repaints the app without leaving the screen

- **WHEN** the user taps `Dark` on the Appearance screen while the app is rendering `Light`
- **THEN** the Appearance screen and every surface behind it repaint in the dark brand palette, the `Dark` row becomes the selected row, and the screen stays open.

#### Scenario: Selection survives process death

- **WHEN** the user selects `Light`, the process is killed, and the app is relaunched
- **THEN** the app renders the light brand palette.

#### Scenario: Re-tapping the already-selected theme is inert

- **WHEN** the user taps the row that is already selected
- **THEN** the selection and the rendered theme are unchanged, and the ViewModel MUST NOT call `setThemePreference` — a re-tap performs no repository write at all.

### Requirement: The chosen theme is rendered on the first frame

The app MUST NOT render a frame in a theme other than the stored one. Because the preference is read asynchronously from DataStore, the composition root MUST hold the splash screen until the first theme value resolves, extending the existing `setKeepOnScreenCondition` predicate rather than blocking the main thread on a synchronous read.

#### Scenario: Cold start with a non-default theme does not flash

- **WHEN** the user has selected `Dark`, the OS is in light mode, and the app is cold-started
- **THEN** no frame of app content is drawn in a light scheme — the splash remains until the theme resolves, and the first content frame is dark.

#### Scenario: The theme read does not block the main thread

- **WHEN** the composition root obtains the stored theme at startup
- **THEN** it does so from a `Flow` observed off the main thread, and MUST NOT call `runBlocking` on the main thread to read DataStore.

### Requirement: System bar icon appearance follows the chosen theme

Status-bar and navigation-bar icon appearance MUST be derived from the resolved app theme, not from the OS light/dark setting. `enableEdgeToEdge`'s default `SystemBarStyle.auto` keys off the OS configuration, which would draw dark icons over a dark app surface — and vice versa — whenever the chosen theme disagrees with the OS.

#### Scenario: Dark theme on a light OS keeps status bar icons legible

- **WHEN** the active theme is `Dark` and the OS is in light mode
- **THEN** the status bar and navigation bar are configured for light-on-dark icons, so the icons remain visible against the app's dark surface.

#### Scenario: Light theme on a dark OS keeps status bar icons legible

- **WHEN** the active theme is `Light` and the OS is in dark mode
- **THEN** the status bar and navigation bar are configured for dark-on-light icons.

#### Scenario: Bar appearance updates when the theme changes at runtime

- **WHEN** the user changes the theme from the Appearance screen
- **THEN** the system bar icon appearance is re-applied to match the newly resolved theme without a restart.

### Requirement: Settings exposes an Appearance row showing the current theme

The Settings root screen MUST render an "Appearance" row that navigates to the Appearance screen and displays the currently-selected theme's label as its supporting text, so the active choice is visible without opening the sub-page.

#### Scenario: Row reflects the active theme

- **WHEN** the active theme is `Dark` and the user opens Settings
- **THEN** the Appearance row's supporting text reads the localized label for `Dark`.

#### Scenario: Row navigates to the Appearance screen

- **WHEN** the user taps the Appearance row
- **THEN** the `Appearance` NavKey is pushed onto MainShell's inner back stack.

### Requirement: The Appearance screen presents themes as a single-select list group

The Appearance screen MUST render the three options with `NubecitaListGroup` / `NubecitaListItem(selected = …)` per the Settings sub-page convention (`nubecita-1ow5`), so it matches the Material 3 Expressive treatment used by the other Settings sub-pages. Each row MUST carry a localized label and MUST expose its selected state in semantics as a single selectable node.

#### Scenario: Exactly one row reads as selected

- **WHEN** the Appearance screen is composed with `Light` active
- **THEN** the `Light` row's semantics report `selected = true` and the `Dynamic` and `Dark` rows report `selected = false`.

#### Scenario: Options render in a fixed order

- **WHEN** the Appearance screen is composed
- **THEN** the rows appear in the order `Dynamic`, `Light`, `Dark`, with `Dynamic` carrying supporting text explaining that it follows the system and wallpaper.

#### Scenario: Labels are localized

- **WHEN** the app runs under `es-419` or `pt-BR`
- **THEN** every Appearance label, supporting text, and the screen title render translated strings, with no `MissingTranslation` lint finding in the owning module.

### Requirement: The Appearance screen is adaptive

The `Appearance` NavKey MUST be registered on `@MainShell` and tagged with `adaptiveDialog()`, so it presents full-screen on Compact width and as a centered dialog on Medium/Expanded — identical to the other Settings sub-routes. The push site MUST be a plain `navState.add(Appearance)` with no width check.

#### Scenario: Phone presents full-screen

- **WHEN** the Appearance row is tapped on a Compact-width device
- **THEN** the screen occupies the full window and the bottom navigation bar is hidden.

#### Scenario: Tablet presents as a dialog

- **WHEN** the Appearance row is tapped on a Medium- or Expanded-width device
- **THEN** the screen renders inside the adaptive dialog scene and the Settings screen behind it stays composed.

### Requirement: The theme choice is reported to analytics without a new wire value

The `theme_preference` GA4 user property MUST continue to be set from the stored preference. `ThemePreference.DYNAMIC` MUST map to the existing analytics wire value `"system"` so historical GA4 data for this property stays continuous — the option was renamed, not redefined.

#### Scenario: Selecting a theme updates the user property

- **WHEN** the user selects `Light`
- **THEN** the `theme_preference` user property is set to `"light"`.

#### Scenario: Dynamic reports as system

- **WHEN** the stored preference is `ThemePreference.DYNAMIC`
- **THEN** the `theme_preference` user property is set to `"system"`.

### Requirement: Existing accessibility behavior applies to every theme

The contrast-level handling (`ContrastLevel.Standard` / `Medium` / `High`) and the reduce-motion behavior already implemented inside `NubecitaTheme` MUST continue to apply unchanged under all three themes. Choosing `Light` or `Dark` MUST NOT bypass the high-contrast brand schemes.

#### Scenario: High contrast is honored under a forced theme

- **WHEN** the active theme is `Dark` and the OS contrast level is high
- **THEN** the app renders `nubecitaDarkHighContrastColorScheme()`, not the standard dark scheme.

#### Scenario: Reduce motion is honored under a forced theme

- **WHEN** the active theme is `Light` and animator duration scale is 0
- **THEN** the reduced motion scheme is applied exactly as it is under `Dynamic`.

### Requirement: The theme model is extensible to future custom themes

`AppTheme` and the persisted `ThemePreference` MUST be shaped so a future custom theme is added as an additional option in the same single list, without restructuring the picker, the persistence format, or the composition-root mapping. The persisted representation MUST remain a forward-compatible string whose unknown values fall back to `ThemePreference.DYNAMIC`.

#### Scenario: An older build tolerates a newer stored value

- **WHEN** a build that does not know about a custom theme reads a stored preference written by a build that does
- **THEN** it falls back to `ThemePreference.DYNAMIC` and renders normally rather than crashing.
