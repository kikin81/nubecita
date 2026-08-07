## Context

`NubecitaTheme` (in `:designsystem`) takes `darkTheme: Boolean = isSystemInDarkTheme()` and `dynamicColor: Boolean = true`, and `MainActivity` calls it with neither argument. The app is therefore permanently Material You, permanently OS-following. Feature code already respects the "never call `MaterialTheme` directly" rule, so there is exactly one place to change how the scheme is chosen.

The persistence half already exists and is dead code in practice:

- `:core:preferences` — `enum class ThemePreference { LIGHT, DARK, SYSTEM }`, plus `UserPreferencesRepository.themePreference: Flow<ThemePreference>` / `setThemePreference`, backed by DataStore. The stored value is the enum's `.name`, read through `runCatching { ThemePreference.valueOf(stored) }.getOrNull() ?: SYSTEM`.
- `:core:analytics` — a *separate* `enum class ThemePreference(val wire: String) { Light("light"), Dark("dark"), System("system") }` feeding the `theme_preference` GA4 user property.
- `:app` — `ProAnalyticsCoordinator` maps between the two.

`ThemePreference`'s KDoc names this change as its unblocking condition: *"the storage + flow exist now so the `theme_preference` analytics user property has a single source of truth that becomes accurate the moment a picker (and a `NubecitaTheme` read of this value) lands."* Nothing writes the preference today, so **every install's stored value is absent** — a fact this design leans on for the rename.

Constraints that shape the work:

- `:designsystem` must not gain a dependency on `:core:preferences` — the latter is a flavored module (`production` / `bench`), and pulling flavors into the design system would ripple through every UI module's variant matrix.
- `MainActivity` already injects `UserPreferencesRepository` and already holds the splash via `splashScreen.setKeepOnScreenCondition { sessionStateProvider.state.value is SessionState.Loading }`.
- Settings sub-pages must render with `NubecitaListGroup` / `NubecitaListItem` (epic `nubecita-1ow5`); `NubecitaListItem` already has a `selected: Boolean` single-select mode with correct one-node semantics.
- Every Settings sub-route is registered `@MainShell` with `adaptiveDialog()` metadata.

## Goals / Non-Goals

**Goals:**

- One mutually-exclusive theme list — `Dynamic` / `Light` / `Dark` — reachable from Settings and applied app-wide instantly.
- No wrong-theme frame on cold start.
- System bar icons stay legible when the app theme disagrees with the OS.
- A shape that a future `Custom` theme extends by appending, not by restructuring.
- Zero change to the existing `NubecitaTheme(darkTheme, dynamicColor)` contract, so ~30 `@Preview` and screenshot-test call sites stay untouched.

**Non-Goals:**

- Custom / user-authored palettes, AMOLED true-black, scheduled switching, per-screen themes, font-size or density controls.
- A separate "use wallpaper colors" switch. Rejected in favor of the single list (see D1).
- Widget theming — Glance widgets follow the launcher.
- Any change to the brand palette, contrast handling, or motion system.

## Decisions

### D1 — One mutually-exclusive list, not a mode + dynamic-color toggle

`AppTheme` is a single axis: `Dynamic`, `Light`, `Dark`. Choosing `Light` or `Dark` opts out of Material You.

*Alternative considered — a `System/Light/Dark` list plus a "use wallpaper colors" switch.* This is the more common Android pattern and it keeps every combination reachable, including "wallpaper colors + forced dark". It was rejected because the stated next step is **custom themes**, which are a third value on the *color-source* axis and would make a boolean toggle incoherent (a switch can't express "wallpaper / brand / my palette"). Collapsing to one axis now means `Custom` is one more list entry later, with no migration of a now-meaningless boolean.

*Accepted cost:* a user who wants wallpaper color *and* a forced dark app cannot have it. This is the concrete price of the simpler model, and it is worth re-opening only if users report it.

### D2 — Two enums: `ThemePreference` persists, `AppTheme` renders

`ThemePreference` stays in `:core:preferences` as the storage contract (no Compose dependency, serialized by name). `AppTheme` is new in `:designsystem` as the rendering contract. `:app` maps one to the other.

*Alternative considered — a single enum shared by both.* It would have to live in `:core:preferences` (the persistence owner) and be imported by `:designsystem`, adding a flavored dependency to the design system — the constraint above. Putting it in `:designsystem` instead and having `:core:preferences` depend on it inverts the layering.

The duplication is real but small, and it mirrors the pattern already in the codebase for `PrefsThemePreference` ↔ `AnalyticsThemePreference`. The mapping is a single `when` with exhaustive compile-time checking, so adding `Custom` later fails the build at every mapping site rather than silently defaulting.

### D3 — Rename `SYSTEM` to `DYNAMIC` rather than adding a fourth constant

`ThemePreference` becomes `{ DYNAMIC, LIGHT, DARK }` with `DYNAMIC` as the default.

This looks like a breaking persistence change and is not one: no code path has ever called `setThemePreference`, so no install has a stored value, so every install reads the default. Even in the impossible case of a stored `"SYSTEM"`, `runCatching { valueOf(...) }.getOrNull() ?: DYNAMIC` already yields the correct answer — the fallback *is* the migration. The spec pins this as a scenario so a future refactor can't quietly remove the `runCatching`.

*Alternative considered — keep `SYSTEM` and add `DYNAMIC` as a fourth constant.* This would leave a permanently unreachable constant plus a "which one do I write?" ambiguity in every mapping `when`, for no benefit given the empty storage.

### D4 — Gate the splash on the theme, don't block on it

The theme is read from DataStore, which is asynchronous; naively collecting it with a `Dynamic` initial value produces a visible light→dark flash on cold start for anyone who chose `Dark`.

The chosen fix extends the existing keep-on-screen predicate:

```
splashScreen.setKeepOnScreenCondition {
    sessionStateProvider.state.value is SessionState.Loading || appThemeState.value == null
}
```

The splash is already the mechanism for "we're not ready to draw yet", and the theme is a legitimate second not-ready condition. In practice the two resolve concurrently and the theme read is far faster than the session restore, so the added splash time is expected to be zero.

*Alternatives considered:* `runBlocking { themePreference.first() }` before `setContent` — blocks the main thread on disk I/O at the most latency-sensitive moment of startup, and startup TTID is a tracked metric here. Rendering nothing (or a bare surface) until the theme resolves — reintroduces a blank frame, which is what the splash exists to prevent.

### D5 — A `@Singleton` theme state holder, not an Activity-local `stateIn`

`AppThemeState` is a `@Singleton` in `:app` that does `userPreferences.themePreference.map(::toAppTheme).stateIn(appScope, SharingStarted.Eagerly, initialValue = null)`, injected into `MainActivity` alongside the repository it already injects.

Doing the `stateIn` inside the Activity would reset the flow to `null` on every Activity recreation — rotation, unfolding the Pixel Fold, a locale change — re-showing the splash gate or flashing on each one. A singleton started eagerly resolves once at process start and is non-null for every subsequent Activity. `@ApplicationScope CoroutineScope` already exists in `:core:common` for exactly this.

`null` is meaningful here and is not a "loading" wrapper at a VM→UI boundary — it is a process-lifetime latch read by an Activity, so it does not conflict with the MVI no-`Async<T>` rule.

### D6 — Derive system bar appearance from the resolved theme

`enableEdgeToEdge()`'s default `SystemBarStyle.auto(...)` decides icon polarity from the OS `uiMode` configuration. With app theme `Dark` on an OS in light mode, that draws dark icons over the app's dark status bar area — invisible icons. This is the single most likely user-visible defect in this change and is not caught by unit or screenshot tests.

`MainActivity` re-invokes `enableEdgeToEdge` with explicit `SystemBarStyle.dark(...)` / `SystemBarStyle.light(...)` chosen from the *resolved* dark-ness (i.e. `AppTheme.Dark`, or `AppTheme.Dynamic` while the OS is dark), from an effect keyed on that resolved boolean so a runtime theme change re-applies it. `enableEdgeToEdge` is idempotent and designed to be called again.

The existing `window.isNavigationBarContrastEnforced = false` line is preserved.

### D7 — `DYNAMIC` keeps the `"system"` analytics wire value

Adding a `"dynamic"` value would split the `theme_preference` GA4 dimension into pre- and post-release buckets that mean the same thing, breaking every existing report. The option was renamed, not redefined — `Dynamic` still means "follows the system". `:core:analytics` is untouched; only the `when` in `ProAnalyticsCoordinator` changes its left-hand side.

This is worth revisiting only if a future `Custom` theme makes "which color source" a question analytics needs to answer.

### D8 — A dedicated Appearance sub-page

`Appearance` is a new `NavKey` in `:feature:settings:api`, registered `@MainShell` with `adaptiveDialog()` in `SettingsNavigationModule`, pushed by a plain `navState.add(Appearance)` — identical to `FeedPreferences` and `ContentFilters`. The Settings root gets an Appearance row whose supporting text is the active theme's label.

*Alternatives considered:* an inline section on the Settings root — the root screen is already long (Pro, notifications, content & moderation, about, account) and custom themes would make it longer. A bottom sheet — a poor host for a growing list that will eventually want palette swatches and possibly a nested picker.

Showing the current value on the root row costs adding the theme to `SettingsState`, which is a one-field change and pays for itself by making the setting discoverable without a tap.

### D9 — A new overload, not new defaults on the existing function

`NubecitaTheme(appTheme: AppTheme, content: …)` resolves the pair and delegates to the existing `NubecitaTheme(darkTheme, dynamicColor, content)`. Roughly thirty `@Preview` and instrumentation call sites pass `dynamicColor = false` to get deterministic brand colors; changing the existing signature would churn all of them and risk moving committed screenshot baselines for reasons unrelated to this change. Delegation also guarantees the contrast and reduce-motion logic has exactly one implementation.

### D10 — Standard MVI for the Appearance screen

`AppearanceViewModel : MviViewModel<AppearanceState, AppearanceEvent, AppearanceEffect>` with `AppearanceState(selected: AppTheme)` — a flat, UI-ready field. No sealed status sum is warranted: there is no loading, error, or empty mode; the preference flow always emits. `AppearanceEffect` exists for error routing only if a write ever needs to surface one. Selection is `viewModelScope.launch { userPreferences.setThemePreference(...) }`; the rendered selection comes from the repository flow, not from local optimistic state, so the list and the app can never disagree.

## Risks / Trade-offs

- **A theme flash on cold start slips through** → The splash gate is invisible to unit and screenshot tests. Verify on the plugged-in Pixel Fold: set `Dark`, put the OS in light mode, force-stop, cold start, and confirm the first content frame is dark. Repeat for `Light` on a dark OS.
- **Invisible status-bar icons (D6)** → Same device pass, checking the status bar specifically in all four theme × OS-mode disagreement combinations. This is the defect most likely to ship silently.
- **Enum rename breaks the bench flavor** → `core/preferences/src/bench/.../FakeUserPreferencesRepository.kt` references `ThemePreference.SYSTEM`, and bench-flavor `when` exhaustiveness is not covered by the default debug compile. Compile the bench variant locally before pushing.
- **`:core:preferences` tests don't run under the root `testDebugUnitTest`** → It is a flavored module. Verify with `jacocoTestReportAggregated`, which is what CI runs.
- **Screenshot baseline churn** → The Settings root gains a row, shifting its baselines; new Appearance baselines are needed. Regenerate deliberately and *look at* the diffs — a regenerated baseline matches itself and proves nothing.
- **Missing `es-419` / `pt-BR` translations** → New strings ship in the same commit as their translations, verified with `:feature:settings:impl`'s own `lint` task (`:app lint` does not surface `MissingTranslation` for feature modules).
- **A theme switch recomposes the entire tree** → Unavoidable and correct: the color scheme is a `CompositionLocal` at the root. It is a one-off on an explicit user action, not a scroll-path cost, so it does not threaten the 120hz requirement.
- **"Wallpaper color + forced dark" is unreachable** → The accepted cost of D1. Revisit only on user feedback, and if revisited, prefer adding it as a list entry over reintroducing a second axis.

## Migration Plan

No data migration. The rename is safe because the preference has never been written (D3), and the repository's existing unrecognized-value fallback covers the theoretical case. No feature flag or staged rollout: the default is `Dynamic`, which is byte-for-byte the current behavior, so an install that never opens the Appearance screen sees no change. Rollback is a straight revert.

## Open Questions

- Should the `Dynamic` row hide or disable itself on Android 11 and earlier, where wallpaper-derived color is unavailable and `Dynamic` degrades to "brand palette following the OS"? Leaning **no** — the option is still meaningful (it is the only one that follows the OS) and hiding it would make the list device-dependent. The supporting text could be varied by API level if this proves confusing.
- Does the Appearance screen eventually want a live preview swatch per option? Not needed for three options where the app itself repaints instantly, but it becomes the obvious affordance once custom themes land.
