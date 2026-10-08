## ADDED Requirements

### Requirement: `:feature:settings:api` exposes the `Settings` NavKey

The system SHALL provide a `:feature:settings:api` Android library module exposing `@Serializable Settings : NavKey` and any sub-route NavKeys. The `:api` module MUST NOT depend on Hilt, Compose, Coil, or `:feature:settings:impl`, depending only on `androidx.navigation3.runtime` and `kotlinx.serialization.json`.

#### Scenario: Caller pushes Settings onto the inner nav stack without `:impl` dependency

- **WHEN** a feature module needs to navigate to Settings and declares `implementation(project(":feature:settings:api"))` only
- **THEN** the module compiles successfully and can construct `Settings` and push it onto `LocalMainShellNavState.current` with `add(Settings)`

#### Scenario: Settings NavKey survives process death

- **WHEN** the user navigates to Settings, the OS recreates the activity, and the saved `NavBackStack` is restored
- **THEN** the user lands back on the Settings screen at the same scroll position, restored via NavKey serialization

### Requirement: `:feature:settings:impl` registers a `@MainShell EntryProviderInstaller` for the `Settings` NavKey

The system SHALL provide a `:feature:settings:impl` module applying `nubecita.android.feature`. The module SHALL provide a Hilt `@MainShell EntryProviderInstaller` binding that registers an entry for `Settings`.

#### Scenario: Settings sub-route renders inside MainShell

- **WHEN** any caller invokes `LocalMainShellNavState.current.add(Settings)` and `:feature:settings:impl` is present in the build
- **THEN** `MainShell`'s inner `NavDisplay` resolves the entry to the `:feature:settings:impl` provider and renders the Settings screen

#### Scenario: No `:feature:profile:impl` Settings provider after graduation

- **WHEN** `nubecita-77l` has shipped and the build is assembled
- **THEN** grepping `:feature:profile:impl` for `EntryProviderInstaller` returns no Settings-related providers; only `:feature:settings:impl` provides the `Settings` NavKey

### Requirement: Settings screen adapts shape to window size class

The Settings screen MUST render as a full-screen route with `TopAppBar` and back arrow below Medium width, and as a centered modal dialog (≤ 640dp width, ≤ 80% height) with scrim and close icon at or above Medium width, using `currentWindowAdaptiveInfoV2()`. The section list MUST scroll vertically via `Modifier.verticalScroll` or `LazyColumn` on both form factors.

#### Scenario: Phone width renders full-screen route

- **WHEN** `currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)` returns `false` (the device is below the Medium breakpoint, i.e. a typical phone) and the user navigates to Settings
- **THEN** the screen fills the available space inside `MainShell`, the back-arrow appears in the top-leading `TopAppBar` slot, and no scrim is visible

#### Scenario: Tablet width renders modal with scrim and bounded height

- **WHEN** `currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)` returns `true` (the device is at or above the Medium breakpoint — tablet, foldable, desktop, or large-screen ChromeOS) and the user navigates to Settings
- **THEN** the screen renders as a centered modal at ≤ 640dp width and ≤ 80% window height, with rounded corners and a scrim behind it; an X-close affordance appears in the top-trailing slot

#### Scenario: Section list scrolls inside the modal when it exceeds available height

- **WHEN** the device is a landscape Pixel Tablet and all seven sections are populated (Settings height > modal max height)
- **THEN** the section column scrolls inside the modal wrapper without clipping; the header and X-close remain pinned

#### Scenario: Resize across size-class boundary preserves state

- **WHEN** the user opens Settings on a foldable in folded mode (below the Medium breakpoint), scrolls partway through the section list, then unfolds the device crossing the Medium breakpoint
- **THEN** the wrapper switches from full-screen to modal, and the section-list scroll position, header state, and any partially-open picker dialog are preserved (state survives because it's hoisted into `SettingsViewModel`)

### Requirement: Identity header renders signed-in user's profile and a Manage-Account CTA

The Settings identity header MUST render four vertical, centered elements: user handle, circular avatar with camera badge, display name greeting ("Hi, <name>!"), and a pill button labeled "Manage your Bluesky account". Tapping the pill MUST open `https://bsky.app/settings` in a Custom Tab or browser. Tapping the camera badge is a no-op stub in v1.

#### Scenario: Header renders with full profile

- **WHEN** the signed-in user has a non-null handle, display name, and avatar URL
- **THEN** the header renders handle as the topmost text, the avatar with camera badge below it, the display-name greeting below the avatar, and the Manage-Account pill below the greeting

#### Scenario: Header renders with missing display name

- **WHEN** the signed-in user has no `displayName` set on their profile
- **THEN** the greeting renders as "Hi!" (without a name); the rest of the header is unchanged

#### Scenario: Manage-Account pill opens web settings

- **WHEN** the user taps the "Manage your Bluesky account" pill
- **THEN** the screen emits a `LaunchUri("https://bsky.app/settings")` effect; a Chrome Custom Tab opens on the system, returning to Settings on close

### Requirement: "Switch account" row renders as an inert placeholder

The Settings screen MUST render a single-row `SegmentedListItem` labeled "Switch account" below the identity header, showing the signed-in user's avatar and a chevron. Tapping the row MUST surface an informative placeholder affordance (such as a "Multi-account coming soon" snackbar) without navigating or mutating state.

#### Scenario: Switch-account row renders for any signed-in user

- **WHEN** the user opens Settings while signed in
- **THEN** the row appears with the signed-in user's avatar and a chevron; it is visually distinct from the identity header above it (rounded both top and bottom, since it's a single-row section)

#### Scenario: Tapping switch-account surfaces a coming-soon affordance

- **WHEN** the user taps the Switch-account row
- **THEN** a snackbar appears with "Multi-account coming soon"; no navigation occurs; no state change

### Requirement: Section cards render via M3 Expressive `SegmentedListItem`

Every section in Settings MUST render rows via `SegmentedListItem` using `ListItemDefaults.segmentedShapes(index, count)` and `segmentedColors()`. Each section MUST be wrapped in `Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap))`. Sections MAY display an optional `labelMedium` caption above the card.

#### Scenario: Section with three rows shapes corners correctly

- **WHEN** a section renders three rows
- **THEN** the first row has top-rounded corners only, the middle row is fully squared, the last row has bottom-rounded corners only — handled by `segmentedShapes(index, count)`

#### Scenario: Single-row section is fully rounded

- **WHEN** a section renders exactly one row (e.g. the "Switch account" placeholder)
- **THEN** the row's corners are rounded on all four sides

### Requirement: Settings screen renders sections in a canonical fixed order

The Settings screen MUST render sections in canonical order on both phone and tablet:
1. Open links & sharing
2. Display
3. Notifications
4. Content & moderation
5. Account
6. About
7. Data usage
Sections without rows MAY be hidden or rendered empty without displaying a stray caption.

#### Scenario: Empty section does not show a stray label

- **WHEN** a section is declared but contains zero rows (e.g. before its content task ships)
- **THEN** the section's caption label does NOT render (or renders only when the section has ≥ 1 row); the empty card itself MAY be omitted from the layout

#### Scenario: Sections render in canonical order

- **WHEN** the user scrolls through Settings on a fully-built shell with all seven sections containing rows
- **THEN** sections appear top-to-bottom in this order: Open links & sharing, Display, Notifications, Content & moderation, Account, About, Data usage

### Requirement: Sign Out moves from the shell footer into the Account section

The Sign Out affordance currently rendered as a standalone `Button` at the bottom of `SettingsStubScreen` MUST move into the Account section as an `ActionRow` (or equivalent destructive variant) inside a `SegmentedListItem`. The existing confirm-dialog flow, the snackbar error effect, and the auto-unmount on `SessionStateProvider` transition MUST be preserved unchanged.

#### Scenario: Sign Out row in Account section opens confirm dialog

- **WHEN** the user taps Sign Out inside the Account section
- **THEN** the same `AlertDialog` from `SettingsStubScreen` opens, with the same title, body, confirm button, and cancel button; tapping Confirm runs the same VM path that fires `SessionStateProvider`'s sign-out

#### Scenario: Sign Out error surfaces a snackbar

- **WHEN** the user confirms Sign Out and the sign-out path fails
- **THEN** the existing `ShowSignOutError` effect fires and a snackbar appears at the bottom of the Settings screen; the dialog dismisses

### Requirement: Version row migrates from the shell footer into the About section

The version row currently rendered as a `Text` above the Sign Out button MUST move into the About section as a `SegmentedListItem` (or read-only equivalent). The version string is computed by the existing `rememberAppVersionLabel` helper unchanged; the screenshot fixture (per `nubecita-lq9t.3.6`'s implementation) is reused with the section-card wrapping.

#### Scenario: Version row renders inside About section

- **WHEN** the user scrolls to the About section
- **THEN** a row labeled "Version" with the value `<versionName> (<versionCode>)` appears inside the section card

### Requirement: Local preferences persist via DataStore

Device-scoped preferences (theme, data saver, autoplay video, image quality) MUST persist via `androidx.datastore`. Preference keys MUST survive process death and force-stop, clear on app data wipe, and expose a reactive `Flow` to consumers. Each preference MUST have a single owning writer in Settings.

#### Scenario: Theme persists across process death

- **WHEN** the user changes theme to Dark, the OS kills the process, and the user reopens the app
- **THEN** the app launches in Dark mode without flashing the default theme on cold start

#### Scenario: Data saver toggle survives force-stop

- **WHEN** the user enables Data saver, force-stops the app from system app info, and reopens the app
- **THEN** Data saver is still enabled in Settings on next open

### Requirement: Server-stored preferences persist via atproto putPreferences with optimistic UI updates and rollback on failure

Account-scoped preferences (push notifications, content-warning defaults) MUST persist via atproto XRPC (`putPreferences`), using a local DataStore read-cache. Writes MUST apply optimistically to `UiState` and snapshot the previous value; on XRPC failure, the VM MUST revert to the snapshot and emit an error snackbar effect.

#### Scenario: Content-warning default reflects across devices

- **WHEN** the user changes the adult-content default from Hide to Warn on device A
- **THEN** signing in on device B and opening Settings shows the same value (Warn) after the server resolves
#### Scenario: Optimistic update on successful write

- **WHEN** the user toggles a content-warning default from Hide to Warn and the network is healthy
- **THEN** the row's selected value flips to Warn immediately (before the XRPC completes); on successful response the local read-cache writes Warn and the row stays at Warn

#### Scenario: Optimistic update rolls back on failure

- **WHEN** the user toggles a content-warning default from Hide to Warn and the XRPC call fails (network error, server reject, or atproto-kotlin lexicon missing)
- **THEN** the row's selected value reverts to Hide and a snackbar surfaces explaining the failure; the local read-cache is unchanged

#### Scenario: Push-prefs UI renders coming-soon when lexicon is missing

- **WHEN** the user taps a push-notification preference row and the atproto-kotlin lexicon does not yet expose `putPreferences`
- **THEN** a snackbar surfaces "Coming soon" and no XRPC call is attempted; the local UI does not appear changed

### Requirement: OS-deep-link rows survive process death while the user is in system settings

Rows triggering OS settings intents MUST survive activity destruction while in system settings. `SettingsViewModel` MUST use `SavedStateHandle` to record a transient flag before launching system intents. On resume after process death, the VM MUST read the flag, re-evaluate system settings state, update `UiState`, and clear the flag.

#### Scenario: Default-handler row reflects post-deep-link change after process death

- **WHEN** the user taps the default-handler row, is sent to `ACTION_APP_OPEN_BY_DEFAULT_SETTINGS`, toggles Nubecita on, the Activity is destroyed under memory pressure, and the user returns to Settings
- **THEN** the default-handler row renders the "Bluesky links open in Nubecita ✓" state, not the "Make Nubecita the default" prompt; the `awaitingSystemSettingsReturn` flag is cleared from `SavedStateHandle`

#### Scenario: No double-detect when Activity survives

- **WHEN** the user taps an OS-deep-link row and the Activity survives the round-trip (no process death)
- **THEN** the existing `LifecycleResumeEffect` re-runs detection once on `RESUMED`; the `SavedStateHandle` flag-based path does not fire a redundant second detection
