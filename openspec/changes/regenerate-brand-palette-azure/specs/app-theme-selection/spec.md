## MODIFIED Requirements

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
