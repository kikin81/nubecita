## Context

Settings today is `SettingsStubScreen` in `:feature:profile:impl` — a centered "Coming soon" message plus a Sign Out button, a confirm dialog, and a version row (`rememberAppVersionLabel`). Wrapped in a `Scaffold` + `TopAppBar` with a back arrow, no section structure.

The capability needs to grow to a real Settings home with multiple sections, while:

- Reusing the M3 Expressive grouped-list pattern already shipped in `:feature:chats:impl/ui/ConvoListItem.kt` (`SegmentedListItem`, `ListItemDefaults.segmentedShapes(index, count)`, `Arrangement.spacedBy(ListItemDefaults.SegmentedGap)`, `ListItemDefaults.segmentedColors(...)`).
- Honoring the user's design constraint: Google Play–style sheet — phone keeps full-screen, tablet shows a centered modal.
- Sequencing module graduation (`nubecita-77l`) early in the epic so post-graduation work all lands in the new `:feature:settings:impl` module.
- Splitting per-section work into independent bd children that different sessions can pick up in parallel without stepping on each other.

Surrounding constraints from CLAUDE.md and project memory:

- MVI: flat `UiState`, sealed status sums for mutually-exclusive lifecycles, errors via `UiEffect`, no `Async<T>` wrapper at the VM→UI boundary, no MVI framework.
- Compose stability: list-typed state fields use `ImmutableList`; tab-internal navigation flows through `UiEffect` → `LocalMainShellNavState.current.add(...)` (ViewModels do not inject the nav state holder).
- Module conventions: `:feature:*:api` exposes NavKey types only; `:feature:*:impl` provides screens, ViewModels, and `@IntoSet @MainShell` (or `@OuterShell`) `EntryProviderInstaller` bindings.
- atproto-kotlin gaps: per memory, the notification lexicon is stale (`putPreferences` likely missing); chat lexicon has similar gaps. Settings tasks that touch server-side preferences MUST verify lexicon coverage before scoping and file upstream issues at `kikin81/atproto-kotlin` when missing.
- UI test coverage convention (per memory): every UI-touching task ships unit tests + previews + screenshot tests; tasks that also include android-instrumentation tests need the `run-instrumented` GitHub label on their PR.

## Goals / Non-Goals

**Goals:**

- Establish the Settings screen shell as the foundation that all post-77l section tasks can hang off without further architectural decisions.
- Make adaptive shape (phone full-screen vs tablet modal) a property of the screen, not of each section.
- Standardize section-card rendering on M3 Expressive `SegmentedListItem` so the visual identity is consistent across the section roster.
- Sequence work so that:
  - The shell + `nubecita-ajty` ship into `:feature:profile:impl` (current home).
  - `nubecita-77l` moves the screen to `:feature:settings:impl` while its UI is already in final shape — a pure rename, no UI churn.
  - Post-77l section tasks land into the new module independently.
- Keep each section task small enough for one session to ship — one section card, one repository (where applicable), screenshot + preview + unit-test coverage.

**Non-Goals:**

- Not building a settings-search affordance — sections are short enough to scan.
- Not introducing a global preferences-aggregator capability — each section owns a focused repository.
- Not redesigning the entry point from Profile — the existing chevron / row that opens Settings stays as-is.
- Not implementing multi-account beyond an inert placeholder row — multi-account auth is its own epic.
- Not implementing native mute/block management — both are web-forwarding rows for v1.
- Not introducing a `:core:preferences` module preemptively — it shows up only if/when the Display task's design proposes it (or extends an existing module).

## Decisions

### Decision 1: One `SettingsViewModel` for the screen, no per-section sub-VMs

The screen has one `SettingsViewModel` that owns:

- The header data (signed-in handle / display name / avatar — pulled from the existing session repo).
- Section-aware state where a row needs reactive feedback (e.g., theme picker's current choice, autoplay picker's current choice).
- Effects: `NavigateTo(target: NavKey)` for sub-routes, `OpenUrl(uri: String)` for web rows, `LaunchSystemSettings(intent: Intent)` for OS deep-links, `ShowError(...)` for snackbar.

Sub-routes that warrant their own screen (e.g., a future "Push notifications" deep-dive that goes beyond the master toggles) get their own NavKey + ViewModel, registered as separate `@MainShell` providers.

**Alternatives considered:**

- One VM per section. Rejected: Settings is one screen the user perceives as a unit. Per-section VMs add coordination overhead and split observation of shared state (e.g., the header re-renders on profile change regardless of which section the user is in).
- A central `SettingsAggregatorViewModel` that composes section-specific sub-state objects. Rejected as premature abstraction — three or four `Flow` collectors merged with `combine(...)` is the simplest possible aggregation.

### Decision 2: Adaptive shape via `WindowSizeClass` at the screen root

The screen Composable resolves `WindowSizeClass.computeCurrent(activity).widthSizeClass` (or the project's adaptive helpers from the `adaptive` skill) once at the screen root and chooses between two wrapper Composables:

- **Compact (phone)**: existing `Scaffold` + `TopAppBar` with back-arrow; content fills the screen.
- **Medium / Expanded (tablet, foldable, desktop)**: `Dialog` (or a custom modal `Surface` over a scrim) with an `IconButton(X)` in the top-trailing slot, fixed `maxWidth ≈ 640dp`, `maxHeight ≈ 80% of window height` (clamped so the modal never reaches the system bars on tall tablets and never clips on landscape foldables), centered.

The content tree itself (header + sections) is window-size-class–agnostic — it always renders inside the wrapper. To ensure neither shape clips when the section list grows past available height, the section column MUST wrap in `Modifier.verticalScroll(rememberScrollState())` (or a `LazyColumn` if sections become numerous). This is required for the tablet modal — a Pixel Tablet in landscape with all seven sections populated exceeds 640dp height — and harmless on phone, where the existing scroll behavior remains.

**Why not `BottomSheet`?** Bottom sheets feel transient; Settings is a destination users navigate to and back from. Both Google Play and Material 3 guidance use a centered modal for settings on tablet.

**Why not a list-detail two-pane on tablet?** Settings sub-routes (e.g., push-prefs deep-dive) would benefit, but the master Settings screen reads more naturally as a single-pane destination. Sub-routes can adopt list-detail later as their own decision.

### Decision 3: Sequence the shell BEFORE the module graduation (77l)

Order:

1. `nubecita-ajty` lands in `SettingsStubScreen` as a `SegmentedListItem` (single row inside an "Open links & sharing" section card — already pioneering the section pattern).
2. The shell rebuild rebuilds `SettingsStubScreen` to its final Google Play shape: identity header + section roster scaffolding. Sign Out and version row move into their target sections inside the shell. The current MVI contract (`SettingsStubState/Event/Effect`) survives the rebuild, just gains fields; the file is renamed only by 77l.
3. `nubecita-77l` performs the module move + rename — `SettingsStubScreen.kt` → `SettingsScreen.kt`, `:feature:profile:impl` → `:feature:settings:impl`. Because the UI is already in its final shape, this is a pure refactor with no behavior change.

**Alternative considered:** Do 77l first, then the shell. Rejected because: (a) the shell rebuild touches many of the same files 77l renames — coordinating two large concurrent diffs against the same files invites merge conflict, and (b) the shell is a UI design decision; doing it post-rename burns the new module's screenshot-test baselines twice.

### Decision 4: `SegmentedListItem` for every section row

Every section card is a `Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap))` containing `SegmentedListItem` children. Each child receives `shapes = ListItemDefaults.segmentedShapes(index = position, count = section.size)` and `colors = ListItemDefaults.segmentedColors(...)`.

The rendering helper for a section is a small composable:

```kotlin
@Composable
internal fun SettingsSection(
    label: String?,
    rows: ImmutableList<SettingsRow>,
    modifier: Modifier = Modifier,
) {
    if (label != null) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, ...)
    }
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        rows.forEachIndexed { index, row ->
            SettingsRowRender(row, index = index, count = rows.size)
        }
    }
}
```

`SettingsRow` is a sealed sum: `ActionRow` (icon + headline + intent-style tap), `ToggleRow` (icon + headline + `Switch`), `PickerRow` (icon + headline + current-value text, tap opens picker dialog), `LinkRow` (icon + headline + external-link badge). The shell change ships the rendering primitives; each section task fills `rows` with the appropriate variant.

**Why not let each section task invent its own rendering?** Without a shared primitive, sections will visually drift (different paddings, different leading-icon sizes, different ripple shapes). The shared `SegmentedListItem` is exactly what M3 Expressive solves.

### Decision 5a: Optimistic updates for server-stored preferences, with rollback on failure

Every server-stored preference write (push prefs, content-warning defaults, anything routed through `app.bsky.*.putPreferences`) MUST follow the optimistic-update pattern in the MVI contract:

1. On `ToggleChanged(...)` / `ChoiceChanged(...)` event, the ViewModel updates `UiState` immediately to the new value (UI flips instantly).
2. The ViewModel snapshots the prior value into a per-pref `pendingWrite: PendingPrefWrite?` field (or a sealed-status sum if multiple writes can be in flight; per CLAUDE.md's "sealed status sums for mutually-exclusive lifecycles" guidance).
3. The XRPC call fires; on success, `pendingWrite` clears and the local DataStore read-cache writes the confirmed value.
4. On failure (network, lexicon-missing, server reject), the ViewModel reverts `UiState` to the snapshot and emits `UiEffect.ShowError(message)` so a snackbar surfaces. The local cache is NOT written.

Local-only writes (DataStore-backed preferences — theme, data saver, autoplay, image quality) do not need this pattern because DataStore writes are local and effectively transactional; UI just reads the canonical `Flow`.

**Why not pessimistic writes (block UI on the XRPC round-trip)?** Settings toggles read as instantaneous; a 200–800ms round-trip is enough to break the perception. Optimistic updates with rollback are the standard mobile-app pattern (Bluesky's own app does this for the same prefs).

**Alternative considered:** A single `OptimisticWriteHelper<T>` abstraction in `:core:preferences`. Rejected as premature — the section count is small enough that each section task can inline the pattern with confidence; an abstraction can come later if duplication grows.

### Decision 5b: Local vs server-stored preferences

- **Local (DataStore)**: theme (System/Light/Dark), data saver master toggle, autoplay video policy, image-quality picker. These are device-installation-scoped — a tablet and a phone with the same account can legitimately want different settings.
- **Server (atproto `app.bsky.actor.putPreferences` / `app.bsky.notification.putPreferences`)**: content-warning defaults, push notification preferences, muted words (when native lands). These are account-scoped — should follow the user across devices.

Each preference has exactly one writer in the app. Each consumer reads from the canonical repo (e.g., `:feature:feed:impl` reads autoplay policy from a `DataSaverRepository`; the player layer reads it on each video binding).

The Display section task may file a `:core:preferences` module if it doesn't exist yet. Otherwise, sections add their own DataStore handle in `:core:preferences`.

### Decision 6: Section roster fixed-order on both phone and tablet

Order (top to bottom):

1. **Open links & sharing** — default Bluesky link handler (`ajty`) and future cousins.
2. **Display** — theme picker; future row candidates: language, reduce motion, font size.
3. **Notifications** — push prefs + system-notification-settings shortcut.
4. **Content & moderation** — content-warning defaults + muted-words / blocked-accounts web rows.
5. **Account** — sign out (refactored), sign out from all devices, app passwords web row.
6. **About** — version row (migrated from current shell), open-source licenses, terms, privacy.
7. **Data usage** — data saver toggle + autoplay picker + image-quality picker.

The order is opinionated and not configurable: most-frequently-touched at the top (links / display), most-rarely-touched at the bottom (about / data usage). A future "search Settings" affordance, if it ships, would relax this constraint.

## Risks / Trade-offs

- **Theme propagation must not parameter-drill or trigger feed recomposition.** Passing `currentTheme: AppTheme` as an arg through every Composable would tank stability and cause cascade recomposition. → Mitigation: introduce a `LocalAppTheme: ProvidableCompositionLocal<AppTheme>` (or extend the existing `:designsystem` theme function to do the same) and provide it once via `CompositionLocalProvider` at `MainActivity`'s root. The `ThemeRepository`'s read `Flow` runs on `Dispatchers.IO`; `collectAsStateWithLifecycle` at the root reads it and feeds the `CompositionLocalProvider`. Sub-trees pull theme via `LocalAppTheme.current` rather than args. The Display task's instrumentation test asserts that toggling theme does not cause any feed Composable to recompose more than once.
- **Deep-linking out to OS settings risks activity teardown.** Memory-pressured Samsung/MIUI Activities can be destroyed while the user is in the OS settings page (e.g., ajty's `ACTION_APP_OPEN_BY_DEFAULT_SETTINGS`). On return, the deep-link refresh signal (was-default vs is-default-now) would be lost. → Mitigation: every OS-deep-link ViewModel route writes a transient `awaitingSystemSettingsReturn: Boolean = true` flag into `SavedStateHandle` before firing the intent. On `RESUMED` after process death, the VM reads the flag, re-runs the relevant detection (e.g., default-handler query, notification-permission check), and clears the flag. `SettingsViewModel` therefore receives `SavedStateHandle` via Hilt injection. Per CLAUDE.md, `SavedStateHandle` is opt-in per screen and not baked into the base class.
- **`atproto-kotlin` lexicon gaps block server-preference tasks.** Notifications putPreferences is likely missing per memory; content-warning putPreferences needs verification. → Mitigation: each affected task starts with a lexicon check against `~/code/kikinlex` (per memory `reference_kikinlex_local_repo`); file upstream issues with `gh issue create --repo kikin81/atproto-kotlin` per memory `feedback_file_atproto_kotlin_issues`. Tasks split into "lexicon work" + "UI wiring" subtasks when needed.
- **DataStore + Compose theme propagation is non-trivial.** Theme changes must propagate across the whole `Activity` tree without recomposition jank. → Mitigation: theme is read once at the `MainActivity` root via `collectAsStateWithLifecycle` and passed down through the existing `:designsystem` theme function. The Display task ships an instrumentation test that toggles theme and asserts no Composable in the feed recomposes more than once.
- **Tablet modal vs full-screen behavior on resize.** A tablet that resizes mid-session (foldable unfolds) may cross the size-class boundary. → Mitigation: the wrapper composable re-evaluates on every recomposition; the underlying screen state survives because it's hoisted into the `SettingsViewModel`. No special handling needed; screenshot tests cover both shapes.
- **Sign-out-from-all-devices may require atproto-kotlin work.** OAuth-session revocation isn't trivial to do client-side. → Mitigation: the Account task scopes the lexicon check first; ships only the row UI if the helper isn't there yet, with a snackbar "Coming soon" effect. Lexicon work files upstream.
- **Coil isn't wired yet.** Image-quality picker has no consumer until Coil lands. → Mitigation: ship the row as a UI-only preference (persists, no effect). Memory `feedback_compose_preview_workflow` covers ensuring the row's previews + screenshot tests work without Coil. When Coil lands, a follow-up wires the policy through.
- **Switching to a real multi-account picker later means rewiring the "Switch account" row.** → Mitigation: keep the placeholder row inert (no tap action, or a snackbar). Documentation: the row's `data object SwitchAccount` selector is named so the multi-account epic's owner can find it.
- **The shell rebuild changes `SettingsStubContract` shape.** Existing screenshot + instrumentation tests need rewriting. → Mitigation: treat the shell rebuild as a "replace and verify" task — drop the old screenshot baselines, add new ones for the rebuilt shell. The `update-baselines` label workflow (per memory `feedback_compose_preview_workflow`) handles the baseline churn.

## Migration Plan

1. Land `nubecita-ajty` into the current `SettingsStubScreen` as a single `SegmentedListItem` inside a one-item "Open links & sharing" section card. This proves the SegmentedListItem pattern works in this module.
2. Land the shell rebuild (new bd child to be filed). All current sections render with placeholder content (Sign Out + version row + ajty row + empty section cards for the rest are acceptable for the shell PR — sections fill in later). New screenshot baselines for phone and tablet shapes. Old `SettingsStubContent` removed.
3. Land `nubecita-77l` per its existing description: rename + module move, no UI changes. PR-internal diff is mostly file moves and qualified-name rewrites.
4. Land six section tasks (Display, Notifications, Content & moderation, Account, About, Data usage) in any order. Each is one bd child, one PR, one section card, one ViewModel handler set, full preview + screenshot + unit-test coverage. Recommended first: Display (theme picker) because (a) most-requested, (b) state-bearing — exercises the DataStore-backed repo path the other local-preference rows reuse.
5. Once all six ship, the Settings epic `nubecita-37to` closes. Follow-on Settings work (multi-account picker, native muted-words editor, accessibility surfaces) ships as new epics referencing the established shell.

No rollback needed at any stage — every step is additive and survives a partial-merge state cleanly (the shell renders empty cards for sections that haven't shipped yet).

## Open Questions

- Does `:core:preferences` already exist in some form (e.g., inside `:core:auth` for OAuth session prefs)? If yes, extend it; if no, the Display task creates it. To be resolved when scoping the Display task.
- Should the X-close on tablet modal also replace the back-arrow on phone (consistency), or does phone keep the back-arrow (standard Material navigation)? Recommend phone keeps back-arrow; tablet uses X. To be confirmed during shell implementation by visual review.
- "Manage your Bluesky account" pill destination — `https://bsky.app/settings` or the user's specific PDS settings page? Recommend bsky.app/settings for the v1 (web settings UI is bsky.app-hosted regardless of PDS); revisit when self-hosted PDS users surface as a real cohort.
- For server-stored preferences (push prefs, content-warning), do we mirror them to a local read-cache so the UI doesn't flash empty on cold start while the network resolves? Recommend yes, mirror; decision deferred to the Notifications and Content & moderation tasks.
