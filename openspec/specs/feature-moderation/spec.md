# feature-moderation Specification

## Purpose
The report flow: the `Report` `@MainShell` sub-route and its `ReportSubject` sum, the reason taxonomy mapped onto the dialog's category cards, the stepped `ReportDialogViewModel` with derived submit-enablement and step-wise back handling, submission via `com.atproto.moderation.createReport`, and the post- and profile-overflow entry points that route into it.
## Requirements
### Requirement: `:feature:moderation:api` exposes the `Report` NavKey + `ReportSubject` sealed sum

The system SHALL provide `:feature:moderation:api` declaring `@Serializable Report(subject: ReportSubject) : NavKey` and sealed `ReportSubject` with variants `Post(uri: String, cid: String)` and `Account(did: String)`. Identifiers MUST be plain `String`s. The module MUST NOT depend on Hilt, Compose, Coil, or `:feature:moderation:impl`, depending only on `androidx.navigation3.runtime` and `kotlinx.serialization.json`.

#### Scenario: Caller constructs a post report NavKey without `:impl` dependency

- **WHEN** a feature module (e.g. `:feature:feed:impl`) needs to navigate to the report dialog for a specific post and declares `implementation(project(":feature:moderation:api"))` only (not `:impl`)
- **THEN** the module compiles successfully and can construct `Report(subject = ReportSubject.Post(uri = post.uri, cid = post.cid))` and push it onto `LocalMainShellNavState.current`

#### Scenario: Caller constructs an account report NavKey

- **WHEN** a feature module needs to navigate to the report dialog for a specific account and constructs `Report(subject = ReportSubject.Account(did = profileHeader.did))`
- **THEN** the resulting `NavKey` is serializable (round-trips through process death) and renders the report dialog for that account when pushed onto `LocalMainShellNavState.current`

### Requirement: `:feature:moderation:impl` registers a `@MainShell` `EntryProviderInstaller` for the `Report` NavKey

The system SHALL provide `:feature:moderation:impl` with a Hilt `@MainShell EntryProviderInstaller` binding registering an entry for `Report`. The entry MUST render a `ModalBottomSheet` hosting the report dialog content without `listPane` or `detailPane` metadata, popping `Report` off `LocalMainShellNavState.current` on dismissal.

#### Scenario: Report sub-route renders on push

- **WHEN** any caller invokes `LocalMainShellNavState.current.add(Report(subject = ...))` and `:feature:moderation:impl` is present in the build
- **THEN** `MainShell`'s inner `NavDisplay` resolves the entry to the `:feature:moderation:impl` provider and renders the report dialog as a Modal Bottom Sheet

#### Scenario: Sheet dismissal pops the sub-route

- **WHEN** the user taps outside the sheet, swipes the sheet down, or presses Back from the Subject step
- **THEN** the `ModalBottomSheet`'s `onDismissRequest` fires and the entry provider pops the `Report` NavKey off `LocalMainShellNavState.current`

### Requirement: `ReportReasons` exposes granular `tools.ozone.report.defs` and legacy `com.atproto.moderation.defs` tokens as `String` constants

`ReportReasons` in `:feature:moderation:impl` SHALL expose canonical reason string constants for `tools.ozone.report.defs` (Violence, Sexual, ChildSafety, Harassment, Misleading, RuleViolation, SelfHarm, Appeal, and Other) and legacy `com.atproto.moderation.defs` fallback tokens (such as `REASON_LEGACY_SPAM`). It SHALL expose `OTHER_REPORT_REASONS: Set<String>` containing all granular `*Other` tokens and top-level `REASON_OTHER`.

#### Scenario: `OTHER_REPORT_REASONS` contains exactly the granular `*Other` tokens plus the top-level granular `reasonOther`

- **WHEN** application code references `ReportReasons.OTHER_REPORT_REASONS`
- **THEN** the set contains exactly these 8 values: `REASON_VIOLENCE_OTHER`, `REASON_SEXUAL_OTHER`, `REASON_CHILD_SAFETY_OTHER`, `REASON_HARASSMENT_OTHER`, `REASON_MISLEADING_OTHER`, `REASON_RULE_OTHER`, `REASON_SELF_HARM_OTHER`, `REASON_OTHER` — no more, no fewer

#### Scenario: Granular CSAM constant matches canonical lexicon string verbatim

- **WHEN** the test suite reads `ReportReasons.REASON_CHILD_SAFETY_CSAM`
- **THEN** the value SHALL be the exact string `"tools.ozone.report.defs#reasonChildSafetyCSAM"` (case-sensitive, prefix-sensitive — `CSAM` is uppercase per the lexicon)

#### Scenario: Legacy Spam constant maps to the `com.atproto.moderation.defs` namespace

- **WHEN** the test suite reads `ReportReasons.REASON_LEGACY_SPAM`
- **THEN** the value SHALL be the exact string `"com.atproto.moderation.defs#reasonSpam"`

### Requirement: `ReportCategory` sealed sum models the 9 dialog cards and their child reasons

The system SHALL define sealed `ReportCategory` with 9 variants: `Spam`, `Sexual`, `Violence`, `ChildSafety`, `Harassment`, `Misleading`, `RuleViolation`, `SelfHarm`, and `Other`. Each variant SHALL expose `reasons: List<String>` matching lexicon reason tokens. `Spam` SHALL contain `[REASON_LEGACY_SPAM]` bypassing the sub-reason step; `Other` SHALL contain `[REASON_OTHER]`.

#### Scenario: ChildSafety category exposes the five granular child-safety reasons in lexicon order

- **WHEN** the UI iterates `ReportCategory.ChildSafety.reasons`
- **THEN** the list contains exactly `[REASON_CHILD_SAFETY_CSAM, REASON_CHILD_SAFETY_GROOM, REASON_CHILD_SAFETY_PRIVACY, REASON_CHILD_SAFETY_HARASSMENT, REASON_CHILD_SAFETY_OTHER]` in that order

#### Scenario: Spam category uses the legacy reason token and has no sub-reason step

- **WHEN** the UI iterates `ReportCategory.Spam.reasons`
- **THEN** the list contains exactly `[REASON_LEGACY_SPAM]` — Spam submits the legacy spam token directly and the dialog bypasses the SubReason step

#### Scenario: Other category is the granular catch-all

- **WHEN** the UI iterates `ReportCategory.Other.reasons`
- **THEN** the list contains exactly `[REASON_OTHER]` — equivalent to selecting a `*Other` granular reason from any other category (forces the Details required gate)

### Requirement: `ReportDialogViewModel` extends `MviViewModel` with a sealed `ReportDialogStep`

`ReportDialogViewModel` SHALL extend `MviViewModel<ReportDialogState, ReportDialogEvent, ReportDialogEffect>`. `ReportDialogState` MUST contain `subject: ReportSubject`, optional `subjectPreview: SubjectPreview?`, sealed `step: ReportDialogStep` (`Subject | Category | SubReason | Details`), `selectedCategory: ReportCategory?`, `selectedReason: String?`, `details: String`, `detailsRequired: Boolean`, and sealed `submission: SubmissionStatus`. Flat booleans MUST NOT represent steps or submission.

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

- **WHEN** the user is on `step = SubReason` for `ReportCategory.Violence` and dispatches `ReportDialogEvent.OnReasonSelected(ReportReasons.REASON_VIOLENCE_GRAPHIC_CONTENT)`
- **THEN** the emitted state has `selectedReason = REASON_VIOLENCE_GRAPHIC_CONTENT`, `detailsRequired = false`, and `step = Details` (the details textarea is shown but the Submit CTA is enabled even with `details = ""`)

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

`ModerationRepository` SHALL provide `suspend fun reportPost(uri: String, cid: String, reasonToken: String, details: String?): Result<Unit>` and `suspend fun reportAccount(did: String, reasonToken: String, details: String?): Result<Unit>`. It MUST invoke `createReport` with `reasonToken`, subject `StrongRef` (post) or `RepoRef` (account), details truncated to 2000 graphemes, and modTool `CreateReportModTool(name = "nubecita/android")`.

#### Scenario: `reportPost` uses `StrongRef` subject

- **WHEN** a caller invokes `repository.reportPost(uri = "at://...", cid = "bafy...", reasonToken = REASON_LEGACY_SPAM, details = null)`
- **THEN** the SDK call's `request.subject` is a `StrongRef(uri = "at://...", cid = "bafy...")` (NOT a `RepoRef`), `request.reasonType == REASON_LEGACY_SPAM`, `request.reason` is `AtField.Missing`, and `request.modTool.name == "nubecita/android"`

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

On successful submission, `ReportDialogViewModel` SHALL transition `submission` to `SubmissionStatus.Success`. The dialog SHALL replace form content with a success card for approximately 2.5 seconds (longer if accessibility is enabled), then emit `ReportDialogEffect.RequestDismiss` to pop the `Report` NavKey. No separate snackbar SHALL be surfaced by host screens.

#### Scenario: Submission success renders the success card

- **WHEN** the user submits a valid report and the repository returns `Result.success(Unit)`
- **THEN** `state.submission is SubmissionStatus.Success` and the dialog Composable renders the success card in place of the form

#### Scenario: Success auto-dismiss after the timer

- **WHEN** the success card has been rendered for approximately 2.5 seconds (or longer under TalkBack) without user interaction
- **THEN** the VM emits `ReportDialogEffect.RequestDismiss` and the screen pops the `Report` NavKey off `LocalMainShellNavState.current`

### Requirement: Submission failure renders an inline error banner; the form retains selection for retry

On submission failure, `ReportDialogViewModel` SHALL transition `submission` to `SubmissionStatus.Failed(message)` using the localized failure message or a fallback string. The dialog SHALL display an inline error banner above the Submit button while preserving `selectedCategory`, `selectedReason`, and `details` for retry.

#### Scenario: Submission failure preserves form state

- **WHEN** the user submits a report with `selectedReason = REASON_VIOLENCE_GRAPHIC_CONTENT`, `details = "context"`, and the repository returns `Result.failure(IOException("..."))`
- **THEN** the emitted state has `submission is SubmissionStatus.Failed`, `selectedReason == REASON_VIOLENCE_GRAPHIC_CONTENT`, `details == "context"`, `step == Details`; the inline error banner displays the failure message

#### Scenario: Retry re-attempts submission

- **WHEN** the user is in the `Failed` state and taps Submit again
- **THEN** the VM transitions `submission` to `Submitting` and re-invokes the repository with the same `subject` / `reasonToken` / `details` values

### Requirement: Back-button collapses through dialog steps; Back from `Subject` dismisses the sub-route

The dialog SHALL register a `BackHandler` enabled when `state.step != ReportDialogStep.Subject`. On back press, the VM MUST step backward (`Details → SubReason → Category → Subject`), clearing the field from the abandoned step. When `step == ReportDialogStep.Subject`, `BackHandler` MUST be disabled, allowing system back to trigger `onDismissRequest` and pop `Report`.

#### Scenario: Back from Details returns to SubReason and clears details

- **WHEN** the user is on `step = Details` with `selectedReason = REASON_RULE_OTHER` and `details = "they're banned"`, and presses Back
- **THEN** the emitted state has `step = SubReason`, `selectedReason = REASON_RULE_OTHER` preserved, `details = ""` cleared

#### Scenario: Back from Subject dismisses the dialog

- **WHEN** the user is on `step = Subject` and presses Back
- **THEN** the `BackHandler` does NOT consume the press; the system propagates to the `ModalBottomSheet`'s `onDismissRequest`; the entry provider pops `Report` off `LocalMainShellNavState.current`

### Requirement: PostCard overflow Report row routes to the Report dialog via `LocalMainShellNavState`

When `PostOverflowAction.ReportPost` is tapped in the PostCard overflow menu, the host ViewModel SHALL emit a navigation effect with `Report(subject = ReportSubject.Post(uri = post.uri, cid = post.cid))`. The screen collector MUST push the NavKey onto `LocalMainShellNavState.current`. Hosts MUST NOT show inline dialogs or snackbars.

#### Scenario: Feed VM routes the Report overflow action

- **WHEN** the Feed screen dispatches a `FeedEvent` representing the user tapping `PostOverflowAction.ReportPost` for a post with `uri = "at://...abc"` and `cid = "bafy123"`
- **THEN** the FeedViewModel emits exactly one `FeedEffect.NavigateTo(Report(subject = ReportSubject.Post(uri = "at://...abc", cid = "bafy123")))`. The Feed screen's effect collector calls `LocalMainShellNavState.current.add(...)` with that key. No `FeedEffect.ShowError`, `ShowMessage`, or inline modal is emitted.

### Requirement: ProfileHero overflow Report row routes to the Report dialog and removes the snackbar stub

`ProfileEffect.ShowComingSoon(StubbedAction.Report)` and `R.string.profile_snackbar_report_coming_soon` SHALL be removed from `:feature:profile:impl`. Tapping Report on ProfileHero SHALL emit `ProfileEffect.NavigateTo(Report(subject = ReportSubject.Account(did = profileHeader.did)))`, and the screen collector MUST push the NavKey onto `LocalMainShellNavState.current`.

#### Scenario: Profile VM routes the Report tap to navigation

- **WHEN** the Profile screen dispatches the event corresponding to the user tapping the "Report account" overflow row on a profile with `did = "did:plc:xxx"`
- **THEN** the ProfileViewModel emits exactly one `ProfileEffect.NavigateTo(Report(subject = ReportSubject.Account(did = "did:plc:xxx")))`. No `ProfileEffect.ShowComingSoon` is emitted for this action. The Profile screen's effect collector calls `LocalMainShellNavState.current.add(...)`.

#### Scenario: Snackbar resource is removed

- **WHEN** the build is compiled
- **THEN** `R.string.profile_snackbar_report_coming_soon` does NOT resolve from `:feature:profile:impl`'s `strings.xml`. The `StubbedAction` enum does NOT contain a `Report` constant.

#### Scenario: Block / Mute / Edit rows still surface their coming-soon snackbars

- **WHEN** the user taps the Block row on a profile (which remains stubbed)
- **THEN** the ProfileViewModel emits `ProfileEffect.ShowComingSoon(StubbedAction.Block)` — unchanged from prior behavior. Same for `Edit` and `Mute`.
