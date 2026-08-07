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
- **THEN** the brand palette (Sky / Peach / Lilac / Neutral) is used instead of wallpaper-derived tones, and `MaterialTheme.colorScheme.primary` equals the brand Sky-50 (`#0A7AFF`) in light mode.

#### Scenario: Dynamic color default-on

- **WHEN** `NubecitaTheme { ... }` is composed without an explicit `dynamicColor` argument on an Android 12+ device — or equivalently `NubecitaTheme(appTheme = AppTheme.Dynamic) { ... }`
- **THEN** `MaterialTheme.colorScheme` is sourced from `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)` and brand colors are NOT visible.

#### Scenario: Dynamic color on pre-Android-12

- **WHEN** `NubecitaTheme { ... }` is composed on an Android 11 or earlier device, regardless of the `dynamicColor` argument
- **THEN** the brand palette is used (the dynamic color API isn't available) and `MaterialTheme.colorScheme.primary` equals the brand Sky-50.

#### Scenario: AppTheme.Dark forces the dark brand scheme

- **WHEN** `NubecitaTheme(appTheme = AppTheme.Dark) { ... }` is composed on an Android 12+ device whose OS is in light mode
- **THEN** the brand dark palette is used, no `dynamic*ColorScheme` call is made, and the result is identical to `NubecitaTheme(darkTheme = true, dynamicColor = false) { ... }`.

#### Scenario: AppTheme.Light forces the light brand scheme

- **WHEN** `NubecitaTheme(appTheme = AppTheme.Light) { ... }` is composed on a device whose OS is in dark mode
- **THEN** the brand light palette is used and the result is identical to `NubecitaTheme(darkTheme = false, dynamicColor = false) { ... }`.

#### Scenario: Contrast and motion handling is shared by both overloads

- **WHEN** any `AppTheme` is composed on a device with a high contrast level or with animators disabled
- **THEN** the high-contrast brand scheme and the reduced motion scheme are applied exactly as they are through the two-argument overload — the overload adds no branch of its own.
