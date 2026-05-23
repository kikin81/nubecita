## ADDED Requirements

### Requirement: `:feature:settings:api` exposes the `Settings` NavKey

The system SHALL ship a new `:feature:settings:api` Android library module exposing a single `@Serializable Settings : NavKey` data object (or empty data class) and any sub-route NavKeys filed by post-shell section tasks (e.g. a future `PushNotificationPreferences` deep-dive). The `:api` module MUST NOT depend on Hilt, Compose, Coil, or any `:feature:settings:impl` symbol; it depends only on `androidx.navigation3.runtime` (for `NavKey`) and `kotlinx.serialization.json`.

Until `nubecita-77l` ships, the `Settings` NavKey lives in `:feature:profile:api` (its current home). Once `nubecita-77l` lands, the NavKey moves to `:feature:settings:api` with no behavior change. Consumers of the NavKey (e.g. the Profile screen's "Settings" row) update their dependency at that time.

#### Scenario: Caller pushes Settings onto the inner nav stack without `:impl` dependency

- **WHEN** a feature module needs to navigate to Settings and declares `implementation(project(":feature:settings:api"))` only
- **THEN** the module compiles successfully and can construct `Settings` and push it onto `LocalMainShellNavState.current` with `add(Settings)`

#### Scenario: Settings NavKey survives process death

- **WHEN** the user navigates to Settings, the OS recreates the activity, and the saved `NavBackStack` is restored
- **THEN** the user lands back on the Settings screen at the same scroll position, restored via NavKey serialization

### Requirement: `:feature:settings:impl` registers a `@MainShell EntryProviderInstaller` for the `Settings` NavKey

The system SHALL ship a new `:feature:settings:impl` Android library module applying the `nubecita.android.feature` convention plugin. The module SHALL provide a Hilt `@Provides @IntoSet @MainShell EntryProviderInstaller` binding that registers an entry for the `Settings` NavKey.

Until `nubecita-77l` ships, this provider lives in `:feature:profile:impl` registered for the existing settings stub. Once `nubecita-77l` lands, the provider moves to `:feature:settings:impl` and the `:feature:profile:impl` provider is removed.

#### Scenario: Settings sub-route renders inside MainShell

- **WHEN** any caller invokes `LocalMainShellNavState.current.add(Settings)` and `:feature:settings:impl` is present in the build
- **THEN** `MainShell`'s inner `NavDisplay` resolves the entry to the `:feature:settings:impl` provider and renders the Settings screen

#### Scenario: No `:feature:profile:impl` Settings provider after graduation

- **WHEN** `nubecita-77l` has shipped and the build is assembled
- **THEN** grepping `:feature:profile:impl` for `EntryProviderInstaller` returns no Settings-related providers; only `:feature:settings:impl` provides the `Settings` NavKey

### Requirement: Settings screen adapts shape to window size class

The Settings screen MUST render as a full-screen route below the Medium width breakpoint and as a centered modal with scrim at or above the Medium width breakpoint, driven by `currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)` evaluated at the screen root (the project-wide adaptive pattern; `WindowSizeClass` from `androidx.window.core.layout`). Both shapes MUST render the same identity-header + section-roster content tree below the wrapper; only the wrapping container differs.

The phone shape (below the Medium breakpoint) uses a `Scaffold` with a `TopAppBar` displaying the screen title and a back-arrow navigation icon. The tablet / foldable / desktop shape (at or above the Medium breakpoint) uses a modal `Surface` (or `Dialog`) centered over a scrim, with a maximum width of approximately 640dp, a maximum height of approximately 80% of the window height (so the modal never reaches the system bars on tall tablets), rounded corners on all four sides, and an `IconButton(X)` in the top-trailing slot as the close affordance.

The section column inside the screen MUST wrap in `Modifier.verticalScroll(rememberScrollState())` (or be implemented as a `LazyColumn`) on both shapes, so the section list scrolls inside the wrapper rather than clipping. This is mandatory for the tablet modal — a Pixel Tablet in landscape with all seven sections exceeds the 80% height budget — and harmless on phone, where the existing scroll behavior is preserved.

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

The Settings screen's identity header MUST render four elements arranged vertically and centered horizontally: (1) the user's handle (e.g. `kikin.bsky.social`), (2) a large circular avatar (~80dp) with a small camera-icon badge in the bottom-trailing slot indicating tappable to change avatar, (3) the user's display name as a greeting (e.g. "Hi, Francisco!"), and (4) a `TextButton` styled as a pill labeled "Manage your Bluesky account".

Tapping the "Manage your Bluesky account" pill MUST open `https://bsky.app/settings` in a Chrome Custom Tab (falling back to the system browser if Custom Tabs are unavailable).

Tapping the avatar's camera badge is deferred to a follow-on task — the badge renders in the v1 shell but its tap action is a no-op (or surfaces a "Coming soon" snackbar). This requirement does NOT mandate avatar-editing functionality.

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

The Settings screen MUST render a single-row `SegmentedListItem` labeled "Switch account" immediately below the identity header, with a small avatar of the signed-in user and a chevron in the trailing slot.

Until multi-account auth ships, tapping the row MUST surface a non-disruptive affordance indicating the feature is coming (e.g. a snackbar with "Multi-account coming soon") and MUST NOT navigate or change state. The row's identity in the codebase MUST be discoverable enough that the multi-account epic's owner can wire it without spelunking — recommended naming is `SwitchAccountRow` with a clearly-named call site.

#### Scenario: Switch-account row renders for any signed-in user

- **WHEN** the user opens Settings while signed in
- **THEN** the row appears with the signed-in user's avatar and a chevron; it is visually distinct from the identity header above it (rounded both top and bottom, since it's a single-row section)

#### Scenario: Tapping switch-account surfaces a coming-soon affordance

- **WHEN** the user taps the Switch-account row
- **THEN** a snackbar appears with "Multi-account coming soon"; no navigation occurs; no state change

### Requirement: Section cards render via M3 Expressive `SegmentedListItem`

Every section in the Settings screen MUST render its rows via `androidx.compose.material3.SegmentedListItem` with `shapes = ListItemDefaults.segmentedShapes(index = position, count = rowCount)` and `colors = ListItemDefaults.segmentedColors(...)`. Each section MUST be wrapped in a `Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap))` so M3's segmented-gap baseline applies uniformly.

Sections MAY display an optional caption label rendered above the card using `MaterialTheme.typography.labelMedium` and `colorScheme.onSurfaceVariant`, matching the Google Play sheet pattern.

This requirement establishes the contract that future section tasks must follow; it does NOT mandate which section captions exist (the section roster is established in a separate requirement below).

#### Scenario: Section with three rows shapes corners correctly

- **WHEN** a section renders three rows
- **THEN** the first row has top-rounded corners only, the middle row is fully squared, the last row has bottom-rounded corners only — handled by `segmentedShapes(index, count)`

#### Scenario: Single-row section is fully rounded

- **WHEN** a section renders exactly one row (e.g. the "Switch account" placeholder)
- **THEN** the row's corners are rounded on all four sides

### Requirement: Settings screen renders sections in a canonical fixed order

The Settings screen MUST render sections in this top-to-bottom order, with empty / not-yet-implemented sections occupying their slot as empty cards (rendered as zero-height or hidden until at least one row is filled, at the implementer's discretion):

1. **Open links & sharing** — contains the default-Bluesky-link-handler row (`nubecita-ajty`) and future link-related rows.
2. **Display** — contains the theme picker; future row candidates: language, reduce motion, font size.
3. **Notifications** — push-notification preferences and a system-notification-settings shortcut.
4. **Content & moderation** — content-warning defaults; web-forwarding rows for muted words and blocked accounts.
5. **Account** — Sign Out, Sign Out from all devices, App passwords web row.
6. **About** — Version row (migrated from the current shell), Open-source licenses, Terms of service, Privacy policy.
7. **Data usage** — Data saver master toggle, Autoplay video picker, Image quality picker.

This order MUST be identical on phone and tablet. Section captions render in English in the v1 shell; localization is a sibling concern.

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

Preferences that are device-installation-scoped — theme, data saver master toggle, autoplay video policy, image quality — MUST persist via `androidx.datastore`. Each preference key MUST survive process death and app force-stop, MUST clear on app data clear, and MUST be readable as a `Flow` so consumers can observe changes reactively.

Each preference has exactly one writer (the Settings ViewModel route that owns its row) and may have many readers (e.g., the autoplay-policy reader lives in `:feature:feed-video`). The writer-reader contract is per-section and is detailed in each section task's own design.

#### Scenario: Theme persists across process death

- **WHEN** the user changes theme to Dark, the OS kills the process, and the user reopens the app
- **THEN** the app launches in Dark mode without flashing the default theme on cold start

#### Scenario: Data saver toggle survives force-stop

- **WHEN** the user enables Data saver, force-stops the app from system app info, and reopens the app
- **THEN** Data saver is still enabled in Settings on next open

### Requirement: Server-stored preferences persist via atproto putPreferences with optimistic UI updates and rollback on failure

Preferences that are account-scoped and must follow the user across devices — push notification preferences and content-warning defaults — MUST persist via the atproto `app.bsky.notification.putPreferences` and `app.bsky.actor.putPreferences` XRPC calls respectively.

Reading these preferences SHOULD use a local read-cache backed by DataStore so the UI doesn't flash empty on cold start while the network resolves; the cache is invalidated on every successful putPreferences and on app foreground after extended background.

Writing these preferences MUST follow the optimistic-update pattern:

1. On the user's toggle / choice change, the ViewModel updates `UiState` immediately so the UI reflects the new value.
2. The ViewModel snapshots the prior value into a per-preference pending-write slot.
3. The XRPC `putPreferences` call fires; on success, the pending-write slot clears and the local DataStore read-cache writes the confirmed value.
4. On failure (network error, lexicon-missing, server reject), the ViewModel reverts `UiState` to the snapshot and emits `UiEffect.ShowError(...)` so a snackbar surfaces explaining the failure. The local read-cache is NOT written.

Where the atproto-kotlin lexicon is incomplete (as of this change, the notification lexicon exposes only `getUnreadCount`, `listNotifications`, and `updateSeen`; `putPreferences` is missing), the affected section tasks MUST file upstream issues at `kikin81/atproto-kotlin` before implementation; the row UI MAY ship first with a snackbar "Coming soon" effect on tap if the lexicon work hasn't merged.

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

Rows that fire an OS-level settings intent (default-handler affordance, system-notification-settings shortcut, system app-info shortcut) MUST survive activity teardown during the user's time in the system settings page. Memory-pressured OEM skins (Samsung, MIUI) may destroy the underlying Activity while the user is away; on `RESUMED` the screen MUST re-run the relevant detection so the row's state reflects whatever the user actually changed in system settings.

The `SettingsViewModel` MUST receive `SavedStateHandle` via Hilt injection. Before firing an OS-deep-link intent, the ViewModel writes a transient flag (e.g. `awaitingSystemSettingsReturn = true` plus the row identifier) into the handle. On `RESUMED` after process death, the ViewModel reads the flag, re-runs the row-specific detection (e.g., `PackageManager.queryIntentActivities` for default-handler, `NotificationManagerCompat.areNotificationsEnabled()` for notification permission), updates `UiState` accordingly, and clears the flag.

#### Scenario: Default-handler row reflects post-deep-link change after process death

- **WHEN** the user taps the default-handler row, is sent to `ACTION_APP_OPEN_BY_DEFAULT_SETTINGS`, toggles Nubecita on, the Activity is destroyed under memory pressure, and the user returns to Settings
- **THEN** the default-handler row renders the "Bluesky links open in Nubecita ✓" state, not the "Make Nubecita the default" prompt; the `awaitingSystemSettingsReturn` flag is cleared from `SavedStateHandle`

#### Scenario: No double-detect when Activity survives

- **WHEN** the user taps an OS-deep-link row and the Activity survives the round-trip (no process death)
- **THEN** the existing `LifecycleResumeEffect` re-runs detection once on `RESUMED`; the `SavedStateHandle` flag-based path does not fire a redundant second detection
