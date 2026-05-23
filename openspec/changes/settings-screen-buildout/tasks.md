## 1. Pre-77l: ajty (existing bd `nubecita-ajty`) reframed for SegmentedListItem

- [ ] 1.1 Confirm ajty implementation lands as a `SegmentedListItem` (single-row "Open links & sharing" section card) rather than a standalone row in `SettingsStubScreen`
- [ ] 1.2 Detection logic (`PackageManager.queryIntentActivities` + `MATCH_DEFAULT_ONLY`) per ajty's existing bd description; row state reflects current default-handler status
- [ ] 1.3 OS deep-link intent (`ACTION_APP_OPEN_BY_DEFAULT_SETTINGS` API 31+, `ACTION_APPLICATION_DETAILS_SETTINGS` fallback)
- [ ] 1.4 `LifecycleResumeEffect` re-queries default-handler status on screen resume
- [ ] 1.4a Process-death survival: ViewModel writes an `awaitingSystemSettingsReturn` flag into `SavedStateHandle` before firing the intent; on `RESUMED` after process death, the flag triggers a re-detect and is cleared. Covers OEM (Samsung / MIUI) Activity teardown while user is in system settings.
- [ ] 1.5 Unit tests for detection logic (matrix: is-default / is-not-default / OEM-quirky returns)
- [ ] 1.6 Compose `@Preview` + `@PreviewTest` for both states; screenshot baselines committed via `update-baselines` label
- [ ] 1.7 Instrumentation test in `:feature:profile:impl` covering tap → intent fired (with intent stubbing); PR carries `run-instrumented` label

## 2. Settings shell rebuild (new bd child, files into `:feature:profile:impl` for now)

- [ ] 2.1 Introduce shared rendering primitives: `SettingsSection(label, rows)` composable + `SettingsRow` sealed sum (`ActionRow`, `ToggleRow`, `PickerRow`, `LinkRow`) inside `:feature:profile:impl/.../ui/settings/` (relocates with 77l)
- [ ] 2.2 Implement `SettingsHeader` composable: handle (Text) + avatar (with camera badge no-op) + display-name greeting + "Manage your Bluesky account" pill (`TextButton` styled, fires `LaunchUri` effect)
- [ ] 2.3 Implement `SwitchAccountRow` as inert single-row section (`SegmentedListItem` with avatar leading + chevron trailing; tap fires "Coming soon" snackbar effect)
- [ ] 2.4 Replace `SettingsStubContent` with new shell layout: header + SwitchAccountRow + section column rendering all seven section slots (most empty for now; ajty fills "Open links & sharing"; Sign Out fills Account; version fills About)
- [ ] 2.5 Move Sign Out from the standalone `Button` into an `ActionRow` inside the Account section card; preserve confirm dialog, snackbar error effect, and SessionStateProvider auto-unmount behavior
- [ ] 2.6 Move version row into the About section card; reuse `rememberAppVersionLabel`; ensure existing test fixture (per lq9t.3.6) passes
- [ ] 2.7 Add window-size-class evaluation at screen root via `currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)` (the project-wide pattern — see `app/.../shell/MainShell.kt` and `feature/composer/impl/.../internal/ComposerDiscardDialog.kt`). Below Medium = `Scaffold` + `TopAppBar` (back arrow); at/above Medium = modal `Surface` over scrim, ≤640dp max width, ≤80% window height max, rounded corners, X-close in top-trailing
- [ ] 2.7a Wrap the section column in `Modifier.verticalScroll(rememberScrollState())` (or use `LazyColumn`) so the section list scrolls inside the wrapper on both shapes; verify a Pixel Tablet landscape preview with all sections populated does not clip
- [ ] 2.8 Extend `SettingsStubViewModel` (rename pending in 77l) to add header data state (handle, display name, avatar URL), session-derived; add effects: `LaunchUri`, `ShowSnackbar`, `LaunchSystemSettingsIntent`
- [ ] 2.8a Inject `SavedStateHandle` into the VM via Hilt; add the `awaitingSystemSettingsReturn` flag plumbing so OS-deep-link rows (starting with ajty) survive process death — covers the shared infrastructure for tasks 4+ that fire system intents
- [ ] 2.9 Unit tests for the new VM state-shape, effects, and dialog/snackbar flows
- [ ] 2.10 `@Preview` + `@PreviewTest` for phone shape and tablet shape (modal) — apply `@PreviewNubecitaScreenPreviews` (defined in `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/preview/PreviewNubecitaScreenPreviews.kt`) for full-screen wrappers and plain `@Preview` for the SettingsHeader / SegmentedListItem atoms; ship both signed-in and missing-display-name fixtures
- [ ] 2.11 Drop old `SettingsStubScreenScreenshotTest` baselines; commit new ones via `update-baselines` label
- [ ] 2.12 Instrumentation test: tap each section's example row → expected effect fires; resize foldable simulator across size-class boundary → state preserved. PR carries `run-instrumented` label

## 3. Module graduation (`nubecita-77l`)

- [ ] 3.1 Create `:feature:settings:api` module applying the `nubecita.android.library` convention plugin; declare `@Serializable data object Settings : NavKey`
- [ ] 3.2 Create `:feature:settings:impl` module applying the `nubecita.android.feature` convention plugin; depends on `:feature:settings:api`, `:designsystem`, `:core:common:navigation`, session repos, etc.
- [ ] 3.3 Move screen + ViewModel + contract + ui/ + section row composables from `:feature:profile:impl` to `:feature:settings:impl`; rename `SettingsStub*` → `Settings*` (Contract, ViewModel, Screen, Content, ViewState, Status, Event, Effect)
- [ ] 3.4 Move the `@MainShell EntryProviderInstaller` from `ProfileNavigationModule.kt` (or wherever it lives) into a new `SettingsNavigationModule` in `:feature:settings:impl`; remove the `:feature:profile:impl` provider
- [ ] 3.5 Update `:feature:profile:api/Settings.kt` KDoc per `nubecita-77l`'s description (remove "graduates to its own module" note); move the `Settings` NavKey type to `:feature:settings:api` and add a transitional re-export OR force consumers to update their import in this same PR (recommended: force consumers — cleaner diff at archive time)
- [ ] 3.6 Update `:app` Hilt module aggregations to wire the new `:feature:settings:impl`
- [ ] 3.7 Move all unit / screenshot / instrumentation tests for Settings into the new module; verify nothing references the old package
- [ ] 3.8 `./gradlew :feature:settings:impl:assembleDebug :app:assembleDebug` clean; verify no stale references to the old path
- [ ] 3.9 PR carries `run-instrumented` label since androidTest moved

## 4. Post-77l: Display section + theme picker (bd child)

- [ ] 4.1 Survey: does `:core:preferences` exist? Inspect `:core:auth` and other core modules. If not, scope creating it as part of this task or split into a precursor task
- [ ] 4.2 Implement `ThemeRepository` (read + write theme preference: System / Light / Dark) backed by DataStore Preferences; the read `Flow` runs on `Dispatchers.IO`
- [ ] 4.3 Introduce `LocalAppTheme: ProvidableCompositionLocal<AppTheme>` in `:designsystem`; wire `ThemeRepository` read into `MainActivity` via `collectAsStateWithLifecycle`, provide it once at the root via `CompositionLocalProvider(LocalAppTheme provides ...)` so sub-trees pull theme via `LocalAppTheme.current` rather than parameter-drilling
- [ ] 4.4 Add `Display` section to the section column with a single `PickerRow` for "Theme" (current value: System / Light / Dark); tapping opens a radio-button dialog
- [ ] 4.5 Extend `SettingsViewModel` to expose theme state + handle `ThemeChanged(choice)` event
- [ ] 4.6 Unit tests: VM state transitions, repository write/read, theme propagation
- [ ] 4.7 `@Preview` + `@PreviewTest` for Display section card with each choice highlighted
- [ ] 4.8 Instrumentation test: toggle theme → assert no Composable in feed recomposes > 1 time; theme persists across activity recreate

## 5. Post-77l: Notifications section (bd child)

- [ ] 5.1 Lexicon check: verify `app.bsky.notification.putPreferences` is generated in atproto-kotlin (`~/code/kikinlex/atproto-kotlin/lexicons/com/atproto/...`); if missing, file `gh issue create --repo kikin81/atproto-kotlin "Add notification putPreferences"` and decide split (UI-only ship + lexicon follow-up, or block on lexicon)
- [ ] 5.2 Implement `PushPreferencesRepository` (read via local DataStore cache, write via XRPC `putPreferences` with cache invalidation); covers likes / follows / mentions / reposts / quotes / replies booleans
- [ ] 5.2a Implement the optimistic-update pattern in the VM for each toggle: flip `UiState` immediately, snapshot prior value, fire XRPC, on failure revert + `UiEffect.ShowError(...)` snackbar, on success clear snapshot + write local cache
- [ ] 5.3 Add `Notifications` section to the section column with: (a) one master `ActionRow` opening a "Push notifications" sub-route screen (NavKey: `PushPreferences`) with the six toggles, or (b) inline the six toggles directly in the section card. Decide based on visual density once mocked
- [ ] 5.4 Add `Open system notification settings` `LinkRow` firing `Intent(ACTION_APP_NOTIFICATION_SETTINGS)` with `EXTRA_APP_PACKAGE`; falls back to `ACTION_APPLICATION_DETAILS_SETTINGS` pre-API-26
- [ ] 5.5 Unit tests: VM state, repo, intent firing, lexicon-missing snackbar path
- [ ] 5.6 `@Preview` + `@PreviewTest` for Notifications section (with each toggle state) and the sub-route screen if chosen
- [ ] 5.7 Instrumentation test: tap system-settings row → intent fired (stubbed); toggle a push pref → repo write attempted

## 6. Post-77l: Content & moderation section (bd child)

- [ ] 6.1 Lexicon check: verify `app.bsky.actor.putPreferences` covers content-label preferences in atproto-kotlin; if missing, file upstream issue
- [ ] 6.2 Implement `ContentLabelsRepository` (read via local DataStore cache, write via `app.bsky.actor.putPreferences` with cache invalidation)
- [ ] 6.2a Implement the optimistic-update pattern in the VM for each picker: flip `UiState` immediately, snapshot prior value, fire XRPC, on failure revert + `UiEffect.ShowError(...)` snackbar, on success clear snapshot + write local cache
- [ ] 6.3 Add `Content & moderation` section with: (a) `PickerRow` for adult content default (Hide / Warn / Show); (b) `PickerRow` for sexual content; (c) `PickerRow` for graphic content; (d) `LinkRow` "Muted words" → web (bsky.app/settings/moderation); (e) `LinkRow` "Blocked accounts" → web
- [ ] 6.4 Picker dialog uses radio buttons; default selection reflects current server value
- [ ] 6.5 Unit tests: VM, repo, picker dialog flows, web-row launchUri
- [ ] 6.6 `@Preview` + `@PreviewTest` for the section card with each picker open and closed
- [ ] 6.7 Instrumentation test: change a content-label value → repo write attempted

## 7. Post-77l: Account section (bd child)

- [ ] 7.1 Lexicon check: verify atproto-kotlin exposes a per-PDS OAuth-session bulk-revoke helper for "sign out from all devices"; if missing, file upstream issue
- [ ] 7.2 Refactor the existing Sign Out `ActionRow` (already moved into Account section in task 2.5) — verify confirm-dialog + error-snackbar still work after section-card wrapping
- [ ] 7.3 Add `Sign out from all devices` `ActionRow` with destructive styling + a second confirm dialog wording around revoke-everywhere; ships with snackbar "Coming soon" if lexicon work isn't merged
- [ ] 7.4 Add `App passwords` `LinkRow` → web (bsky.app/settings/app-passwords)
- [ ] 7.5 Unit tests: confirm dialogs, success/error effect routing, revoke-everywhere ViewModel state
- [ ] 7.6 `@Preview` + `@PreviewTest` for the section card + both confirm dialogs
- [ ] 7.7 Instrumentation test: tap sign-out-everywhere → confirm → snackbar (lexicon TBD) or fired XRPC (lexicon present)

## 8. Post-77l: About section + version row migration (bd child)

- [ ] 8.1 Add `About` section caption + card
- [ ] 8.2 Move the version row (already in About section from task 2.6) — verify display and screenshot fixture still passes
- [ ] 8.3 Add `Open-source licenses` `ActionRow` — wire either Google Play services' `oss-licenses-plugin` or roll a simple license-viewer screen; ships behind its own NavKey if a screen is chosen
- [ ] 8.4 Add `Terms of service` `LinkRow` → web (target URL TBD; placeholder until product confirms)
- [ ] 8.5 Add `Privacy policy` `LinkRow` → web
- [ ] 8.6 Unit tests: VM, web-row launchUri effects
- [ ] 8.7 `@Preview` + `@PreviewTest` for the About section card
- [ ] 8.8 Instrumentation test: tap each row → expected effect fires

## 9. Post-77l: Data usage section (bd child)

- [ ] 9.1 Implement `DataSaverRepository` (read + write data-saver master toggle, autoplay policy, image-quality choice) backed by DataStore Preferences
- [ ] 9.2 Add `Data usage` section with: (a) `ToggleRow` for "Data saver" master; (b) `PickerRow` for "Autoplay videos" (Always / Wi-Fi only / Never); (c) `PickerRow` for "Image quality" (High / Medium / Low)
- [ ] 9.3 Wire autoplay policy into `:feature:feed-video` — read from `DataSaverRepository`; cellular vs Wi-Fi detection via `ConnectivityManager.getNetworkCapabilities`. Verify `android.permission.ACCESS_NETWORK_STATE` is already declared in the merged manifest (it's a normal-protection permission, granted at install); if absent, add it to the appropriate `:core` module's manifest. NOTE: this writes a delta to `feature-feed-video` spec; file a follow-up OpenSpec change if it adds requirements beyond the v1 behavior
- [ ] 9.4 Image-quality row ships UI-only until Coil lands; document the deferral inline with a TODO comment + bd issue link
- [ ] 9.5 Unit tests: VM, repo, autoplay policy logic, master-toggle-gates-children semantics if any
- [ ] 9.6 `@Preview` + `@PreviewTest` for Data usage section card + each picker open
- [ ] 9.7 Instrumentation test: toggle each row → repo write attempted; autoplay decision exercised end-to-end if feed-video integration is included in this PR (split if needed)

## 10. Epic close-out

- [ ] 10.1 Verify all sections render in the canonical order on phone and tablet
- [ ] 10.2 Verify the empty-section behavior — a not-yet-implemented section does not show a stray caption
- [ ] 10.3 Add an end-to-end screenshot test that captures the full Settings screen on phone and on tablet with all sections populated
- [ ] 10.4 Close bd `nubecita-37to` once all children land
- [ ] 10.5 Archive this OpenSpec change via `opsx:archive` once the last task PR merges
