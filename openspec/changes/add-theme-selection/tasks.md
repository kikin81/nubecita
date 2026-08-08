Tracked by beads epic **`nubecita-wqb8`**. Each task group below maps to one child issue and one PR:

| Group | bd issue | Blocked by |
|---|---|---|
| 1. Storage | `nubecita-wqb8.1` | — |
| 2. Design system | `nubecita-wqb8.2` | — |
| 3. Composition root | `nubecita-wqb8.3` | `.1`, `.2` |
| 4. Appearance screen | `nubecita-wqb8.4` | `.1`, `.2` |
| 5. Settings root row | `nubecita-wqb8.5` | `.4` |
| 6. Final verification | `nubecita-wqb8.6` | `.3`, `.5` |

## 1. Storage: rename the persisted option and re-default

- [x] 1.1 Rename `ThemePreference.SYSTEM` → `DYNAMIC` in `core/preferences/.../ThemePreference.kt` and rewrite the KDoc: drop the "no picker exists yet" note, state that `DYNAMIC` means wallpaper-derived color following the OS, and record that a future `Custom` theme appends to this enum. **Tests:** update `DefaultUserPreferencesRepositoryTest` — rename the default-value assertion and add a case asserting an unrecognized stored string (`"SYSTEM"`) falls back to `DYNAMIC`.
- [x] 1.2 Update `DefaultUserPreferencesRepository`'s `?: ThemePreference.SYSTEM` fallback to `DYNAMIC`, and update `core/preferences/src/bench/.../FakeUserPreferencesRepository.kt`. **Verify:** compile the bench variant locally (`./gradlew :core:preferences:compileBenchDebugKotlin`) — the default debug compile does not check bench-flavor `when` exhaustiveness.
- [x] 1.3 Update `ProAnalyticsCoordinator`'s `toAnalyticsPreference()` so `DYNAMIC` maps to `AnalyticsThemePreference.System` (wire `"system"` — unchanged, per design D7); `:core:analytics` itself is untouched. **Tests:** update `ProAnalyticsCoordinatorTest` for the renamed constant and assert `DYNAMIC` still emits `Theme(System)`.
- [x] 1.4 Update the remaining `ThemePreference.SYSTEM` references in `:feature:onboarding:impl` — both `androidTest/.../testing/FakeUserPreferencesRepository.kt` and the two inline fakes in `test/.../OnboardingViewModelTest.kt` (the latter were missed when this task was written; grep implementors repo-wide). **Verify:** `./gradlew jacocoTestReportAggregated` (the root `testDebugUnitTest` skips flavored modules like `:core:preferences`).

## 2. Design system: the `AppTheme` type and overload

- [x] 2.1 Add `enum class AppTheme { Dynamic, Light, Dark }` to `:designsystem` with KDoc naming it the rendering-side theme identity (distinct from `:core:preferences`'s storage-side `ThemePreference`, per design D2) and noting that `Custom` appends here.
- [x] 2.2 Add `@Composable fun NubecitaTheme(appTheme: AppTheme, content: @Composable () -> Unit)` in `Theme.kt` that resolves the `darkTheme` / `dynamicColor` pair per the spec's table and **delegates** to the existing two-argument overload — no duplicated contrast or motion branch. Leave the existing signature and its defaults untouched. **Tests:** unit test the `AppTheme` → `(darkTheme, dynamicColor)` resolution, including that `Dynamic` reads `isSystemInDarkTheme()` while `Light`/`Dark` ignore it.
- [x] 2.3 Add a `@Composable`-callable helper that reports whether an `AppTheme` resolves to dark (needed by task 3.3 for system bar polarity). **Tests:** the pure half (`forcedDarkTheme`) is unit-tested for all three values; the `?: isSystemInDarkTheme()` fallback is a Compose read, so it is covered by the screenshot pair in 2.4 rather than a JVM test — `Dynamic` under both OS modes, plus the two forced cases.
- [x] 2.4 Add `@Preview` variants exercising all three `AppTheme` values on a representative component, and commit their screenshot baselines. **Verify:** `./gradlew :designsystem:validateDebugScreenshotTest`, and hash the new baselines for uniqueness — three visually distinct themes must produce three distinct images.

## 3. Composition root: drive the theme and the system bars

- [ ] 3.1 Add a `@Singleton AppThemeState` in `:app` exposing `StateFlow<AppTheme?>` via `userPreferences.themePreference.map(::toAppTheme).stateIn(appScope, SharingStarted.Eagerly, null)`, injecting `@ApplicationScope CoroutineScope`. Include the `ThemePreference` → `AppTheme` mapping as an exhaustive `when`. **Tests:** unit test with a fake repository that the flow starts `null`, resolves to the mapped value, and re-emits on change.
- [ ] 3.2 Inject `AppThemeState` into `MainActivity`; extend the splash predicate to `sessionStateProvider.state.value is SessionState.Loading || appThemeState.value == null`, and change `setContent` to `NubecitaTheme(appTheme = <collected>) { … }`. Do **not** introduce a main-thread `runBlocking` read (design D4).
- [ ] 3.3 Re-apply `enableEdgeToEdge` with an explicit `SystemBarStyle.dark(...)` / `SystemBarStyle.light(...)` derived from whether the theme resolves to dark (`isDark`), from an effect keyed on that boolean so a runtime theme change re-applies it. Preserve the existing `isNavigationBarContrastEnforced = false` line and its comment.
- [ ] 3.4 **Device pass** on a physical foldable (a Pixel Fold; pick the target from `adb devices`): for each of `Dark`-on-light-OS and `Light`-on-dark-OS, force-stop, cold start, and confirm (a) the first content frame is the chosen theme with no flash and (b) status-bar icons are legible. Capture a screenshot per case, passing the device's **physical** display id to `screencap -d` — the default display 0 is not the inner screen.

## 4. Appearance screen

- [ ] 4.1 Add the `Appearance` `NavKey` to `:feature:settings:api`, with KDoc mirroring `FeedPreferences` (pushed from the Settings root; tagged `adaptiveDialog()`).
- [ ] 4.2 Add `AppearanceContract.kt` — `AppearanceState(selected: AppTheme)`, `AppearanceEvent.ThemeSelected(theme)`, and an `AppearanceEffect` for error routing. Flat state, no sealed status sum (design D10).
- [ ] 4.3 Add `AppearanceViewModel : MviViewModel<…>` observing `userPreferences.themePreference` into state and writing via `setThemePreference` in `viewModelScope.launch`; render selection from the repository flow, never from local optimistic state. **Tests:** JVM unit tests with `MainDispatcherExtension` + Turbine + MockK — initial state reflects the stored value, selecting writes through, and a repository re-emission updates state.
- [ ] 4.4 Add `AppearanceScreen.kt` rendering the three options via `NubecitaListGroup` / `NubecitaListItem(selected = …)` in fixed `Dynamic, Light, Dark` order, `Dynamic` carrying supporting text about following the system and wallpaper. `Scaffold(containerColor = MaterialTheme.colorScheme.surface)`. Add the screen's `strings.xml` keys (title, three labels, `Dynamic` supporting text) **with their `es-419` and `pt-BR` translations in this same commit**. **Verify:** `./gradlew :feature:settings:impl:lint` — `:app lint` does not surface `MissingTranslation` for feature modules.
- [ ] 4.5 Register the entry in `SettingsNavigationModule` as `@MainShell` with `adaptiveDialog()` metadata.
- [ ] 4.6 Add `@Preview` variants (each theme selected, light and dark) and commit screenshot baselines. **Verify:** `./gradlew :feature:settings:impl:validateDebugScreenshotTest`; confirm the selected-row indicator actually differs between baselines rather than pinning a missing indicator as correct.
- [ ] 4.7 Add an instrumentation test asserting exactly one row reports `selected = true` in semantics and that tapping another row moves the selection (follow the existing `ContentFiltersSemanticsInstrumentationTest` shape; requires the `run-instrumented` PR label to run in CI).

## 5. Settings root entry point

- [ ] 5.1 Add the current theme to `SettingsState` and have `SettingsViewModel` observe `userPreferences.themePreference`. **Tests:** unit-test that the state field tracks repository emissions.
- [ ] 5.2 Add the Appearance row to `SettingsScreen`'s section list with the active theme's localized label as supporting text, pushing via `onNavigateTo(Appearance)`. Place it deliberately within the canonical section order. Add the row-label string with its `es-419` / `pt-BR` translations **in this same commit**, and re-run `:feature:settings:impl:lint`.
- [ ] 5.3 Regenerate the Settings root screenshot baselines (the added row shifts them) and **inspect the diffs** rather than accepting them wholesale.

## 6. Final verification

- [ ] 6.1 Run the full local gate: `./gradlew spotlessCheck lint checkSortDependencies`, `./gradlew jacocoTestReportAggregated`, and the two `validateDebugScreenshotTest` tasks.
- [ ] 6.2 Run the `compose-expert` skill over the diff (it adds `@Composable` lines, so the Compose review gate applies) and address its findings.
- [ ] 6.3 Bench-flavor smoke: `./gradlew :app:assembleBenchDebug`, install, open Settings → Appearance, toggle to `Dark`, and confirm the persisted change is visible in a screenshot — a missed tap yields a false pass.
