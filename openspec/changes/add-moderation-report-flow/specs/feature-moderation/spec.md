## ADDED Requirements

### Requirement: `:feature:moderation:api` exposes the `Report` NavKey + `ReportSubject` sealed sum

The system SHALL ship a new `:feature:moderation:api` Android library module. The module SHALL define a single `@Serializable Report(subject: ReportSubject) : NavKey` and a sealed `ReportSubject` with exactly two variants: `data class Post(uri: String, cid: String) : ReportSubject` and `data class Account(did: String) : ReportSubject`. The `uri` / `cid` / `did` fields MUST be plain `String`s (not the atproto SDK's generated value-class types and not typealiases) — matching the codebase's established convention (see `PostDetailRoute.postUri` and `PostUi.id` / `PostUi.cid`); consumers wrap to lexicon-typed values at the XRPC boundary. The `:api` module MUST NOT depend on Hilt, Compose, Coil, or any `:feature:moderation:impl` symbol; it depends only on `androidx.navigation3.runtime` (for `NavKey`) and `kotlinx.serialization.json`.

#### Scenario: Caller constructs a post report NavKey without `:impl` dependency

- **WHEN** a feature module (e.g. `:feature:feed:impl`) needs to navigate to the report dialog for a specific post and declares `implementation(project(":feature:moderation:api"))` only (not `:impl`)
- **THEN** the module compiles successfully and can construct `Report(subject = ReportSubject.Post(uri = post.uri, cid = post.cid))` and push it onto `LocalMainShellNavState.current`

#### Scenario: Caller constructs an account report NavKey

- **WHEN** a feature module needs to navigate to the report dialog for a specific account and constructs `Report(subject = ReportSubject.Account(did = profileHeader.did))`
- **THEN** the resulting `NavKey` is serializable (round-trips through process death) and renders the report dialog for that account when pushed onto `LocalMainShellNavState.current`

### Requirement: `:feature:moderation:impl` registers a `@MainShell` `EntryProviderInstaller` for the `Report` NavKey

The system SHALL ship a new `:feature:moderation:impl` Android library module applying the `nubecita.android.feature` convention plugin. The module SHALL provide a Hilt `@Provides @IntoSet @MainShell EntryProviderInstaller` binding that registers an entry for the `Report` NavKey declared in `:feature:moderation:api`. The entry MUST render an `androidx.compose.material3.ModalBottomSheet` hosting the report dialog content. The entry MUST NOT carry `ListDetailSceneStrategy.listPane { }` or `detailPane { }` metadata — the Report sub-route is a transient overlay that the scene strategy resolves wherever it fits.

#### Scenario: Report sub-route renders on push

- **WHEN** any caller invokes `LocalMainShellNavState.current.add(Report(subject = ...))` and `:feature:moderation:impl` is present in the build
- **THEN** `MainShell`'s inner `NavDisplay` resolves the entry to the `:feature:moderation:impl` provider and renders the report dialog as a Modal Bottom Sheet

#### Scenario: Sheet dismissal pops the sub-route

- **WHEN** the user taps outside the sheet, swipes the sheet down, or presses Back from the Subject step
- **THEN** the `ModalBottomSheet`'s `onDismissRequest` fires and the entry provider pops the `Report` NavKey off `LocalMainShellNavState.current`

### Requirement: `ReportReasons` exposes granular `tools.ozone.report.defs` and legacy `com.atproto.moderation.defs` tokens as `String` constants

The system SHALL ship a `ReportReasons` Kotlin `object` (in `:feature:moderation:impl`) holding each granular `tools.ozone.report.defs` reason and each legacy `com.atproto.moderation.defs` fallback reason as a `const val String` whose value matches the canonical lexicon token string. The object MUST include at minimum: `REASON_SPAM`, `REASON_SEXUAL_ABUSE`, `REASON_SEXUAL_NCII`, `REASON_SEXUAL_DEEPFAKE`, `REASON_SEXUAL_OTHER`, `REASON_VIOLENCE_ANIMAL`, `REASON_VIOLENCE_THREATS`, `REASON_VIOLENCE_GRAPHIC`, `REASON_VIOLENCE_OTHER`, `REASON_CHILD_SAFETY_CSAM`, `REASON_CHILD_SAFETY_GROOM`, `REASON_CHILD_SAFETY_MINOR`, `REASON_CHILD_SAFETY_OTHER`, `REASON_HARASSMENT_TROLL`, `REASON_HARASSMENT_TARGETED`, `REASON_HARASSMENT_HATE`, `REASON_HARASSMENT_OTHER`, `REASON_MISLEADING_BOT`, `REASON_MISLEADING_IMPERSONATION`, `REASON_MISLEADING_SCAM`, `REASON_MISLEADING_SYNTHETIC`, `REASON_MISLEADING_MANIPULATED`, `REASON_MISLEADING_OTHER`, `REASON_RULE_SITE_SECURITY`, `REASON_RULE_BAN_EVASION`, `REASON_RULE_OTHER`, `REASON_SELF_HARM_SUICIDE`, `REASON_SELF_HARM_OTHER`, `REASON_OTHER`, and the legacy fallbacks `REASON_SPAM_LEGACY`, `REASON_SEXUAL_LEGACY`, `REASON_RUDE_LEGACY`, `REASON_VIOLATION_LEGACY`, `REASON_MISLEADING_LEGACY`. The object SHALL also expose `OTHER_REPORT_REASONS: Set<String>` containing every `*_OTHER`-suffixed token plus `REASON_OTHER`.

#### Scenario: `OTHER_REPORT_REASONS` contains exactly the `_OTHER` tokens

- **WHEN** application code references `ReportReasons.OTHER_REPORT_REASONS`
- **THEN** the set contains `REASON_SEXUAL_OTHER`, `REASON_VIOLENCE_OTHER`, `REASON_CHILD_SAFETY_OTHER`, `REASON_HARASSMENT_OTHER`, `REASON_MISLEADING_OTHER`, `REASON_RULE_OTHER`, `REASON_SELF_HARM_OTHER`, and `REASON_OTHER` — no more, no fewer

#### Scenario: Constants match canonical lexicon strings

- **WHEN** the test suite reads `ReportReasons.REASON_CHILD_SAFETY_CSAM`
- **THEN** the value SHALL be the exact string `tools.ozone.report.defs#reasonChildSafetyCSAM` (or whichever canonical token the upstream lexicon uses — case- and prefix-sensitive)

### Requirement: `ReportCategory` sealed sum models the 9 dialog cards and their child reasons

The system's UI / VM layer SHALL define a sealed `ReportCategory` with exactly 9 variants: `Spam`, `Sexual`, `Violence`, `ChildSafety`, `Harassment`, `Misleading`, `RuleViolation`, `SelfHarm`, `Other`. Each variant SHALL expose a `reasons: List<String>` property whose entries are token strings from `ReportReasons`. The `Other` variant's `reasons` list MUST be a single-element list containing `REASON_OTHER` (the fallback). Sub-reason ordering within each category MUST match the order specified in the change's design document (matching social-app's reference UI order).

#### Scenario: ChildSafety category exposes all four child-safety reasons

- **WHEN** the UI iterates `ReportCategory.ChildSafety.reasons`
- **THEN** the list contains exactly `[REASON_CHILD_SAFETY_CSAM, REASON_CHILD_SAFETY_GROOM, REASON_CHILD_SAFETY_MINOR, REASON_CHILD_SAFETY_OTHER]` in that order

#### Scenario: Spam category has no sub-reasons

- **WHEN** the UI iterates `ReportCategory.Spam.reasons`
- **THEN** the list contains exactly `[REASON_SPAM]` — Spam selects directly to submission without a sub-reason picker step

### Requirement: `ReportDialogViewModel` extends `MviViewModel` with a sealed `ReportDialogStep`

The system SHALL ship `ReportDialogViewModel` extending `net.kikin.nubecita.ui.mvi.MviViewModel<ReportDialogState, ReportDialogEvent, ReportDialogEffect>`. `ReportDialogState` MUST contain a `subject: ReportSubject` flat field (the navigated subject), an optional `subjectPreview: SubjectPreview?` flat field (null while resolving the subject's display name), a `step: ReportDialogStep` sealed sum (`Subject | Category | SubReason | Details`), `selectedCategory: ReportCategory?` and `selectedReason: String?` flat fields, a `details: String` flat field, a derived `detailsRequired: Boolean` flat field, and a `submission: SubmissionStatus` sealed sum (`Idle | Submitting | Success(sentAt: Instant) | Failed(message: String)`). The VM MUST NOT model the 4 steps as flat booleans and MUST NOT model the submission status as flat booleans — invalid step / submission combinations MUST be unrepresentable.

#### Scenario: Initial state for a post report

- **WHEN** the VM is constructed for `Report(subject = ReportSubject.Post(uri = "at://did:plc:xxx/app.bsky.feed.post/abc", cid = "bafy..."))`
- **THEN** the emitted initial state has `subject = ReportSubject.Post(...)`, `step = Subject`, `selectedCategory = null`, `selectedReason = null`, `details = ""`, `detailsRequired = false`, `submission = Idle`. `subjectPreview` MAY initially be null; the VM MUST start a side-coroutine that resolves the post into a `SubjectPreview` (author handle + 280-char snippet) and emits a state update when it completes.

#### Scenario: Step transitions are monotonic forward, monotonic backward

- **WHEN** the user is on `step = SubReason` and dispatches `ReportDialogEvent.OnBackPressed`
- **THEN** the emitted state has `step = Category` and `selectedReason = null`; `selectedCategory` is preserved

#### Scenario: Selecting an OTHER reason flips `detailsRequired`

- **WHEN** the user is on `step = SubReason` for `ReportCategory.Violence` and dispatches `ReportDialogEvent.OnReasonSelected(ReportReasons.REASON_VIOLENCE_OTHER)`
- **THEN** the emitted state has `selectedReason = REASON_VIOLENCE_OTHER`, `detailsRequired = true`, and `step = Details`

#### Scenario: Selecting a non-OTHER reason skips the required Details gate

- **WHEN** the user is on `step = SubReason` for `ReportCategory.Violence` and dispatches `ReportDialogEvent.OnReasonSelected(ReportReasons.REASON_VIOLENCE_GRAPHIC)`
- **THEN** the emitted state has `selectedReason = REASON_VIOLENCE_GRAPHIC`, `detailsRequired = false`, and `step = Details` (the details textarea is shown but the Submit CTA is enabled even with `details = ""`)

### Requirement: `canSubmit` is derived state — disabled until a reason is chosen and details satisfy validation

The system's `ReportDialogState` SHALL expose a derived `canSubmit: Boolean` flat field updated by the reducer on every event. `canSubmit` MUST be `true` if and only if all of the following hold: `selectedReason != null` AND (`!detailsRequired` OR `details.graphemeCount in 1..300`) AND `submission !is Submitting`. The host Composable's Submit CTA MUST read `state.canSubmit` and disable itself otherwise.

#### Scenario: Submit disabled until a reason is selected

- **WHEN** the user is on `step = Details`, `selectedReason = null`, `details = ""`
- **THEN** `state.canSubmit == false`

#### Scenario: Submit disabled when OTHER reason has empty details

- **WHEN** the user is on `step = Details`, `selectedReason = REASON_HARASSMENT_OTHER`, `details = ""`, `detailsRequired = true`
- **THEN** `state.canSubmit == false`

#### Scenario: Submit disabled during in-flight submission

- **WHEN** the user has tapped Submit and the VM has transitioned `submission` to `Submitting`
- **THEN** `state.canSubmit == false` regardless of other field values (prevents double-tap)

### Requirement: `ModerationRepository` submits `com.atproto.moderation.createReport` with the correct subject union variant

The system SHALL ship `ModerationRepository` (interface) with `suspend fun reportPost(uri: String, cid: String, reasonToken: String, details: String?): Result<Unit>` and `suspend fun reportAccount(did: String, reasonToken: String, details: String?): Result<Unit>`. `uri` / `cid` / `did` are plain `String`s carrying wire-format AT URIs / CIDs / DIDs; the implementation wraps them to lexicon-typed values at the XRPC boundary. `DefaultModerationRepository` MUST implement both by invoking `ModerationService(client).createReport(...)` with: (a) the `reasonType` field set to `reasonToken` verbatim, (b) the `subject` field set to a `StrongRef` constructed from `uri` + `cid` for `reportPost` and a `RepoRef` constructed from `did` for `reportAccount`, (c) the `reason` field set to `details` truncated to a maximum of 2000 graphemes (or `AtField.Absent()` when `details` is null or blank), and (d) the `modTool` field set to `CreateReportModTool(name = "nubecita/android", meta = AtField.Absent())`. Both methods MUST return `Result.success(Unit)` on a 2xx and `Result.failure(...)` on any thrown exception.

#### Scenario: `reportPost` uses `StrongRef` subject

- **WHEN** a caller invokes `repository.reportPost(uri = "at://...", cid = "bafy...", reasonToken = REASON_SPAM, details = null)`
- **THEN** the SDK call's `request.subject` is a `StrongRef(uri = "at://...", cid = "bafy...")` (NOT a `RepoRef`), `request.reasonType == REASON_SPAM`, `request.reason` is `AtField.Absent()`, and `request.modTool.name == "nubecita/android"`

#### Scenario: `reportAccount` uses `RepoRef` subject

- **WHEN** a caller invokes `repository.reportAccount(did = "did:plc:xxx", reasonToken = REASON_HARASSMENT_TARGETED, details = "context about the harassment")`
- **THEN** the SDK call's `request.subject` is a `RepoRef(did = "did:plc:xxx")` (NOT a `StrongRef`), `request.reasonType == REASON_HARASSMENT_TARGETED`, and `request.reason` carries `"context about the harassment"`

#### Scenario: `details` is truncated to the lexicon's 2000-grapheme cap before submission

- **WHEN** a caller invokes `reportPost` with a `details` string whose grapheme length is 3000
- **THEN** the SDK call's `request.reason` carries a string of exactly 2000 graphemes; the original 3000-grapheme value is NOT sent

#### Scenario: A transport failure returns `Result.failure`

- **WHEN** `ModerationService.createReport` throws a Ktor `HttpRequestTimeoutException`
- **THEN** the repository's `reportPost`/`reportAccount` method returns `Result.failure(<the same exception>)` — the exception is NOT swallowed

### Requirement: Successful submission renders an in-dialog success card before auto-dismiss

When `ReportDialogViewModel` receives a `ReportDialogEvent.OnSubmitClicked` and the underlying `ModerationRepository` call succeeds, the VM SHALL transition `submission` to `SubmissionStatus.Success(sentAt = <now>)`. The dialog Composable SHALL replace its form content with a success card identifying the report as submitted ("Report submitted. Thanks — Bluesky moderation will review it.") for approximately 2.5 seconds (longer when `AccessibilityManager.isEnabled`), then emit `ReportDialogEffect.RequestDismiss` which the screen translates to a pop of the `Report` NavKey. The host Feed or Profile screen MUST NOT receive a separate success snackbar — the success acknowledgement lives entirely inside the dialog.

#### Scenario: Submission success renders the success card

- **WHEN** the user submits a valid report and the repository returns `Result.success(Unit)`
- **THEN** `state.submission is SubmissionStatus.Success` and the dialog Composable renders the success card in place of the form

#### Scenario: Success auto-dismiss after the timer

- **WHEN** the success card has been rendered for approximately 2.5 seconds (or longer under TalkBack) without user interaction
- **THEN** the VM emits `ReportDialogEffect.RequestDismiss` and the screen pops the `Report` NavKey off `LocalMainShellNavState.current`

### Requirement: Submission failure renders an inline error banner; the form retains selection for retry

When `ReportDialogViewModel` receives `OnSubmitClicked` and the underlying `ModerationRepository` call returns `Result.failure(...)`, the VM SHALL transition `submission` to `SubmissionStatus.Failed(message)` where `message` is either the underlying exception's `localizedMessage` or — when null or blank — a generic fallback `"Couldn't submit report. Please try again."`. The dialog Composable SHALL render the message in an inline error banner above the Submit CTA. The form's `selectedCategory`, `selectedReason`, and `details` values MUST be preserved unchanged. Tapping Submit again MUST re-attempt the submission.

#### Scenario: Submission failure preserves form state

- **WHEN** the user submits a report with `selectedReason = REASON_VIOLENCE_GRAPHIC`, `details = "context"`, and the repository returns `Result.failure(IOException("..."))`
- **THEN** the emitted state has `submission is SubmissionStatus.Failed`, `selectedReason == REASON_VIOLENCE_GRAPHIC`, `details == "context"`, `step == Details`; the inline error banner displays the failure message

#### Scenario: Retry re-attempts submission

- **WHEN** the user is in the `Failed` state and taps Submit again
- **THEN** the VM transitions `submission` to `Submitting` and re-invokes the repository with the same `subject` / `reasonToken` / `details` values

### Requirement: Back-button collapses through dialog steps; Back from `Subject` dismisses the sub-route

The dialog Composable SHALL register an `androidx.activity.compose.BackHandler { ... }` whose enabled condition is `state.step != ReportDialogStep.Subject`. When enabled, the handler MUST dispatch `ReportDialogEvent.OnBackPressed`, which the VM reduces by transitioning `step` backward (Details → SubReason → Category → Subject) and clearing the field that was set during the abandoned step (e.g. `Details → SubReason` clears `details` to `""`; `SubReason → Category` clears `selectedReason` to null). When `state.step == ReportDialogStep.Subject`, the `BackHandler` MUST NOT be enabled, allowing the system back-press to fall through to the bottom sheet's `onDismissRequest` and pop the sub-route.

#### Scenario: Back from Details returns to SubReason and clears details

- **WHEN** the user is on `step = Details` with `selectedReason = REASON_RULE_OTHER` and `details = "they're banned"`, and presses Back
- **THEN** the emitted state has `step = SubReason`, `selectedReason = REASON_RULE_OTHER` preserved, `details = ""` cleared

#### Scenario: Back from Subject dismisses the dialog

- **WHEN** the user is on `step = Subject` and presses Back
- **THEN** the `BackHandler` does NOT consume the press; the system propagates to the `ModalBottomSheet`'s `onDismissRequest`; the entry provider pops `Report` off `LocalMainShellNavState.current`

### Requirement: PostCard overflow Report row routes to the Report dialog via `LocalMainShellNavState`

When a user activates the `PostOverflowAction.ReportPost` row in the PostCard overflow menu (from any host that wires the menu — Feed timeline, Profile tabs, PostDetail), the host's ViewModel SHALL emit a `NavigateTo` effect carrying `Report(subject = ReportSubject.Post(uri = post.uri, cid = post.cid))`. The screen's effect collector SHALL push the NavKey onto `LocalMainShellNavState.current`. Hosts MUST NOT render an inline modal, dialog, or snackbar in response to the Report row activation — the only correct response is navigation to the Report sub-route.

#### Scenario: Feed VM routes the Report overflow action

- **WHEN** the Feed screen dispatches a `FeedEvent` representing the user tapping `PostOverflowAction.ReportPost` for a post with `uri = "at://...abc"` and `cid = "bafy123"`
- **THEN** the FeedViewModel emits exactly one `FeedEffect.NavigateTo(Report(subject = ReportSubject.Post(uri = "at://...abc", cid = "bafy123")))`. The Feed screen's effect collector calls `LocalMainShellNavState.current.add(...)` with that key. No `FeedEffect.ShowError`, `ShowMessage`, or inline modal is emitted.

### Requirement: ProfileHero overflow Report row routes to the Report dialog and removes the snackbar stub

The system SHALL remove the `ProfileEffect.ShowComingSoon(StubbedAction.Report)` branch and the `R.string.profile_snackbar_report_coming_soon` string resource from `:feature:profile:impl`. The `StubbedAction` enum MUST no longer contain a `Report` variant. The Profile screen's overflow menu SHALL emit a new `ProfileEvent` variant (`OnReportAccountRequested` or its closest established name) that the `ProfileViewModel` reduces to `ProfileEffect.NavigateTo(Report(subject = ReportSubject.Account(did = profileHeader.did)))`. The Profile screen's effect collector SHALL push the resulting NavKey onto `LocalMainShellNavState.current`. The other `StubbedAction` variants (`Edit`, `Block`, `Mute`) MUST remain intact pending their own moderation-epic children.

#### Scenario: Profile VM routes the Report tap to navigation

- **WHEN** the Profile screen dispatches the event corresponding to the user tapping the "Report account" overflow row on a profile with `did = "did:plc:xxx"`
- **THEN** the ProfileViewModel emits exactly one `ProfileEffect.NavigateTo(Report(subject = ReportSubject.Account(did = "did:plc:xxx")))`. No `ProfileEffect.ShowComingSoon` is emitted for this action. The Profile screen's effect collector calls `LocalMainShellNavState.current.add(...)`.

#### Scenario: Snackbar resource is removed

- **WHEN** the build is compiled
- **THEN** `R.string.profile_snackbar_report_coming_soon` does NOT resolve from `:feature:profile:impl`'s `strings.xml`. The `StubbedAction` enum does NOT contain a `Report` constant.

#### Scenario: Block / Mute / Edit rows still surface their coming-soon snackbars

- **WHEN** the user taps the Block row on a profile (which remains stubbed)
- **THEN** the ProfileViewModel emits `ProfileEffect.ShowComingSoon(StubbedAction.Block)` — unchanged from prior behavior. Same for `Edit` and `Mute`.
