# Group chat Phase 3c-3a — join link management (owner side)

**Epic:** `nubecita-hwix` (Group chat support). **Sub-slice C3a** of Phase 3, after Slices A
(group details, #561), B (member management, #562), C1 (group creation, #564), C2 (join requests,
#565). **bd:** `nubecita-hwix.8`.

## Purpose

Let a group **owner** create, share, enable/disable, and configure their group's single **join
link**, via atproto 9.4.0 `chat.bsky.group` — from a dedicated screen reachable off the
group-details screen. A shared link lets others join (or request to join) the group.

## Scope

C3a only: the **owner-side** of invite links (manage + share). **Out of scope → C3b** (the joiner
side): `getGroupPublicInfo` preview, `requestJoin`, the manifest intent filter, the
`GroupJoinDeepLinkKey(code)` deep-link matcher, and `MainActivity.handleIntent` routing. C3a
generates and shares the `https://nubecita.app/group/join/{code}` URL; C3b is what *intercepts* it.

No "delete link" (the SDK has none — once created, a link is enabled/disabled). No code rotation UI
(no rotate endpoint). No SDK bump or overlay needed.

## SDK surface (atproto 9.4.0 — verified in `models-jvm-9.4.0.jar`)

`io.github.kikin81.atproto.chat.bsky.group.GroupService`, constructed `GroupService(xrpcClient)`
(same as the Slice B/C1/C2 group ops).

- `createJoinLink(CreateJoinLinkRequest(convoId: String, joinRule: JoinRule, requireApproval: AtField<Boolean> = Missing))`
  → `CreateJoinLinkResponse(joinLink: JoinLinkView)`. Creates the link when none exists.
- `editJoinLink(EditJoinLinkRequest(convoId: String, joinRule: AtField<JoinRule> = Missing, requireApproval: AtField<Boolean> = Missing))`
  → `EditJoinLinkResponse(joinLink: JoinLinkView)`. Edits the existing link's settings.
- `enableJoinLink(EnableJoinLinkRequest(convoId: String))` → `EnableJoinLinkResponse(joinLink: JoinLinkView)`.
  Re-enables a previously disabled link.
- `disableJoinLink(DisableJoinLinkRequest(convoId: String))` → `DisableJoinLinkResponse(joinLink: JoinLinkView)`.
  Disables the active link.
- `JoinLinkView(code: String, createdAt: Datetime, enabledStatus: LinkEnabledStatus, joinRule: JoinRule, requireApproval: Boolean)`.
- `JoinRule` = `String` typealias; knownValues `"anyone"`, `"followedByOwner"`.
- `LinkEnabledStatus` = `String` typealias; knownValues `"enabled"`, `"disabled"`.

**No standalone "get link" endpoint.** The current link is read from the convo:
`ConvoService(client).getConvo(GetConvoRequest(convoId)).convo.kind` → `GroupConvo.joinLink: JoinLinkView?`
(optional — `groupConvo` requires only `name` + `lockStatus`, so a group has **0 or 1** link).

`requireApproval` ties directly to C2: `true` routes joiners into the C2 Join-requests approve/reject
queue; `false` lets them join directly.

## Architecture

### Entry point + route

- An **owner-only "Invite link" row** in `GroupDetailsScreen`'s `LoadedBody` — a tappable row
  (leading icon + label + trailing chevron) gated on `state.viewerRole == GroupRole.Owner`, placed
  next to the existing "Join requests" row. Tapping emits `GroupDetailsEvent.InviteLinkTapped` →
  `GroupDetailsEffect.NavigateTo(ManageJoinLink(convoId))`. No status badge (state loads on the
  sub-screen; avoids an extra `getConvo` on every group-details open).
- New `@Serializable data class ManageJoinLink(val convoId: String) : NavKey` in
  `:feature:chats:api` (`Chats.kt`).
- `ChatsNavigationModule` registers `entry<ManageJoinLink>(metadata = adaptiveDialog())` (full-screen
  on Compact, centered dialog on Medium/Expanded), with the assisted-injected VM — mirrors the C2
  `GroupJoinRequests` wiring.

### Repository (`ChatRepository` + `DefaultChatRepository` + the 5 fakes)

```kotlin
suspend fun getJoinLink(convoId: String): Result<JoinLinkUi?>
suspend fun createJoinLink(convoId: String, joinRule: JoinRule, requireApproval: Boolean): Result<JoinLinkUi>
suspend fun editJoinLink(convoId: String, joinRule: JoinRule? = null, requireApproval: Boolean? = null): Result<JoinLinkUi>
suspend fun enableJoinLink(convoId: String): Result<JoinLinkUi>
suspend fun disableJoinLink(convoId: String): Result<JoinLinkUi>
```

- `getJoinLink` reads the raw wire convo directly — `ConvoService(client).getConvo(GetConvoRequest(convoId)).convo.kind`,
  narrowed to `GroupConvo`, then `joinLink?.toJoinLinkUi()` (returns `null` when the convo has no
  link or is not a group). It does **not** go through the repo's mapped `getConvo`, whose `ChatConvo`
  return type intentionally drops `joinLink`.
- The four mutations wrap `GroupService(client)`; each maps its response's `joinLink` →
  `JoinLinkUi`.
- `editJoinLink` maps each non-null arg to its `AtField(...)` and leaves a `null` arg as
  `AtField.Missing` — so a single-field edit (e.g. only `requireApproval`) **never re-sends
  `joinRule`** and cannot clobber a rule the client doesn't understand. `createJoinLink` always
  sends `joinRule` (the SDK requires it) + `requireApproval`.
- All wrap calls in `runCatchingCancellable` style: rethrow `CancellationException`, log
  `throwable.javaClass.name` only (no PII), return `Result.failure`.
- All 5 fakes updated: a settable result + call capture per method, plus a per-method gate
  (`CompletableDeferred<Unit>?`) so tests can hold a mutation in-flight (mirrors C2's
  `approveJoinRequestGate`).

### Model + mapper

- `:data:models`: `enum class JoinRule { Anyone, FollowedByOwner, Unsupported }` and
  `@Immutable data class JoinLinkUi(code: String, url: String, enabled: Boolean, joinRule: JoinRule, requireApproval: Boolean, createdAt: kotlin.time.Instant)`.
  - `url = "https://nubecita.app/group/join/$code"` (computed in the mapper, stored on the model so
    the screen reads it directly for Copy/Share).
  - `enabled = enabledStatus == "enabled"`.
  - **`JoinRule` mapping (fail-closed):** `"anyone"` → `Anyone`, `"followedByOwner"` →
    `FollowedByOwner`, **any other / unknown value → `Unsupported`**. Defaulting an unknown future
    rule to `Anyone` is a **privacy risk**: a newer client could create a more-restrictive rule (e.g.
    `"adminsOnly"`); an older Nubecita that mapped it to `Anyone` and then re-saved would silently
    *widen* the group. `Unsupported` is non-permissive and is **never sent back** to the server
    (`JoinRule.toWire()` is defined only for `Anyone`/`FollowedByOwner`; `Unsupported` has no wire
    form). The UI locks setting mutations whenever the link is `Unsupported` (see Screen §).
- `feature/chats/impl/.../data/JoinLinkMapper.kt`: `internal fun JoinLinkView.toJoinLinkUi()` using
  `kotlin.time.Instant.parse(createdAt.raw)` (codebase uses `kotlin.time.Instant`, **not**
  `kotlinx.datetime`). A `JoinRule.toWire(): String` helper for the request direction.
- Fixture factory `JoinLinkUiFixtures` alongside the model (mirrors `PostUiFixtures`).

### Contract + ViewModel (`ManageJoinLinkContract`, `ManageJoinLinkViewModel`)

```kotlin
sealed interface ManageJoinLinkStatus {
    data object Loading : ManageJoinLinkStatus
    data class Loaded(val link: JoinLinkUi?) : ManageJoinLinkStatus   // null = no link yet
    data class Error(val error: ChatError) : ManageJoinLinkStatus
}

data class ManageJoinLinkViewState(
    val status: ManageJoinLinkStatus = ManageJoinLinkStatus.Loading,
    val mutationInFlight: Boolean = false,
) : UiState

sealed interface ManageJoinLinkEvent : UiEvent {
    data object Retry : ManageJoinLinkEvent
    data class CreateTapped(val joinRule: JoinRule, val requireApproval: Boolean) : ManageJoinLinkEvent
    data class JoinRuleChanged(val joinRule: JoinRule) : ManageJoinLinkEvent
    data class RequireApprovalChanged(val requireApproval: Boolean) : ManageJoinLinkEvent
    data object EnableTapped : ManageJoinLinkEvent
    data object DisableTapped : ManageJoinLinkEvent
}

sealed interface ManageJoinLinkEffect : UiEffect {
    data class ShowError(val error: ChatError) : ManageJoinLinkEffect
}
```

- `@HiltViewModel(assistedFactory = Factory::class)`, `@AssistedInject (@Assisted route: ManageJoinLink, repository: ChatRepository)`.
- **Load** on init: `getJoinLink(convoId)` → `Loaded(link?)` on success, `Error(toMemberMgmtError())`
  on failure. `Retry` re-runs the load (back to `Loading` first).
- **Create** (`CreateTapped`, only valid from `Loaded(null)`): guard on `mutationInFlight`; set
  `mutationInFlight = true`; `createJoinLink(...)` → success `status = Loaded(link)`; failure →
  `ShowError`; `finally mutationInFlight = false`.
- **Edit settings** (`JoinRuleChanged` / `RequireApprovalChanged`, only valid from `Loaded(non-null)`
  with `joinRule != Unsupported`): guard on `mutationInFlight`; **optimistic** — update
  `status = Loaded(link.copy(joinRule/requireApproval = …))` immediately, capture the prior link for
  rollback, call `editJoinLink` with **only the changed field** (`JoinRuleChanged` → `editJoinLink(convoId, joinRule = new)`;
  `RequireApprovalChanged` → `editJoinLink(convoId, requireApproval = new)`), success → reconcile with
  the returned `JoinLinkUi`, failure → roll back to the prior link + `ShowError`;
  `finally mutationInFlight = false`. Reducers read **current**
  state, not captured snapshots beyond the explicit rollback value.
- **Enable/Disable** (`EnableTapped` / `DisableTapped`): guard on `mutationInFlight`; **optimistic**
  flip `link.enabled`; call `enableJoinLink` / `disableJoinLink`; reconcile / roll back as above.
- All mutations share the single `mutationInFlight` flag (the link is one object — no per-row set
  needed, unlike C2). Errors map via the existing `toMemberMgmtError()`.
- **Unsupported guard:** when `Loaded(link)` and `link.joinRule == JoinRule.Unsupported`, the VM
  drops **all** setting events (`JoinRuleChanged` / `RequireApprovalChanged` / `EnableTapped` /
  `DisableTapped`) as no-ops — the screen disables the controls (below), but this is the
  defence-in-depth backstop so a stray event can never round-trip an `Unsupported` rule to the
  server.

### Screen + components

- `ManageJoinLinkScreen` (stateful: collects state + effects into a snackbar host via the
  child-coroutine + `rememberUpdatedState` pattern from C2's `GroupJoinRequestsScreen`) delegating to
  a stateless `ManageJoinLinkScreenContent`.
- `Scaffold(containerColor = surface)`, `TopAppBar` "Invite link" + close nav icon, body
  `widthIn(max = 600.dp)` centered, branching on `status`:
  - `Loading` → centered `NubecitaWavyProgressIndicator`.
  - `Error` → centered message + retry `TextButton`.
  - `Loaded(null)` → **create empty-state** (M3 Expressive): a **large tonal icon** — a link/group
    glyph (~64dp) in a soft `surfaceContainerHighest` circle for visual weight before the call to
    action — above a short title + explanatory text, then the two shared setting controls (a segmented
    button **Anyone / Followed by owner** for `joinRule`, a **Require approval** switch) seeded with
    local defaults (`Anyone`, `requireApproval = true`), and a **"Create link"** `FilledTonalButton`
    → `CreateTapped(joinRule, requireApproval)`. Button shows an in-button spinner while
    `mutationInFlight` (with the `// nubecita-allow-raw-progress` marker).
  - `Loaded(link)` → **link card** (`surfaceContainerLow`): the URL (selectable text, truncated) with
    **Copy** (clipboard) and **Share** (`ACTION_SEND` chooser) actions reading `link.url`; an
    **Enable/Disable** affordance reflecting `link.enabled`; the inline segmented `joinRule` +
    `requireApproval` switch bound live to `JoinRuleChanged` / `RequireApprovalChanged`; helper text
    under the switch noting that with approval on, joiners appear in **Join requests** (the C2 queue).
    - **Copy feedback:** tapping Copy shows a lightweight **"Link copied"** snackbar (reuses the
      screen's existing `SnackbarHostState`) so the action has a visible confirmation.
    - **Disabled-link de-emphasis** (`link.enabled == false`): wrap the card content in
      `Modifier.alpha(0.6f)`, render the URL in `colorScheme.onSurfaceVariant`, drop text selection
      (plain `Text`, not `SelectionContainer`), and disable Copy/Share. The Enable toggle stays fully
      opaque/enabled so re-enabling is always reachable.
    - **Unsupported-rule banner** (`link.joinRule == JoinRule.Unsupported`): show an inline banner —
      *"This link uses settings from a newer version of Nubecita. Update to edit."* — and disable the
      `joinRule` segmented control, the `requireApproval` switch, **and** the Enable/Disable toggle
      (no setting can be mutated). Copy/Share of the existing URL remain available. Backed by the VM
      Unsupported guard above.
- **Top bar inside the adaptive dialog (decision, not a bug):** the screen keeps a normal
  `Scaffold` + `TopAppBar` at every width. Per `docs/adaptive-layouts.md` ("scene strategies over
  per-form-factor layouts"), an `adaptiveDialog` screen is **presentation-agnostic** — it does not
  branch on window size; the shared `AdaptiveDialogSceneStrategy` owns the dialog window, scrim, and
  fixed **640dp** card (`MAX_CARD_WIDTH`), inside which the standard chrome renders. This matches
  every existing adaptive surface (`EditProfile` — the canonical reference — and C2's `GroupDetails`
  / `ChatSettings`). A conditional dialog-title vs. app-bar treatment would couple the screen to its
  presentation mode and diverge from the established pattern, so it is intentionally **not** done.
  The body's `widthIn(max = 600.dp)` already sits inside the 640dp card, so the card never stretches
  past the content.
- Standalone loaders use `NubecitaWavyProgressIndicator` (per the #563 guard); the only raw spinner
  is the in-button create/mutation micro-spinner, marked `// nubecita-allow-raw-progress: in-button micro-spinner`.
- Copy uses the platform clipboard; Share builds an `Intent.ACTION_SEND` (`text/plain`,
  `EXTRA_TEXT = link.url`) wrapped in `Intent.createChooser`. Both are screen-side (no VM effect).

### Surface tokens

Screen canvas `surface`; the link card `surfaceContainerLow` (recessed inset role); per
`docs/design-system/surface-roles.md`. Every `Scaffold` sets `containerColor` explicitly.

## Error handling

All repository failures surface as a non-sticky **Snackbar** via `ManageJoinLinkEffect.ShowError`,
collected once in the outermost composable (single `LaunchedEffect`, child-coroutine launch so a
queued error isn't head-of-line blocked). Load failure is **sticky** via `ManageJoinLinkStatus.Error`
with a retry. Optimistic mutations roll back to the prior link on failure so the UI never shows a
state the server rejected. `CancellationException` is always rethrown in the repository.

## Testing

- **VM unit tests** (JUnit5 + MockK + Turbine + `MainDispatcherExtension`): load success
  (`Loaded(link)`) / empty (`Loaded(null)`) / error (`Error`) + retry; create success → `Loaded`;
  create failure → `ShowError` + stays `Loaded(null)`; enable/disable optimistic flip + reconcile +
  rollback-on-failure; single-field edit sends only the changed `AtField` (assert `joinRule` is
  `Missing` on a `requireApproval` flip and vice-versa); `joinRule`/`requireApproval` edit optimistic
  + reconcile + rollback; `mutationInFlight` guard drops a second concurrent tap (fake gate);
  **Unsupported guard** — with `Loaded(link.joinRule == Unsupported)`, every setting event is a no-op
  (no repo call, state unchanged).
- **Mapper test**: `JoinLinkView.toJoinLinkUi()` — URL composition, `enabled` derivation, `"anyone"`
  → `Anyone`, `"followedByOwner"` → `FollowedByOwner`, an **unknown value → `Unsupported`**, and
  `toWire()` round-trips the two supported values; timestamp parse.
- **NavKey test**: `ManageJoinLink` serializes/round-trips (mirrors `GroupJoinRequestsNavKeyTest`).
- **Screenshot tests** (component-level, light/dark): the link card **enabled**, **disabled**, and
  **Unsupported-rule** (banner + locked controls), plus the **create empty-state**. Unlike C2, there
  is no Paging, so these render statically in the recomposer-less screenshot host. Baselines under
  `src/screenshotTestProductionDebug/reference/`.
- Full gate: `spotlessCheck lint :app:checkSortDependencies testDebugUnitTest`,
  `validateProductionDebugScreenshotTest`, and a **compose-expert** review pass before PR (gate fires
  because the diff adds `@Composable`).

## Reuse summary

GroupDetails owner-gating + row pattern (C2 "Join requests"); the `adaptiveDialog()` scene strategy;
the assisted-VM + `@MainShell entry<>` wiring; the optimistic + in-flight-guard + rollback pattern;
`toMemberMgmtError()`; `getConvo`; the C2 effect-collector snackbar pattern;
`NubecitaWavyProgressIndicator` + the `nubecita-allow-raw-progress` marker; the screenshot
`FixtureClock`/`LocalClock` approach for `createdAt` relative time.

## Commit / branch conventions

Branch `feat/nubecita-hwix.8-join-link-management`. Conventional Commits, lowercase-leading subjects,
`Refs: nubecita-hwix.8` in the footer; `Closes: nubecita-hwix.8` in the **PR body only**.
