# Tasks — add-settings-about-section (bd nubecita-jkg8)

> **Prerequisite:** bd `nubecita-bq29` (coalescing adaptive-dialog scene) lands first as its own PR — it delivers the "single content-swapping dialog on tablet" behavior and tags `Settings` as a dialog. This feature only *consumes* it by tagging the new routes `adaptiveDialog()` (task 2.4).

## 1. aboutlibraries plugin wiring (do first — de-risks the beta)

- [ ] 1.1 Add `aboutlibraries` to `gradle/libs.versions.toml`: version `15.0.0-b03`, the `com.mikepenz.aboutlibraries.plugin` plugin alias, and the `aboutlibraries-compose` (+ `-core` if needed) library coordinates.
- [ ] 1.2 Apply the `aboutlibraries` plugin to `:feature:settings:impl/build.gradle.kts` and add the `aboutlibraries-compose` dependency.
- [ ] 1.3 Run `:app:assembleDebug` to verify the `15.0.0-b03` beta builds against current AGP/Kotlin/Compose and generates the library metadata. **If incompatible**, fall back to the latest stable aboutlibraries (and render via `aboutlibraries-core` per design D4 alternative b); record the chosen version in this file.

## 2. Navigation routes

- [ ] 2.1 Add `About : NavKey` and `AboutLicenses : NavKey` (`@Serializable data object`) to `:feature:settings:api`.
- [ ] 2.2 Register both as `@MainShell` `@IntoSet` `EntryProviderInstaller` entries in `SettingsNavigationModule`, wiring `onBack = { navState.removeLast() }` and `onNavigateTo = { navState.add(it) }` (mirror the existing `Settings` entry).
- [ ] 2.3 Add an "About" `SettingsRow.Action` to the Settings screen + a `SettingsEvent`/`SettingsEffect.NavigateTo(About)` path so tapping it pushes `About`.
- [ ] 2.4 Tag both new entries with `metadata = adaptiveDialog()` so they present full-screen on phone and swap content within the Settings dialog on tablet (relies on prerequisite bd `nubecita-bq29`; `Settings` itself is tagged there).

## 3. About screen + ViewModel

- [ ] 3.1 Add `:core:actors` dependency to `:feature:settings:impl`.
- [ ] 3.2 Define the static Special Thanks config (DID + blurb) for the 4 contributors and `data class ThanksRowUi(did, handle, displayName?, avatarUrl?, blurb)` in `:data:models` or `:feature:settings:impl` (per existing convention).
- [ ] 3.3 Implement `AboutViewModel` extending `MviViewModel<AboutState, AboutEvent, AboutEffect>`: hydrate each contributor via `ActorRepository.getActor(did)`, exposing flat `AboutState { thanks: ImmutableList<ThanksRowUi>, isLoadingThanks }`; per-row fallback on fetch failure. Effects: `NavigateTo`, `NavigateToProfile(did)`, `LaunchUri(url)`.
- [ ] 3.4 Implement `AboutScreen` (stateful) + stateless `AboutContent`: app name + version header (reuse the existing version label), "Source on GitHub" row (`LaunchUri("https://github.com/kikin81/nubecita")`), Special Thanks rows (live, tap → `NavigateToProfile`), and "Open source licenses" row (`NavigateTo(AboutLicenses)`). Collect effects once in the screen Composable; never read `LocalMainShellNavState` in the VM.

## 4. Open-source licenses screen

- [ ] 4.1 Implement `AboutLicensesScreen` using `LibrariesContainer` with the `libraryRow` slot rendering each library through a design-system row (`SettingsRow`-styled).
- [ ] 4.2 Tapping a library with a URL emits `LaunchUri`; wire the same Custom Tabs handler. Add a `TopAppBar`/back affordance consistent with other Settings sub-screens.

## 5. Tests

- [ ] 5.1 `AboutViewModel` unit tests (JUnit5 + Turbine + MockK): successful avatar hydration, per-row fallback on fetch failure, `tap → NavigateToProfile(did)` and `LaunchUri` effect emission.
- [ ] 5.2 Screenshot tests (`@PreviewTest`) for `AboutContent` (with thanks rows, light/dark) and the licenses row rendering, driven by static state (no Hilt). Commit baselines.

## 6. Verification

- [ ] 6.1 `./gradlew spotlessCheck :feature:settings:impl:lintProductionDebug :app:assembleDebug` green.
- [ ] 6.2 On-device (bench build): About row opens; GitHub link opens a Custom Tab; thanks rows show avatars and deep-link to profiles; licenses screen lists libraries and opens URLs; back navigation correct on phone + tablet.
- [ ] 6.3 Validate the OpenSpec change (`openspec validate add-settings-about-section --strict`) and update `tasks.md` checkboxes as work lands.
