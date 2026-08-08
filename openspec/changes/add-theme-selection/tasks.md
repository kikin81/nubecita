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

- [x] 3.1 Add a `@Singleton AppThemeState` in `:app` exposing `StateFlow<AppTheme?>` via `userPreferences.themePreference.map(::toAppTheme).stateIn(appScope, SharingStarted.Eagerly, null)`, injecting `@ApplicationScope CoroutineScope`. Include the `ThemePreference` → `AppTheme` mapping as an exhaustive `when`. **Tests:** unit test with a fake repository that the flow starts `null`, resolves to the mapped value, and re-emits on change.
- [x] 3.2 Inject `AppThemeState` into `MainActivity`; extend the splash predicate to `sessionStateProvider.state.value is SessionState.Loading || appThemeState.value == null`, and change `setContent` to `NubecitaTheme(appTheme = <collected>) { … }`. Do **not** introduce a main-thread `runBlocking` read (design D4).
- [x] 3.3 Re-apply `enableEdgeToEdge` with an explicit `SystemBarStyle.dark(...)` / `SystemBarStyle.light(...)` derived from whether the theme resolves to dark (`isDark`), from an effect keyed on that boolean so a runtime theme change re-applies it. Preserve the existing `isNavigationBarContrastEnforced = false` line and its comment.
- [x] 3.4a **Device pass, OS-following half.** `Dynamic` cold-started under OS light and OS dark: app follows the OS both ways, status-bar icons legible both ways, no flash, 793 ms to first frame. Run on the `Pixel_10_Pro` emulator — the only physical device attached had the **Play-store build** installed, and the bench flavor shares its `applicationId` with no `applicationIdSuffix`, so installing would have meant uninstalling the user's real signed-in app.
- [x] 3.4b **Device pass, forced-theme half.** The bench flavor's fake hard-codes `DYNAMIC` and there is no picker yet, so this was run against the **production** build's real DataStore with the preference seeded directly (hand-encoded `user_preferences.preferences_pb`, pushed via `run-as`). `Dark` on a light OS → app dark, status-bar icons white-on-dark and legible, brand palette (not dynamic). `Light` on a dark OS → app light, icons dark-on-light and legible. Both force-stopped + cold-started, no flash.
- [x] 3.4c **Real-DataStore splash gate.** Verified separately because the bench fake's `flowOf(DYNAMIC)` emits instantly and would mask a hang: on a fresh `pm clear` production install the splash clears and the app reaches Onboarding in 830 ms, so the `null` latch does resolve against a real, empty DataStore.
- [x] 3.4d **Activity-recreation survival (design D5).** Rotated with `Light` forced and the OS dark on the emulator: app stays light, no reset to `null`, no re-gate — the `@Singleton` outlives the Activity.
- [x] 3.4e **Pixel Fold (37201FDHS002UN) pass.** Real signed-in account, production build, real DataStore seeded to `DARK` with the OS in light mode: the inner display renders dark in the brand palette with legible status-bar icons, and stays dark across an inner-display rotation (Activity recreation). `screencap -d 0` returns a 79-byte stub — the **physical** display id is required (`-d 4619827677550801152` inner, `...153` outer).
- [ ] 3.4f **Fold/unfold display-switch transition** — still unverified. Driving `cmd device_state state 0` disconnects the device from ADB mid-transition, so the outer-display hand-off could not be captured; the device also locks on fold. Rotation covers the same Activity-recreation code path, so this is a gap in gesture coverage, not in the mechanism. Needs a physically present tester.

## 4. Appearance screen

- [x] 4.1 Add the `Appearance` `NavKey` to `:feature:settings:api`, with KDoc mirroring `FeedPreferences` (pushed from the Settings root; tagged `adaptiveDialog()`).
- [x] 4.2 Add `AppearanceContract.kt` — `AppearanceState(selected: ThemePreference)`, `AppearanceEvent.ThemeSelected(theme)`, and an `AppearanceEffect` for error routing. Flat state, no sealed status sum (design D10). **Deviation:** the state holds the persisted `ThemePreference`, not `AppTheme` as this task originally specified. The screen edits storage; `AppTheme` is the rendering identity derived from it in `:app`, and using it here would mean mapping back on write for no gain. It also lets the option list be `ThemePreference.entries`, whose declaration order is already the required display order.
- [x] 4.3 Add `AppearanceViewModel : MviViewModel<…>` observing `userPreferences.themePreference` into state and writing via `setThemePreference` in `viewModelScope.launch`; render selection from the repository flow, never from local optimistic state. **Tests:** JVM unit tests with `MainDispatcherExtension` + Turbine + MockK — initial state reflects the stored value, selecting writes through, and a repository re-emission updates state.
- [x] 4.4 Add `AppearanceScreen.kt` rendering the three options via `NubecitaListGroup` / `NubecitaListItem(selected = …)` in fixed `Dynamic, Light, Dark` order, `Dynamic` carrying supporting text about following the system and wallpaper. `Scaffold(containerColor = MaterialTheme.colorScheme.surface)`. Add the screen's `strings.xml` keys (title, three labels, `Dynamic` supporting text) **with their `es-419` and `pt-BR` translations in this same commit**. **Verify:** `./gradlew :feature:settings:impl:lint` — `:app lint` does not surface `MissingTranslation` for feature modules.
- [x] 4.5 Register the entry in `SettingsNavigationModule` as `@MainShell` with `adaptiveDialog()` metadata.
- [x] 4.6 Add `@Preview` variants (each theme selected, light and dark) and commit screenshot baselines. **Verify:** `./gradlew :feature:settings:impl:validateDebugScreenshotTest`; confirm the selected-row indicator actually differs between baselines rather than pinning a missing indicator as correct.
- [x] 4.7 Added `AppearanceSemanticsInstrumentationTest` — 5 cases covering `Role.RadioButton`, exactly-one-selected, the `selectableGroup()` collection context, a tap moving the selection, and a re-tap still emitting so the ViewModel-side guard is not untested dead code. **Run green on the `Pixel_10_Pro` emulator** (5/5); needs the `run-instrumented` PR label to also run in CI.

## 5. Settings root entry point

- [x] 5.1 Add the current theme to `SettingsState` and have `SettingsViewModel` observe `userPreferences.themePreference`. **Tests:** unit-test that the state field tracks repository emissions.
- [x] 5.2 Add the Appearance row to `SettingsScreen`'s section list with the active theme's localized label as supporting text, pushing via `onNavigateTo(Appearance)`. Place it deliberately within the canonical section order. Add the row-label string with its `es-419` / `pt-BR` translations **in this same commit**, and re-run `:feature:settings:impl:lint`.
- [x] 5.3 Regenerated the Settings root baselines — exactly the 10 root fixtures changed, nothing unrelated (checked, after `.2`'s clobbering incident). Inspected the diff: the new **Display** section renders between Nubecita Pro and Notifications, captioned with the active theme.

## 6. Final verification

- [ ] 6.1 Run the full local gate: `./gradlew spotlessCheck lint checkSortDependencies`, `./gradlew jacocoTestReportAggregated`, and the two `validateDebugScreenshotTest` tasks.
- [ ] 6.2 Run the `compose-expert` skill over the diff (it adds `@Composable` lines, so the Compose review gate applies) and address its findings.
- [x] 6.3 **Bench smoke — done in `.5`, and the fixture had to be fixed first.** `FakeUserPreferencesRepository.setThemePreference` was a **no-op** with a constant `flowOf(DYNAMIC)`, so this check could never have passed: tapping a theme in a bench build changed nothing. Made the fake stateful (in-memory `MutableStateFlow`, still starting at `DYNAMIC` so benchmark journeys are unaffected). Then ran the full journey on the emulator with the OS in **light** mode: Profile → gear → Settings → Appearance → tap `Dark`. The app repainted dark instantly without leaving the screen, status-bar icons went white and legible, and the Settings row re-captioned from `Dynamic` to `Dark`.

## 7. Theme glyph (`nubecita-wqb8.8`)

Follow-up to 5.2, which shipped the Appearance row icon-less because adding a glyph is a dedicated design-system change that owns its baseline regen.

- [x] 7.1 Confirm the codepoint from the **cached upstream's cmap** with fontTools rather than reading it off the website: `palette` = `U+E3B7`. Add `Palette("\uE3B7")` to `NubecitaIconName` in alphabetical order.
- [x] 7.2 Regenerate the subset font with `scripts/update_material_symbols.sh` (fontTools via a throwaway venv on `PATH`; pip is PEP-668-blocked under pyenv). Cache hit — no upstream re-fetch, which is what keeps the blast radius small.
- [x] 7.3 **Verify the blast radius instead of assuming it.** Diffed `getCoordinates` per shared codepoint between the committed font and the regenerated one: **51 shared glyphs, 0 drifted outlines, 1 added (`U+E3B7`), 0 removed.** So no existing baseline can legitimately move; only the icon showcase and the Settings rows that now draw an icon.
- [x] 7.4 Point the Settings Appearance row at `NubecitaIconName.Palette`, replacing `icon = null`.
- [ ] 7.5 Regenerate baselines **on CI via the `update-baselines` label**, not locally — macOS re-introduces the logomark vector drift, which would ride along in the commit and fail CI for an unrelated reason. Apply the label only after every visual change is pushed; the workflow fires on `labeled` only and force-fails if the branch moves under it.
