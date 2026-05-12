# Profile feature — Bead F: other-user variant + ListDetailSceneStrategy + Settings stub — design

**Bead ID:** `nubecita-s6p.6` (Bead F of the Profile epic `nubecita-s6p`)
**Branch:** `feat/nubecita-s6p.6-feature-profile-impl-other-user-variant-listdetail`
**Predecessor designs:** `openspec/changes/add-profile-feature/design.md` (Decisions 1–8) + `docs/superpowers/specs/2026-05-12-profile-bead-d-design.md` + `docs/superpowers/specs/2026-05-12-profile-bead-e-design.md`
**Predecessor tasks:** `openspec/changes/add-profile-feature/tasks.md` §6 (the bead F definition) + §7 (the follow-up bd issues filed at bead end)

## Scope

This bead closes the Profile epic. After it lands:

- Other-user navigation (`Profile(handle = "alice.bsky.social")`) renders the same screen as own-profile, but with a distinct actions row — **Follow / Following + Message + overflow(Block / Mute / Report)** instead of own-profile's **Edit + overflow(Settings)**. All five new affordances are stubbed → `ProfileEffect.ShowComingSoon(action)` → snackbar. The Follow ↔ Following label is driven by real wire data (`viewer.following`) from `app.bsky.actor.defs#profileViewDetailed`.
- The Profile entry carries `ListDetailSceneStrategy.listPane(detailPlaceholder = …)` metadata. On Medium+ widths, the Profile renders in the left pane and `PostDetailPaneEmptyState` (promoted from Feed's `FeedDetailPlaceholder`) renders in the right pane until a `PostDetailRoute` is pushed onto the back stack via a post-tap inside the body.
- The Settings sub-route resolves to a real `SettingsStubScreen` ("Settings — coming soon" + Sign Out button). Sign Out flows through `AuthRepository.signOut()` with a confirmation dialog, a loading state, and an error snackbar. The success path relies on the existing reactive routing in `MainActivity` (`SessionState.SignedOut → navigator.replaceTo(Login)`).
- `:app`'s `MainShellPlaceholderModule` no longer references `Settings`. After this bead, the module contains only Search and Chats placeholders.
- The own-profile overflow menu's Settings entry is the only user-reachable entry point to the Settings sub-route in this epic.
- Six (optionally seven) follow-up bd issues are filed under the Profile epic for the deferred work (scroll-collapsing hero, Expanded 3-pane, real Follow/Unfollow writes, real Edit profile, real Message routing, Settings-module graduation, optional real moderation actions).

### Out of scope (explicitly deferred)

| Excluded | Where it lands |
|---|---|
| Real Follow / Unfollow writes (`app.bsky.graph.follow` + `deleteFollow` + optimistic UI) | Follow-up bd `7.3` |
| Real Edit profile screen | Follow-up bd `7.4` |
| Real Message routing | Follow-up bd `7.5` (depends on `chat.bsky` lexicon coverage) |
| Real moderation actions (Block / Mute / Report writes) | Follow-up bd `7.7` |
| Scroll-collapsing hero / TopAppBarScrollBehavior integration | `nubecita-1tc` |
| Expanded 3-pane with side panel | Follow-up bd `7.2` |
| `:feature:settings:impl` module graduation | Follow-up bd `7.6` |
| Cross-handle self-tap visual pulse / haptic | Epic open question — defer to reviewer feedback |

## Architecture

### File layout

```
:designsystem/
├── src/main/kotlin/.../component/
│   └── PostDetailPaneEmptyState.kt              # NEW (promoted from :feature:feed:impl/ui/FeedDetailPlaceholder)
├── src/main/res/values/strings.xml              # +nubecita_detail_pane_select_post
└── src/screenshotTest/kotlin/.../component/
    └── PostDetailPaneEmptyStateScreenshotTest.kt # NEW (replaces FeedDetailPlaceholder fixtures)

:feature:feed:impl/
├── src/main/kotlin/.../ui/FeedDetailPlaceholder.kt  # DELETE
├── src/main/kotlin/.../di/FeedNavigationModule.kt   # MODIFY: call PostDetailPaneEmptyState
└── src/main/res/values/strings.xml              # -feed_detail_placeholder_select

:feature:profile:impl/
├── src/main/kotlin/.../
│   ├── ProfileContract.kt                       # MODIFY: + StubbedAction.{Block,Mute,Report}, + ProfileEvent.StubActionTapped
│   ├── ProfileScreen.kt                         # MODIFY: + onNavigateToSettings callback; wires existing effect branch
│   ├── ProfileScreenContent.kt                  # MODIFY: pass viewerRelationship + new callbacks to ProfileHero
│   ├── SettingsStubContract.kt                  # NEW
│   ├── SettingsStubViewModel.kt                 # NEW
│   ├── ui/
│   │   ├── ProfileActionsRow.kt                 # MODIFY: becomes slim router on ownProfile
│   │   ├── OwnProfileActionsRow.kt              # NEW: Edit + overflow(Settings)
│   │   ├── OtherUserActionsRow.kt               # NEW: Follow/Following + Message + overflow(Block/Mute/Report)
│   │   ├── ProfileActionsOverflowMenu.kt        # NEW: shared DropdownMenu primitive
│   │   ├── ProfileHero.kt                       # MODIFY: forward new callbacks
│   │   └── SettingsStubScreen.kt                # NEW
│   ├── di/
│   │   └── ProfileNavigationModule.kt           # MODIFY: listPane{} on Profile; Settings entry wires SettingsStubScreen
│   └── data/
│       └── AuthorProfileMapper.kt               # MODIFY: + toProfileHeaderWithViewer; reads viewer.following
├── src/main/res/values/strings.xml              # + ~10 new strings (stub copy + dialog + settings + signout error)
├── src/test/kotlin/.../
│   ├── SettingsStubViewModelTest.kt             # NEW
│   ├── data/AuthorProfileMapperTest.kt          # MODIFY: + viewer.following cases
│   └── ProfileViewModelTest.kt                  # MODIFY: + StubAction effects + Self override
├── src/screenshotTest/kotlin/.../ui/
│   ├── OwnProfileActionsRowScreenshotTest.kt    # NEW (4 baselines)
│   ├── OtherUserActionsRowScreenshotTest.kt     # NEW (6 baselines)
│   └── SettingsStubScreenScreenshotTest.kt      # NEW (6 baselines)
├── src/screenshotTest/kotlin/.../
│   └── ProfileScreenContentScreenshotTest.kt    # MODIFY (+6 baselines: other-user variants + Medium-width fixture)
└── src/androidTest/kotlin/.../
    ├── ProfileScreenAdaptiveInstrumentationTest.kt # NEW (§6.9)
    ├── SettingsStubInstrumentationTest.kt        # NEW (§6.10)
    └── ProfileScreenInstrumentationTest.kt       # MODIFY: + Settings overflow tap path

app/src/main/java/net/kikin/nubecita/shell/
└── MainShellPlaceholderModule.kt                # MODIFY: remove Settings provider (Search + Chats remain)
```

**Three things that didn't exist anywhere in beads C–E:**

1. A presenter for a "stub" surface (`SettingsStubViewModel`) — first time we MVI-ify a one-button screen.
2. An overflow `DropdownMenu` primitive on the profile actions row.
3. `ListDetailSceneStrategy.listPane{}` metadata on a non-Feed entry.

Everything else is mechanical wiring or test fixtures. Medium-width two-pane behavior comes free from the strategy once the metadata is attached.

### Actions row — router pattern

Bead D's `ProfileActionsRow` (~20 lines, single OutlinedButton + IconButton) becomes a slim router:

```kotlin
@Composable
internal fun ProfileActionsRow(
    ownProfile: Boolean,
    viewerRelationship: ViewerRelationship,
    onEdit: () -> Unit,
    onFollow: () -> Unit,
    onMessage: () -> Unit,
    onOverflowAction: (StubbedAction) -> Unit,   // Block / Mute / Report (other-user only)
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ownProfile) {
        OwnProfileActionsRow(onEdit = onEdit, onSettings = onSettings, modifier = modifier)
    } else {
        OtherUserActionsRow(
            viewerRelationship = viewerRelationship,
            onFollow = onFollow,
            onMessage = onMessage,
            onOverflowAction = onOverflowAction,
            modifier = modifier,
        )
    }
}
```

`OwnProfileActionsRow.kt`:

- `OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.profile_action_edit)) }` — preserves bead D's visual.
- `ProfileActionsOverflowMenu(entries = listOf(OverflowEntry(label = stringResource(R.string.profile_action_settings), onClick = onSettings)))`.

`OtherUserActionsRow.kt`:

- **Follow** — filled `Button(onClick = onFollow)` when `viewerRelationship != Following`, label `"Follow"`. `OutlinedButton(onClick = onFollow)` when `Following`, label `"Following"`. The `None` and `NotFollowing` cases both render `"Follow"`. The shared `onFollow` callback fires `ProfileEvent.FollowTapped` either way — the action handler currently emits `ShowComingSoon(Follow)`.
- **Message** — `OutlinedButton(onClick = onMessage)`, label `"Message"`. `Modifier.weight(1f)` on Follow + Message so they share equal width.
- **Overflow** — `ProfileActionsOverflowMenu` with three entries (Block / Mute / Report), each dispatching `onOverflowAction(StubbedAction.{Block,Mute,Report})`.

`ProfileActionsOverflowMenu.kt`:

```kotlin
internal data class OverflowEntry(val label: String, val onClick: () -> Unit)

@Composable
internal fun ProfileActionsOverflowMenu(
    entries: List<OverflowEntry>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            NubecitaIcon(
                name = NubecitaIconName.MoreVert,
                contentDescription = stringResource(R.string.profile_action_overflow),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.label) },
                    onClick = {
                        expanded = false
                        entry.onClick()
                    },
                )
            }
        }
    }
}
```

The menu owns its own `expanded` state — parents pass callbacks, not visibility. This keeps the parent composables stateless and lets each menu instance manage its own lifecycle.

### Settings stub — full MVI presenter

The Settings sub-route is the only screen in this epic that performs a real side-effectful action. Per CLAUDE.md MVI conventions, it gets a full presenter despite being a one-button screen.

```kotlin
// SettingsStubContract.kt
data class SettingsStubViewState(
    val confirmDialogOpen: Boolean = false,
    val status: SettingsStubStatus = SettingsStubStatus.Idle,
) : UiState

sealed interface SettingsStubStatus {
    data object Idle : SettingsStubStatus
    data object SigningOut : SettingsStubStatus
    // No SignedOut variant: on success, SessionStateProvider transitions →
    // MainActivity reactive collector does replaceTo(Login) → this screen
    // unmounts before we'd render a "signed out" state.
}

sealed interface SettingsStubEvent : UiEvent {
    data object SignOutTapped : SettingsStubEvent
    data object ConfirmSignOut : SettingsStubEvent
    data object DismissDialog : SettingsStubEvent
}

sealed interface SettingsStubEffect : UiEffect {
    data object ShowSignOutError : SettingsStubEffect
}
```

Shape rationale:

- `confirmDialogOpen: Boolean` is **flat** because dialog visibility is independent of sign-out status (a dialog can be open while idle OR while signing out — the latter shows a spinner inside the dialog).
- `status` is a **sealed sum** because Idle / SigningOut are mutually exclusive.
- No `Refresh` / `Retry` events — sign-out is one-shot; retry = tap Sign Out again.

`SettingsStubViewModel`:

```kotlin
@HiltViewModel
internal class SettingsStubViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : MviViewModel<SettingsStubViewState, SettingsStubEvent, SettingsStubEffect>(
    SettingsStubViewState(),
) {
    override fun handleEvent(event: SettingsStubEvent) {
        when (event) {
            SettingsStubEvent.SignOutTapped -> setState { copy(confirmDialogOpen = true) }
            SettingsStubEvent.DismissDialog -> setState { copy(confirmDialogOpen = false) }
            SettingsStubEvent.ConfirmSignOut -> runSignOut()
        }
    }

    private fun runSignOut() {
        if (uiState.value.status is SettingsStubStatus.SigningOut) return
        setState { copy(status = SettingsStubStatus.SigningOut) }
        viewModelScope.launch {
            authRepository.signOut()
                .onFailure {
                    setState { copy(confirmDialogOpen = false, status = SettingsStubStatus.Idle) }
                    sendEffect(SettingsStubEffect.ShowSignOutError)
                }
            // No onSuccess: SessionState transition → outer Navigator → screen unmounts.
        }
    }
}
```

`SettingsStubScreen.kt` follows the `ProfileScreen` / `ProfileScreenContent` split — a stateful wrapper that injects the VM and routes effects, plus a stateless `SettingsStubContent` that previews and screenshot tests call directly. An `AlertDialog` renders inside the content when `state.confirmDialogOpen`, with the confirm button swapping its label for a `CircularProgressIndicator` during `SigningOut`. Both dialog buttons disable mid-sign-out so a stray tap can't dispatch a duplicate event.

### `ProfileNavigationModule` changes

```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Provides
@IntoSet
@MainShell
fun provideProfileEntries(): EntryProviderInstaller =
    {
        entry<Profile>(
            metadata = ListDetailSceneStrategy.listPane(
                detailPlaceholder = { PostDetailPaneEmptyState() },
            ),
        ) { route ->
            val navState = LocalMainShellNavState.current
            val viewModel = hiltViewModel<ProfileViewModel, ProfileViewModel.Factory>(
                creationCallback = { factory -> factory.create(route) },
            )
            ProfileScreen(
                viewModel = viewModel,
                onNavigateToPost = { uri -> navState.add(PostDetailRoute(postUri = uri)) },
                onNavigateToProfile = { handle -> navState.add(Profile(handle = handle)) },
                onNavigateToSettings = { navState.add(Settings) },
            )
        }
    }

@Provides
@IntoSet
@MainShell
fun provideSettingsEntries(): EntryProviderInstaller =
    {
        entry<Settings> {
            val navState = LocalMainShellNavState.current
            SettingsStubScreen(onBack = { navState.removeLast() })
        }
    }
```

`ProfileScreen` gains `onNavigateToSettings: () -> Unit`. The effect collector's existing `ProfileEffect.NavigateToSettings` branch (a TODO comment in bead D) now calls it.

**Why `listPane` on `Profile`:** the strategy's contract is symmetric — `Feed` carries `listPane`, `PostDetailRoute` carries `detailPane`. When the back stack is `[Profile]` on Medium+, the strategy renders the profile in the left pane and `PostDetailPaneEmptyState` in the right. When a `PostDetailRoute` is pushed, the strategy keeps the profile in the left pane and renders the detail on the right — no scaffolding owned by `:feature:profile:impl`. The `Profile → Profile` push (cross-handle navigation) works identically: a second `Profile` entry replaces the first as the list-pane content; the detail pane resets to `PostDetailPaneEmptyState`.

### Mapper change — `viewerRelationship` from `viewer.following`

Bead C's `toProfileHeaderUi()` extension stays as-is. A new `toProfileHeaderWithViewer()` composes over it:

```kotlin
internal data class ProfileHeaderWithViewer(
    val header: ProfileHeaderUi,
    val viewerRelationship: ViewerRelationship,
)

internal fun ProfileViewDetailed.toProfileHeaderWithViewer(): ProfileHeaderWithViewer =
    ProfileHeaderWithViewer(
        header = toProfileHeaderUi(),
        viewerRelationship = viewer.toViewerRelationship(),
    )

private fun ViewerState?.toViewerRelationship(): ViewerRelationship =
    when {
        this == null -> ViewerRelationship.None
        following != null -> ViewerRelationship.Following
        else -> ViewerRelationship.NotFollowing
    }
```

`ViewerState.following: AtUri?` from `app.bsky.actor.defs#viewerState` is the AtUri of *the requesting user's* follow record pointing at the subject profile. Non-null means "I follow them"; null means "I don't" (or unauthed, but profile screens only mount post-login).

**Self-mapping note.** For own-profile (`route.handle == null`), the VM sets `viewerRelationship = Self` after the header loads — *overriding* whatever the mapper reports. This is correct: `viewer.following` semantics for one's own profile are undefined (you can't follow yourself); `Self` is the only meaningful relationship.

The `ProfileRepository.fetchHeader` signature changes from `Result<ProfileHeaderUi>` to `Result<ProfileHeaderWithViewer>`. The VM's `launchHeaderLoad.onSuccess` adapts:

```kotlin
.onSuccess { result ->
    setState {
        copy(
            header = result.header,
            viewerRelationship = if (ownProfile) ViewerRelationship.Self else result.viewerRelationship,
        )
    }
}
```

### Promoting `FeedDetailPlaceholder` to `:designsystem`

`FeedDetailPlaceholder` in `:feature:feed:impl/ui/` moves to `:designsystem/.../component/PostDetailPaneEmptyState.kt`. The composable body is unchanged — same Article icon, same centered column, same `surfaceContainerLow` background. The string resource `feed_detail_placeholder_select` moves to `:designsystem`'s `strings.xml` as `nubecita_detail_pane_select_post` (rename only; English copy stays "Select a post to read its thread").

`:feature:feed:impl/.../FeedNavigationModule.kt` swaps the metadata's detail-placeholder lambda to call `PostDetailPaneEmptyState()`. The old `FeedDetailPlaceholder` file is deleted, the orphan string is removed, and any matching screenshot baselines are deleted (the new `PostDetailPaneEmptyStateScreenshotTest` in `:designsystem` replaces them).

The composable's visibility changes from `internal` to `public` (cross-module callers).

### `MainShellPlaceholderModule.kt` cleanup

Remove `provideSettingsPlaceholderEntries` entirely. Remove the `import …Settings` line. The `R.string.main_shell_settings` resource may stay if used as the bottom-bar label elsewhere — verify during implementation; if orphaned, remove it.

After bead F, this module contains only the Search and Chats placeholder providers.

### New `ProfileEvent` and `StubbedAction` variants

```kotlin
sealed interface ProfileEvent : UiEvent {
    // … existing events …

    /** User tapped one of the stubbed overflow entries (Block / Mute / Report). */
    data class StubAction(val action: StubbedAction) : ProfileEvent
}

enum class StubbedAction { Follow, Edit, Message, Block, Mute, Report }
```

`StubActionTapped` consolidates the three other-user overflow taps into one parameterized event, mirroring the existing `ShowComingSoon(action)` effect shape. The VM's handler is a one-liner: `is ProfileEvent.StubActionTapped -> sendEffect(ProfileEffect.ShowComingSoon(event.action))`. (Renamed from `StubAction` → `StubActionTapped` to match the `FollowTapped`/`EditTapped`/`MessageTapped`/`SettingsTapped` naming convention on the rest of `ProfileEvent`.)

The screen's effect collector adds three new `StubbedAction` cases when picking the snackbar copy:

```kotlin
StubbedAction.Block -> comingSoonBlock
StubbedAction.Mute -> comingSoonMute
StubbedAction.Report -> comingSoonReport
```

## Tests

### Unit tests

| File | Coverage |
|---|---|
| `SettingsStubViewModelTest.kt` (NEW) | `SignOutTapped` opens dialog; `DismissDialog` closes it; `ConfirmSignOut` sets `SigningOut` + calls `signOut()`; failure resets to Idle + closes dialog + emits `ShowSignOutError`; double-`ConfirmSignOut` mid-flight is a no-op (single-flight); success leaves VM in `SigningOut` (relies on screen unmount). |
| `AuthorProfileMapperTest.kt` (MODIFY) | `viewer == null` → `None`; `viewer.following != null` → `Following`; `viewer != null && viewer.following == null` → `NotFollowing`; existing `toProfileHeaderUi` cases unchanged. |
| `ProfileViewModelTest.kt` (MODIFY) | (a) `StubAction(Block)` / `(Mute)` / `(Report)` → `ShowComingSoon(...)` effect, no repo call. (b) Own-profile header load overrides mapper-reported `viewerRelationship` to `Self`. (c) Other-user header load preserves mapper-reported `viewerRelationship`. |

### Screenshot tests (Compact width unless noted)

| File | Fixtures |
|---|---|
| `OwnProfileActionsRowScreenshotTest.kt` (NEW) | own-edit-overflow-closed-{light,dark}; own-edit-overflow-open-{light,dark} (DropdownMenu expanded). 4 baselines. |
| `OtherUserActionsRowScreenshotTest.kt` (NEW) | other-follow-{light,dark}; other-following-{light,dark}; other-overflow-open-{light,dark}. 6 baselines. |
| `SettingsStubScreenScreenshotTest.kt` (NEW) | settings-idle-{light,dark}; settings-confirm-open-{light,dark}; settings-signing-out-{light,dark} (dialog with spinner). 6 baselines. |
| `ProfileScreenContentScreenshotTest.kt` (MODIFY) | + screen-other-user-follow-{light,dark}; + screen-other-user-following-{light,dark}; + screen-medium-two-pane-empty-{light,dark} (Profile in list pane, placeholder in detail pane). 6 new baselines. |
| `PostDetailPaneEmptyStateScreenshotTest.kt` (NEW, `:designsystem`) | placeholder-{light,dark}. 2 baselines. |

Total new/migrated baselines: ~24. Deleted Feed-side `FeedDetailPlaceholder` baselines net against the new `:designsystem` ones.

**Medium-width fixture how-to.** The Medium fixtures compose `ProfileScreenContent` inside a `ListDetailPaneScaffold` with `directive = calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(...)` (the same directive `MainShell` uses) at `widthDp = 800`. Layoutlib renders both panes deterministically. If `:feature:profile:impl/.../screenshotTest/` already defines a `MediumWidthPreview` Multipreview annotation (verify during implementation), reuse it; otherwise define one inline next to the fixture.

### Instrumentation tests (require `run-instrumented` PR label per `feedback_run_instrumented_label_on_androidtest_prs.md`)

| File | Coverage |
|---|---|
| `ProfileScreenAdaptiveInstrumentationTest.kt` (NEW, §6.9) | On a Medium-width activity, tap a `PostCard` in the profile body; assert the profile-screen test tag stays in the composition AND a `PostDetailScreen` test tag joins. Verifies the tap landed in the right pane, not full-screen. |
| `SettingsStubInstrumentationTest.kt` (NEW, §6.10) | Inject a fake `AuthRepository` via a Hilt test module; render `SettingsStubScreen`; tap Sign Out → confirm in dialog; assert `signOut()` was called exactly once. Second case: fake returns `Result.failure(IOException)`; assert the error snackbar appears. |
| `ProfileScreenInstrumentationTest.kt` (MODIFY from bead D) | + own-profile overflow tap path: tap overflow icon, tap Settings entry, assert no snackbar AND `Settings` was pushed onto inner nav stack. |

### What this bead does NOT test

- Real Follow / Unfollow writes — epic 7.3.
- Real Block / Mute / Report writes — epic 7.7.
- `SessionState.SignedOut → replaceTo(Login)` routing — covered by `MainActivity`-level tests.
- Cross-handle self-tap pulse/haptic — epic open question.

## Follow-up bd issues (filed at end of bead per tasks.md §7)

Filed via `bd create` once bead F's commits land; PR body links each.

| # | Title | Notes |
|---|---|---|
| 7.1 | `feature/profile: scroll-collapsing hero + TopAppBar transition` | Verify `nubecita-1tc` is still open and matches scope; refile only if needed. |
| 7.2 | `feature/profile: Expanded 3-pane with side panel (suggested follows + pinned feeds)` | Flag data dependency on `app.bsky.actor.getSuggestions` + `getPreferences`; file upstream `kikin81/atproto-kotlin` issue if lexicons are partial. |
| 7.3 | `feature/profile: real Follow / Unfollow writes` | `app.bsky.graph.follow` + `deleteFollow` + optimistic UI. Replaces the `FollowTapped → ShowComingSoon` stub; mention the actions-row router seam. |
| 7.4 | `feature/profile: real Edit profile screen` | Independent epic. |
| 7.5 | `feature/profile: real Message routing` | Verify `chat.bsky` lexicon coverage; file upstream issue per `reference_atproto_kotlin_notification_lexicon_gap.md` precedent if partial. |
| 7.6 | `feature/settings: graduate to its own :feature:settings:impl module` | Triggered when real Settings ships; migrates the `@MainShell` Settings provider out of `:feature:profile:impl`. |
| 7.7 | `feature/profile: real moderation actions (Block / Mute / Report)` | Replaces `StubAction(Block/Mute/Report) → ShowComingSoon`. Depends on lexicon coverage for `app.bsky.graph.block`, `.mute`, and report endpoints. |

## Risks / trade-offs

| Risk | Mitigation |
|---|---|
| `SettingsStubViewModel` and `MainActivity`'s reactive sign-out collector race on success — VM tries to `setState` after the screen has unmounted. | Success branch deliberately doesn't `setState`. `viewModelScope` cancels cleanly when the VM is scrapped. Worst case is a swallowed `CancellationException` — correct behavior. |
| Confirmation dialog state lives only in the VM (`confirmDialogOpen`). On process death, dialog state is lost. | Acceptable. Sign-out is a deliberate two-tap action; if the system kills the app mid-confirmation, the user re-taps Sign Out and re-confirms. No `SavedStateHandle` plumbing for a single boolean. |
| Promoting `FeedDetailPlaceholder` to `:designsystem` couples the design system to a "select a post" semantic that may not generalize to non-post panes later (e.g., a "select a notification" pane). | The new name `PostDetailPaneEmptyState` is explicit about scope. A future non-post pane gets its own composable; the promotion is correct *for the current consumers* (Feed, Profile), and a third caller in the future doesn't invalidate today's decision. |
| Other-user actions row's Follow button reads `viewerRelationship` for the label, but the tap is stubbed — the button flips Follow ↔ Following based on real data, but tapping never changes it. Risk of "broken-looking" interaction. | The stub copy is split — `"Follow coming soon"` vs `"Unfollow coming soon"` — so the user understands the affordance is pending. Visual fidelity matters more than write-correctness per Decision 5. |
| `ListDetailSceneStrategy.listPane` metadata on `Profile` means the screen renders in the LIST pane on Medium+ widths. The hero is wide; no explicit pane sizing. | Default `calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth` proportions (~50/50). Hero already composes inside a `LazyColumn` with no fixed min-width, so it adapts. Real-device verify on Pixel Tablet (~600dp pane width). |
| Removing the `:app`-side `Settings` placeholder leaves the route briefly unreachable if a reviewer cherry-picks the cleanup commit alone. | All Settings work lands in one PR — no mid-bead unreachable state in `main`. |
| Cross-handle self-tap remains silent — epic open question unresolved. | Defer per Decision 8's open-question note. If reviewers flag it during PR review, add a `ProfileEffect.VisuallyAcknowledgeSelfTap` in a follow-up; the contract surface stays open. |
| `viewerRelationship` plumbing now reaches the mapper boundary, but real Follow writes don't yet update it on optimistic actions. | Epic 7.3 carries the optimistic-update logic. Bead F's contract is read-only from wire data; tapping Follow → `ShowComingSoon` doesn't mutate state. |

## Acceptance

This bead is done when:

- [ ] `OwnProfileActionsRow`, `OtherUserActionsRow`, `ProfileActionsOverflowMenu` exist; `ProfileActionsRow` is the slim router.
- [ ] `SettingsStubScreen`, `SettingsStubContract`, `SettingsStubViewModel` exist; sign-out flows through `AuthRepository.signOut()` with confirmation dialog + error UI.
- [ ] `ProfileNavigationModule` carries `ListDetailSceneStrategy.listPane(detailPlaceholder = { PostDetailPaneEmptyState() })` on the Profile entry; Settings entry resolves to `SettingsStubScreen`.
- [ ] `:app`'s `MainShellPlaceholderModule` no longer references `Settings`; orphan strings removed.
- [ ] `:designsystem` owns `PostDetailPaneEmptyState`; `:feature:feed:impl` calls it.
- [ ] `AuthorProfileMapper` populates `viewerRelationship` from `viewer.following`; `ProfileViewModel` overrides to `Self` for own-profile.
- [ ] `ProfileContract`: + `StubbedAction.{Block,Mute,Report}`; + `ProfileEvent.StubActionTapped(StubbedAction)`; + `SettingsStubContract.kt`.
- [ ] ~24 new screenshot baselines committed; orphan Feed baselines deleted.
- [ ] All new + modified unit tests pass; instrumentation tests pass (`run-instrumented` label on the PR).
- [ ] `./gradlew :designsystem:assembleDebug :feature:feed:impl:assembleDebug :feature:profile:impl:assembleDebug :feature:profile:impl:testDebugUnitTest :feature:profile:impl:validateDebugScreenshotTest :designsystem:validateDebugScreenshotTest :app:assembleDebug spotlessCheck lint :app:checkSortDependencies` all green locally.
- [ ] On-device smoke (Pixel 10 Pro XL Compact + Pixel Tablet Medium): own-profile renders; overflow → Settings opens; Sign Out → confirm → returns to Login; other-user profile renders Follow / Following / Message; Block / Mute / Report stub each surface their snackbar; tapping a post on Medium lands in the right pane; tapping a handle pushes another Profile onto the back stack.
- [ ] 7 follow-up bd issues filed and linked from the PR body.

## References

- Bead E design: `docs/superpowers/specs/2026-05-12-profile-bead-e-design.md`
- Bead D design: `docs/superpowers/specs/2026-05-12-profile-bead-d-design.md`
- Epic-level design: `openspec/changes/add-profile-feature/design.md` (Decisions 1–8)
- Capability spec: `openspec/changes/add-profile-feature/specs/feature-profile/spec.md`
- Tasks (this bead): `openspec/changes/add-profile-feature/tasks.md` §6 + §7
- `:feature:feed:impl/.../FeedNavigationModule.kt` — reference for `ListDetailSceneStrategy.listPane{}` metadata
- `:feature:postdetail:impl/.../PostDetailNavigationModule.kt` — reference for `detailPane()` metadata
- `:core:auth/AuthRepository.signOut()` — the sign-out pathway
- `app/MainActivity.kt` — reactive `SessionState.SignedOut → replaceTo(Login)` routing
- `feedback_run_instrumented_label_on_androidtest_prs.md` — PR-label convention for `androidTest` work
- `feedback_resolve_copilot_threads_on_reply.md` — Copilot review thread resolution convention
