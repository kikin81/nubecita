## Why

The Settings screen today is a one-screen stub (`SettingsStubScreen`) with a Sign Out button and a version row. It's the most-frequently-cited "this feels unfinished" surface during dogfooding — there's no theme picker, no notification preferences, no content-warning defaults, no entry to OS-level affordances (default link handler, system notification settings), and no path to other-account or sign-out-everywhere actions. The Settings epic (`nubecita-37to`) gathers this work; this change scaffolds the screen's foundation and the section roster that fills it.

Now is the right time because (a) the deep-link epic just landed `autoVerify=false` for bsky.app handlers, making the "Make Nubecita default" affordance (`nubecita-ajty`) discoverable only through Settings, (b) the module-graduation chore (`nubecita-77l`) needs the screen to have its final shape before the move pays off, and (c) the M3 Expressive `SegmentedListItem` pattern is already in use in `:feature:chats:impl` and is ready to power the section cards.

## What Changes

- Rebuild the Settings screen UI as a Google Play–style sheet: identity header (handle + display name + avatar + "Manage your Bluesky account" pill) + "Switch account" placeholder row + grouped section cards rendered via M3 Expressive `SegmentedListItem`.
- Make the screen adaptive: phone keeps the existing full-screen route; tablet renders the same content as a centered modal with scrim, driven by `WindowSizeClass` (or the project's Compose `MediaQuery` adaptive helpers).
- Reframe `nubecita-ajty` to land as a row inside an "Open links & sharing" section card rather than a standalone composable.
- After ajty + shell ship, perform `nubecita-77l`: graduate the screen to a new `:feature:settings:impl` module with `:feature:settings:api` exposing the `Settings` NavKey. The shell already has its final shape, so 77l is a pure move + rename.
- Post-graduation, add six section cards as separate tasks (one per section):
  - **Display** — theme picker (System / Light / Dark), persisted via DataStore.
  - **Notifications** — push notification preferences (likes / follows / mentions / reposts / quotes / replies) + "Open system notification settings" OS deep-link.
  - **Content & moderation** — content-warning defaults (adult / sexual / graphic; hide / warn / show) + "Muted words" web-forwarding row + "Blocked accounts" web-forwarding row.
  - **Account** — sign-out (refactor, kept), sign-out-from-all-devices (new), "App passwords" web-forwarding row.
  - **About** — version row (migrated from current stub) + open-source licenses + Terms of service web link + Privacy policy web link.
  - **Data usage** — Data saver master toggle + Autoplay video picker (Always / Wi-Fi only / Never) + Image quality picker (High / Medium / Low).

## Capabilities

### New Capabilities

- `feature-settings`: the Settings screen capability — adaptive shell (phone full-screen vs tablet modal), identity header, grouped `SegmentedListItem` sections, the per-section row roster, and DataStore-backed persistence of in-app preferences (theme, push prefs, content-warning defaults, data-saver / autoplay / image-quality). Owns the `Settings` NavKey and the `@MainShell EntryProviderInstaller` registration for the screen.

### Modified Capabilities

- None at this stage. Consumer-side enforcement of the persisted preferences (e.g. `feature-feed-video` honoring the autoplay-on-cellular policy, theme propagation through `:designsystem`, content-warning filtering in feeds) is real follow-on work but belongs in delta changes scoped to those consumers — each one needs its own scenarios. This change establishes the storage and surface; downstream specs adopt the values in their own changes.

## Impact

- **Replaces**: `SettingsStubScreen` (and its `SettingsStubContract` / `SettingsStubViewModel` / screenshot + instrumentation tests) under `:feature:profile:impl`. The shell rebuild lands there first, then `nubecita-77l` moves it to `:feature:settings:impl` with the rename `SettingsStub*` → `Settings*`.
- **Folds**: `nubecita-lq9t.3.6` (closed; version row already shipped) — the version row migrates into the new About section card as part of the shell rebuild.
- **New module**: `:feature:settings:api` (NavKey only) + `:feature:settings:impl` (screen + ViewModels + section composables + Hilt modules). Created during `nubecita-77l`; the shell ships into `:feature:profile:impl` first.
- **Possible new module**: `:core:preferences` — DataStore-backed user-preferences store. If a similar capability already exists in `:core:auth` or another module, prefer extending it; otherwise file as part of the Display task and let it generalize as more sections need persistence.
- **Cross-feature dependencies** (called out but NOT in scope for this change):
  - `atproto-kotlin` notification lexicon is incomplete as of this change — the SDK exposes `getUnreadCount`, `listNotifications`, and `updateSeen` but `app.bsky.notification.putPreferences` is missing. The Notifications-section task should verify against the SDK's generated sources and file an upstream issue at `kikin81/atproto-kotlin` before scoping.
  - Sign-out-from-all-devices needs a PDS-side OAuth-session revocation helper that may also need atproto-kotlin work.
  - Coil is not yet wired; the image-quality row in Data usage ships UI-only until then.
- **Permissions / system**: OS deep-link rows fire `Intent` actions (`ACTION_APP_NOTIFICATION_SETTINGS`, `ACTION_APP_OPEN_BY_DEFAULT_SETTINGS`, `ACTION_APPLICATION_DETAILS_SETTINGS`). No new manifest declarations required.
- **Out of scope for this epic** (file as siblings or own epics):
  - Full multi-account picker (its own epic when auth supports it; shell ships an inert placeholder).
  - Full muted-words editor / mute-list management UI (web-forwarding row only for now).
  - Accessibility-specific surfaces beyond Compose defaults.
  - Dev / diagnostics panel (user explicitly deselected this axis).
  - Module-architecture spec (the `nubecita-77l` graduation is tracked under the Profile epic, not this one).

## Non-goals

- Not redesigning the Profile screen — the entry point to Settings stays the same.
- Not introducing a global preferences-aggregator capability — each section owns its slice of preferences and exposes a focused repository.
- Not introducing a settings-search affordance — sections are short enough to scan.
- Not introducing an `Async<T>` / `Result<T>` wrapper at the VM→UI boundary — sticks to the project's flat-`UiState` MVI rule per CLAUDE.md.
