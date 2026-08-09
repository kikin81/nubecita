## Why

Nubecita's appearance is entirely dictated by the OS: `NubecitaTheme` hard-codes `dynamicColor = true` and derives light/dark from `isSystemInDarkTheme()`, so a user who keeps their phone in light mode has no way to read Nubecita at night, and a user who dislikes their wallpaper-derived palette has no way to see the brand colors. "Force dark mode" is one of the most common requests for a social client, where long reading sessions and low-light use are the norm.

The storage half of this already exists but is inert: `ThemePreference` and `UserPreferencesRepository.themePreference` were added in `nubecita-y5h5` purely so the `theme_preference` analytics user property had a single source of truth. Its KDoc explicitly says "the app does not yet expose a theme picker … the storage + flow exist now so [the property] becomes accurate the moment a picker (and a `NubecitaTheme` read of this value) lands." This change is that picker.

## What Changes

- Add an **Appearance** settings sub-page presenting one mutually-exclusive list of themes: **Dynamic**, **Light**, **Dark**. Selection applies instantly, with no confirm step.
  - **Dynamic** — today's behavior: Material You wallpaper-derived color, light/dark following the OS. Remains the default.
  - **Light** / **Dark** — the Nubecita brand palette (Sky / Peach / Lilac / Neutral) locked to that brightness, independent of the OS setting. Picking these opts out of Material You.
- Add an **Appearance** row on the Settings root that pushes the sub-page and shows the current selection as supporting text.
- Introduce `AppTheme` in `:designsystem` (`Dynamic` / `Light` / `Dark`) as the theme-identity type, plus a `NubecitaTheme(appTheme: AppTheme, content: …)` overload that resolves it to the existing `darkTheme` / `dynamicColor` arguments. The existing two-argument overload is unchanged, so every `@Preview` and screenshot-test call site keeps working.
- Drive the composition root from the stored preference: `MainActivity` reads `themePreference`, maps it to `AppTheme`, and holds the splash screen until the first value resolves so the app never flashes the wrong theme on cold start.
- Re-derive the **system bar icon appearance** from the resolved theme rather than the OS. Today `enableEdgeToEdge()` uses its default `SystemBarStyle.auto`, which keys off the OS dark setting — with app theme Dark on an OS in light mode, the status-bar icons would be drawn dark-on-dark and become invisible.
- Rename the persisted `ThemePreference.SYSTEM` to `DYNAMIC` and make it the explicit default. **Not breaking in practice**: no code path has ever written this preference, so every install reads the default, and the repository's `runCatching { valueOf(...) }` already falls back to the default for any unrecognized stored string.
- Extend `ThemePreference`'s KDoc contract so a future `Custom` theme appends to the same single list without restructuring the picker, the storage, or the `AppTheme` mapping.

### Non-goals

- **Custom / user-authored themes.** The list and the `AppTheme` type are shaped so `Custom` can be appended later, but no custom palette, palette editor, or per-account theme ships here.
- **A separate "use wallpaper colors" toggle.** Deliberately rejected: a single mutually-exclusive list is what future custom themes extend cleanly. The accepted cost is that "wallpaper colors + forced dark" is not a reachable combination.
- **AMOLED / true-black, per-screen themes, font-size or density controls, and scheduled (time-based) theme switching.** Each is a separate change; Appearance is the page they would land on.
- **Changing the brand palette, contrast handling, or motion.** The existing high/medium-contrast and reduce-motion behavior inside `NubecitaTheme` is untouched and continues to apply to every theme.
- **Widget theming.** The Glance feed widgets follow the launcher's theme and are out of scope.

### Deviations from baseline

None. The Appearance screen is a standard MVI screen (`MviViewModel<AppearanceState, AppearanceEvent, AppearanceEffect>`), uses Hilt for injection, persists via the existing DataStore-backed `UserPreferencesRepository`, and renders with `NubecitaListGroup` / `NubecitaListItem(selected = …)` per the Settings sub-page convention (`nubecita-1ow5`). No new dependency is added.

## Capabilities

### New Capabilities

- `app-theme-selection`: The user-facing theme preference — the available options and their meaning, how the choice is persisted and defaulted, how it reaches the composition root without a cold-start flash, how it drives system bar appearance, and the Settings surface that presents it.

### Modified Capabilities

- `design-system`: The `NubecitaTheme` requirement gains an `AppTheme`-driven overload and its "dynamic color default-on" scenario is restated — the composition root now passes an explicit theme rather than relying on the `dynamicColor = true` default. The two-argument overload's contract is unchanged.

## Impact

**Modules and code**

| Area | Change |
|---|---|
| `:designsystem` | New `AppTheme` enum; new `NubecitaTheme(appTheme, content)` overload; system-bar-appearance resolution helper. `Theme.kt` |
| `:core:preferences` | `ThemePreference.SYSTEM` → `DYNAMIC`; default updated. Flavored module — its unit tests do **not** run under the root `testDebugUnitTest`. |
| `:core:analytics` | No wire change. `DYNAMIC` continues to map to the existing `ThemePreference.System` (`"system"`), preserving GA4 historical continuity for `theme_preference`. |
| `:app` | `MainActivity` reads the preference, maps to `AppTheme`, extends the splash keep-on-screen predicate, and re-applies `enableEdgeToEdge` with a theme-derived `SystemBarStyle`. `ProAnalyticsCoordinator` mapping updated for the renamed constant. |
| `:feature:settings:api` | New `Appearance` NavKey. |
| `:feature:settings:impl` | New `AppearanceScreen` / `AppearanceViewModel` / `AppearanceContract`; `SettingsNavigationModule` registers the entry with `adaptiveDialog()`; Settings root gains the Appearance row. |

**Cross-cutting**

- **Localization** — new strings need `es-419` and `pt-BR` translations in the same commit, verified with the touched module's own `lint` task (`:app lint` does not catch `MissingTranslation` for feature modules).
- **Screenshot baselines** — new baselines for the Appearance screen; the Settings root baselines shift by one row and must be regenerated.
- **Bench flavor** — `FakeUserPreferencesRepository` in `src/bench` references `ThemePreference.SYSTEM` and must be updated, and the bench variant compiled locally before pushing (an enum rename breaks bench-flavor `when` exhaustiveness).
- **Device pass** — the cold-start no-flash behavior and the system-bar-icon contrast fix are only observable on a real device, across all three options with the OS in both light and dark mode.
