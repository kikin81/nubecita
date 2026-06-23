# Group chat Phase 3c-3b — joiner-side deep-link join

**Epic:** `nubecita-hwix` (Group chat support). **Sub-slice C3b** — the **final** slice — after A
(group details #561), B (member management #562), C1 (group creation #564), C2 (join requests #565),
C3a (join link management #566/#567). **bd:** `nubecita-hwix.9`. **Implementing this closes the epic.**

## Purpose

Let a user who taps a group invite link (`https://nubecita.app/group/join/{code}` — the format C3a's
owner-side share action generates) **preview** the group and **join** it (or **request to join**, when
the group requires approval), via atproto 9.4.0 `chat.bsky.group`.

## Scope

C3b only: the **joiner side**. The deep-link entry, the preview screen, and the join action. Out of
scope: anything owner-side (done in C3a/C2), and any new invite-URL format (C3a fixed it). No
unauthenticated preview path — the whole flow sits behind sign-in (see Auth-gating).

## SDK surface (atproto 9.4.0 — verified in the published model sources)

`io.github.kikin81.atproto.chat.bsky.group.GroupService`, constructed `GroupService(xrpcClient)`
(same as B/C1/C2/C3a). Both endpoints are keyed by the opaque link **`code`**, not `convoId`.

- `getGroupPublicInfo(GetGroupPublicInfoRequest(code: String))` →
  `GetGroupPublicInfoResponse(group: GroupPublicView)`.
- `GroupPublicView(memberCount: Long, name: String, owner: ProfileViewBasic, requireApproval: Boolean)`.
  `ProfileViewBasic` exposes `handle: Handle`, `displayName: String?`, `avatar: Uri?` (`.raw` for the
  string forms).
- `requestJoin(RequestJoinRequest(code: String))` → `RequestJoinResponse(convo: ConvoView? = null, status: String)`.
  **Discriminator:** `convo != null` ⇒ the user joined directly (use `convo.id`); `convo == null` ⇒
  the request is pending owner approval (it lands in C2's approve/reject queue). `status` is
  informational and not branched on.

## Architecture

### Deep-link entry (minimal — no `MainActivity` change)

- **Manifest:** add a `<data android:scheme="https" android:host="nubecita.app" android:pathPrefix="/group/join/" />`
  element to the existing **verified** `nubecita.app` App Links `<intent-filter>` (mirrors the
  `/profile/` entry; `assetlinks.json` already covers the whole domain, so no Digital Asset Links
  change is needed).
- **NavKey:** `@Serializable data class GroupJoinPreview(val code: String) : NavKey` in
  `:feature:chats:api`.
- **Matcher:** a `GroupJoinDeepLinkModule` (`@Provides @IntoSet NavKeyDeepLinkMatcher`) using
  `uriDeepLinkMatcher(uriPattern = "https://nubecita.app/group/join/{code}", serializer = GroupJoinPreview.serializer(), filters = listOf(IntentActionFilter(Intent.ACTION_VIEW)), accept = { it.code.isNotBlank() })`.
  Because the `{code}` placeholder maps **directly** to the route's only field, the matcher returns the
  final `GroupJoinPreview` (no intermediate transport key, unlike `PostDeepLinkKey`). `MainActivity.handleIntent`'s
  existing generic `else -> matched` branch publishes it to the `DeepLinkRouter` unchanged — **no
  `MainActivity` edit required**. Mirrors `ChatDeepLinkModule`.
- **Auth-gating is automatic.** `DeepLinkRouter` is a Hilt singleton over a `Channel(BUFFERED)`, and
  `MainShell` is composed only when `SessionState.SignedIn`. A link tapped while signed-out is
  published into the buffer, held through the login flow, and drained by `MainShell`'s
  `pendingDeepLinks` collector when it enters composition — the same proven path as `OAuthRedirectBroker`.
  No new buffering or session check is written.

### Repository (`ChatRepository` + `DefaultChatRepository` + the fakes)

```kotlin
suspend fun getGroupPublicInfo(code: String): Result<GroupPublicInfoUi>
suspend fun requestJoin(code: String): Result<JoinResult>
```

- `getGroupPublicInfo` → `GroupService(client).getGroupPublicInfo(GetGroupPublicInfoRequest(code = code)).group`
  → `toGroupPublicInfoUi()`.
- `requestJoin` → `GroupService(client).requestJoin(RequestJoinRequest(code = code))`; map the response
  to `JoinResult.Joined(convo.id)` when `convo != null`, else `JoinResult.Pending`.
- Both wrap in the established style: rethrow `CancellationException`, log `throwable.javaClass.name`
  only (the `code` is a capability token — never log it), return `Result.failure`.
- All `ChatRepository` implementors updated (the test/androidTest/bench fakes + the inline polling-test
  fake), per the verify-all-implementors rule — the test fake gets settable results + call captures +
  a `requestJoinGate: CompletableDeferred<Unit>?`.

```kotlin
sealed interface JoinResult {
    data class Joined(val convoId: String) : JoinResult
    data object Pending : JoinResult
}
```

### Model + mapper (feature `:impl`, mirroring C3a's `JoinLinkUi` placement)

- `@Immutable data class GroupPublicInfoUi(name: String, memberCount: Int, ownerDisplayName: String?, ownerHandle: String, ownerAvatarUrl: String?, requireApproval: Boolean)`.
- `data/GroupPublicInfoMapper.kt`: `internal fun GroupPublicView.toGroupPublicInfoUi()` — `memberCount.toInt()`,
  `owner.handle.raw`, `owner.displayName?.takeUnless { it.isBlank() }`, `owner.avatar?.raw`.
- `JoinResult` lives in the same contract file (feature-internal).

### Contract + ViewModel

```kotlin
sealed interface GroupJoinPreviewStatus {
    data object Loading : GroupJoinPreviewStatus
    data class Loaded(val info: GroupPublicInfoUi) : GroupJoinPreviewStatus
    data class Error(val error: ChatError) : GroupJoinPreviewStatus
    data object RequestSent : GroupJoinPreviewStatus   // pending-approval confirmation
}

data class GroupJoinPreviewViewState(
    val status: GroupJoinPreviewStatus = GroupJoinPreviewStatus.Loading,
    val joinInFlight: Boolean = false,
) : UiState

sealed interface GroupJoinPreviewEvent : UiEvent {
    data object Retry : GroupJoinPreviewEvent
    data object JoinTapped : GroupJoinPreviewEvent
}

sealed interface GroupJoinPreviewEffect : UiEffect {
    data class ShowError(val error: ChatError) : GroupJoinPreviewEffect
    data class NavigateToConvo(val convoId: String) : GroupJoinPreviewEffect
}
```

- `@HiltViewModel(assistedFactory = Factory::class)`, `@AssistedInject(@Assisted route: GroupJoinPreview, repository: ChatRepository)`.
- **Load** on init: `getGroupPublicInfo(code)` → `Loaded(info)` / `Error(toJoinError())`. `Retry`
  reloads (back to `Loading`). A failed load most commonly means `InvalidCode` (a dead/typo'd link)
  → `ChatError.InvalidInviteLink`, surfaced as the sticky error body with retry.
- **Join** (`JoinTapped`, only valid from `Loaded`): guard on `joinInFlight`; set `joinInFlight = true`;
  `requestJoin(code)` → `Joined(convoId)` ⇒ `sendEffect(NavigateToConvo(convoId))`; `Pending` ⇒
  `setState { copy(status = RequestSent) }`; failure ⇒ `sendEffect(ShowError(it.toJoinError()))`;
  `finally joinInFlight = false`.

  **"Already a member" note:** 9.4.0 defines **no** `AlreadyMember` error and `GroupPublicView` carries
  no viewer-membership flag, so it can't be detected up front (and there's no `convoId` to power an
  "Open Chat" affordance — only `requestJoin`'s response has the convo). The expected server behavior
  is idempotent: an existing member's `requestJoin` returns `status=joined` + the convo, which the
  `Joined → NavigateToConvo` path already routes into the group. No dedicated state is built.

### Screen + components (adaptiveDialog)

- `GroupJoinPreviewScreen` (stateful: collects state + effects via the C2 child-coroutine snackbar
  pattern; `NavigateToConvo` → `onJoined(convoId)`) delegating to a stateless
  `GroupJoinPreviewScreenContent`.
- `Scaffold(containerColor = surface)` + `TopAppBar` (title "Join group" + close nav icon), body
  `widthIn(max = 600.dp)` centered, branching on `status`:
  - `Loading` → centered `NubecitaWavyProgressIndicator`.
  - `Error` → centered message + retry `TextButton`.
  - `Loaded(info)` → **preview**: the group details are wrapped in a prominent `surfaceContainerLow`
    `Card` (rounded corners) so the invite reads as an invitation rather than raw text — inside it the
    group `name` (titleLarge), "`{memberCount}` members", and an owner row (`NubecitaAvatar` + display
    name + `@handle`). A `requireApproval` note ("New members are approved by the group owner.") shows
    below the card only when true. Below that, a primary `Button` labeled **Join** (`requireApproval == false`)
    or **Request to join** (`true`); while `joinInFlight` it is disabled and hosts an in-button
    `CircularProgressIndicator(18.dp)` marked `// nubecita-allow-raw-progress: in-button micro-spinner`.
  - `RequestSent` → a centered **hero** confirmation (M3 Expressive success state): a `Check` glyph in a
    large ~96.dp `primaryContainer` circle (tint `onPrimaryContainer`) above "Request sent" (titleMedium)
    and a body line ("The group owner will review your request."), then a **Done** `Button` that closes
    the screen (`onClose`).
- Standalone loaders use `NubecitaWavyProgressIndicator`; the only raw spinner is the in-button one
  (marked). Surface tokens per `docs/design-system/surface-roles.md` (canvas `surface`; preview card
  `surfaceContainerLow`).

### Two deliberate decisions (so they aren't re-raised in review)

- **`Scaffold` + `TopAppBar` are kept at every width** (not stripped to a bare dialog title at
  Medium/Expanded). Per `docs/adaptive-layouts.md`, an `adaptiveDialog` screen is
  presentation-agnostic — the shared `AdaptiveDialogSceneStrategy` owns the dialog window / scrim /
  640dp card, inside which the standard chrome renders. This matches `EditProfile` (the canonical
  reference) and C2/C3a. A conditional dialog-title treatment would couple the screen to its
  presentation mode and diverge from every existing adaptive surface. (Same decision as C3a.)
- **No blank background on cold-start.** `MainShell` builds its inner stack with
  `rememberMainShellNavState(startRoute = Feed)`, so **Feed is always the root** beneath any pushed
  route, and `AdaptiveDialogScene` keeps `overlaidEntries` composed — so a cold-start deep link (app
  dead → login → drain) renders the preview dialog *over Feed*, never over a blank/black screen. No
  default-root insertion is needed; the existing nav architecture guarantees it.

### Navigation wiring

`ChatsNavigationModule`: `entry<GroupJoinPreview>(metadata = adaptiveDialog())` with the assisted VM,
`onJoined = { convoId -> navState.replaceTop(Chat(convoId = convoId)) }` (swap the preview for the
group thread — mirrors C1's post-create navigation), `onClose = { navState.removeLast() }`. No
result-passing.

## Error handling

Repository failures surface as a non-sticky **Snackbar** via `GroupJoinPreviewEffect.ShowError`,
collected once in the outermost composable (single `LaunchedEffect`, child-coroutine launch). Initial
load failure is **sticky** via `GroupJoinPreviewStatus.Error` + retry. `CancellationException` is
always rethrown; the link `code` is never logged. A join that fails leaves the user on the preview so
they can retry.

### Joiner-specific error mapping (`toJoinError()`)

`requestJoin` defines six errors and `getGroupPublicInfo` one (`InvalidCode`). The DM-oriented
`toMemberMgmtError()` doesn't cover the joiner cases, so add a `Throwable.toJoinError(): ChatError`
mapper (next to it in `ChatsErrorMapping.kt`) keying on `errorName` (lowercased, `Locale.ROOT`),
falling through to `toChatError()`. This matters concretely: C3a lets owners **disable** a link, so
joiners will routinely hit `LinkDisabled`/`InvalidCode` and deserve clear copy, not a generic failure.

| Wire error (`requestJoin` / `getGroupPublicInfo`) | `ChatError` | UX copy intent |
|---|---|---|
| `InvalidCode`, `LinkDisabled` | **`InvalidInviteLink`** (new) | "This invite link is invalid or no longer active." |
| `MemberLimitReached` | `GroupFull` (existing) | the group is full |
| `FollowRequired` | **`FollowRequiredToJoin`** (new) | "Only people the group's owner follows can join." |
| `UserKicked` | **`CannotRejoin`** (new) | "You can't rejoin a group you were removed from." |
| `ConvoLocked`, anything else | `Unknown` → generic | generic retryable failure |

Three new `ChatError` variants (`InvalidInviteLink`, `FollowRequiredToJoin`, `CannotRejoin`) are added
to the existing `sealed interface ChatError` in `ChatContract.kt`. The `GroupJoinPreviewScreen` effect
collector pre-resolves these three plus the existing `GroupFull`/generic strings (mirrors C2/C3a's
collector) and maps them to snackbar / sticky-error text.

## Testing

- **VM unit tests** (JUnit5 + MockK + Turbine + `MainDispatcherExtension`): load success / error +
  retry; `JoinTapped` → `Joined` → `NavigateToConvo(convoId)`; `JoinTapped` → `Pending` → `status = RequestSent`;
  join failure → `ShowError` + stays `Loaded`; `joinInFlight` guard drops a second concurrent tap
  (fake gate); `joinInFlight` cleared after success and after failure.
- **Mapper test**: `GroupPublicView.toGroupPublicInfoUi()` — `memberCount.toInt()`, owner handle/display/avatar
  mapping (incl. blank display name → null), `requireApproval` passthrough.
- **`toJoinError()` test**: `InvalidCode`/`LinkDisabled` → `InvalidInviteLink`, `MemberLimitReached` →
  `GroupFull`, `FollowRequired` → `FollowRequiredToJoin`, `UserKicked` → `CannotRejoin`, an unknown
  errorName → `Unknown`, `IOException` → `Network` (drive via `XrpcError` fakes keyed on `errorName`).
- **NavKey test**: `GroupJoinPreview` JSON round-trip + `convoId`/`code` field (mirrors `ManageJoinLinkNavKeyTest`,
  in `:feature:chats:impl/src/test`).
- **Deep-link matcher test**: feed `https://nubecita.app/group/join/abc123` through the matcher →
  `GroupJoinPreview(code = "abc123")`; a blank/missing code is rejected; a non-VIEW action is rejected.
- **Screenshot tests** (component-level, light/dark, statically renderable): the preview body in
  **Join** and **Request-to-join** variants, and the **RequestSent** confirmation. Baselines under
  `src/screenshotTestProductionDebug/reference/` (CI-regenerated via the `update-baselines` label).
- Full gate: `spotlessCheck lint :app:checkSortDependencies testDebugUnitTest`,
  `validateProductionDebugScreenshotTest`, the progress-indicator guard, a **compose-expert** review,
  and a **`/gemini review`** comment posted after the implementation is pushed (so Gemini reviews the
  code, not just the spec — see the gemini-review-only-on-pr-open learning).

## Reuse summary

The `:core:common:navigation` deep-link infra (`uriDeepLinkMatcher` / `NavKeyDeepLinkMatcher` /
`IntentActionFilter`), `MainActivity.handleIntent`'s generic publish path + the buffered
`DeepLinkRouter` + `MainShell`'s drain (auth-gating for free), `ChatDeepLinkModule` as the matcher
template, the `adaptiveDialog()` scene strategy, the assisted-VM + `@MainShell entry<>` wiring,
`replaceTop(Chat(convoId))` (from C1), the optimistic/in-flight-guard pattern, the C2 effect-collector
snackbar pattern, `toMemberMgmtError()`, `NubecitaAvatar`, `NubecitaWavyProgressIndicator` + the
`nubecita-allow-raw-progress` marker.

## Commit / branch conventions

Branch `feat/nubecita-hwix.9-deep-link-join`. Conventional Commits, lowercase-leading subjects,
`Refs: nubecita-hwix.9` in the footer; `Closes: nubecita-hwix.9` in the **PR body only**.
