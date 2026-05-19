## Context

The Report flow is the first child of the `nubecita-oftc` epic that actually performs a server-side write. Two prerequisites already shipped:

- `nubecita-oftc.1` surfaced account-level moderation flags (`muted`, `blocking`, `blockedBy`) on `PostUi.ViewerStateUi` and `ProfileHeaderUi`. This change consumes none of those directly — but the same data layer underpins the eventual Block and Mute children that will delta the same capability.
- `nubecita-oftc.2` added the PostCard overflow menu — a `sealed interface PostOverflowAction` in `:designsystem/component/PostOverflowAction.kt` with a `ReportPost` variant whose `onAction(PostOverflowAction.ReportPost)` callback (see `:designsystem/component/PostCard.kt:449`) fires today but has no consumer on the feed VM side. The menu scaffolding is shippable; the wiring is not.

On the profile side, `:feature:profile:impl/ProfileContract.kt` already declares `ProfileEvent.StubActionTapped(action: StubbedAction)` (line 376) with `StubbedAction = Edit | Block | Mute | Report` (line 502). Tapping the Report row emits `ProfileEffect.ShowComingSoon(StubbedAction.Report)` and surfaces `R.string.profile_snackbar_report_coming_soon`. The Block / Mute / Edit rows stay stubbed for now — only Report graduates as part of this change.

The atproto-kotlin SDK (v5.5.0, pinned in `gradle/libs.versions.toml`) generates everything we need:

- `io.github.kikin81.atproto.com.atproto.moderation.ModerationService.createReport(request: CreateReportRequest): CreateReportResponse` (suspend; throws on transport / 4xx / 5xx).
- `CreateReportRequest(reasonType: ReasonType, subject: CreateReportRequestSubjectUnion, reason: AtField<String>, modTool: AtField<CreateReportModTool>)`.
- The subject union: `RepoRef(did: Did)` for accounts and `StrongRef(uri: AtUri, cid: Cid)` for posts.
- `CreateReportModTool(name: String, meta: AtField<JsonObject>)`.

Reason tokens are not generated as enums — they're string constants embedded in the lexicon. The bd issue spells out the granular `tools.ozone.report.defs` hierarchy plus the legacy `com.atproto.moderation.defs` fallback values (`reasonSpam`, `reasonOther`, …); this change is responsible for materializing them as Kotlin `String` constants under a `ReportReasons` object.

`MainShell` (in `:app`) hosts an inner `NavDisplay` with `rememberListDetailSceneStrategy` attached. Sub-routes pushed onto `LocalMainShellNavState.current.add(...)` render through that strategy: full-screen on Compact, in the detail pane on Medium / Expanded. The Report dialog is a sub-route — it gets adaptive layout for free with no feature-side scaffolding.

## Goals / Non-Goals

**Goals:**

- Wire the existing `PostOverflowAction.ReportPost` and `ProfileEvent` Report row into a real submitted `com.atproto.moderation.createReport` write, with the granular `tools.ozone.report.defs` reason hierarchy.
- Ship a new `:feature:moderation:impl` module that owns the dialog, the VM, the repository, and the reason model. Land an `:api` companion that exposes the `Report` NavKey and `ReportSubject` sealed sum so callers can route into the dialog without depending on `:impl`.
- Treat the dialog as a sub-route of `MainShell`'s inner `NavDisplay`, registered via a `@Provides @IntoSet @MainShell EntryProviderInstaller`. Reuse the existing adaptive scaffolding (`ListDetailSceneStrategy`) rather than building a feed-local modal.
- Remove the profile-side "Coming soon" snackbar for Report (the row, the event-handler branch, the `ShowComingSoon(Report)` effect path, and the `profile_snackbar_report_coming_soon` string resource).
- Establish screenshot-test fixtures across each dialog step + each category card in light and dark.
- Establish the pattern future moderation children (`oftc.4` Block, `oftc.5` Mute) will follow: same module (`:feature:moderation:impl`), same capability (`feature-moderation`), same nav-shell registration pattern.

**Non-Goals:**

- A labeler picker (which moderation service receives the report). V1 routes to Bluesky's default only; `app.bsky.labeler.getServices` resolution is a separate change.
- Reporting embedded media (images / videos) as subjects distinct from the parent post. The `createReport` subject union has no image-blob variant today.
- Reporting chat messages. Lexicon has no chat-message subject variant.
- The "Report this account" affordance from a post (bsky.app shows both "Report post" and "Report account" on a post overflow). V1 has "Report post" on PostCard and "Report account" only on the ProfileHero overflow.
- Persisting a local history of reports the user has submitted.
- An optimistic "mute this account after reporting" affordance. Defer to `oftc.5` (Mute).
- The Block and Mute flows — separate children that will delta `feature-moderation`, not this change.
- Pre-flight RPCs against `getReportOptions` / `getServices` to drive the picker dynamically — V1 uses hard-coded reason constants.
- Custom UI primitives (shape morphing, motion graphs, hand-rolled bottom-sheet anchors). Same baseline prohibition as every prior change: M3 / Compose / `:designsystem` only.

## Decisions

### Decision 1: Two-module split — `:feature:moderation:api` (NavKey + ReportSubject) + `:feature:moderation:impl` (dialog, VM, repo, ReportReasons)

**Choice:** Mirror the established `:feature:postdetail:{api,impl}` and `:feature:profile:{api,impl}` shape. `:feature:moderation:api` ships a single `@Serializable Report(subject: ReportSubject) : NavKey` and a sealed `ReportSubject` (`Post(uri: String, cid: String) : ReportSubject` | `Account(did: String) : ReportSubject`). The `uri` / `cid` / `did` fields are plain `String`s — matching the codebase's established convention (see `PostDetailRoute.postUri` and `PostUi.id` / `PostUi.cid`, which both note that wrapping to lexicon-typed values happens at the XRPC boundary, never on the wire of the UI / nav layers). No DI, no Compose, no atproto-kotlin types. `:feature:moderation:impl` owns everything else and depends on `:feature:moderation:api`.

**Why this over alternatives:**

- *Single `:feature:moderation` module with both NavKey and impl* — would force every caller (`:feature:feed:impl` and `:feature:profile:impl`) to depend on the full `:impl` dependency tree (atproto-kotlin runtime, Coil, etc.) just to construct a NavKey. The `:api`-only split is the established convention for exactly this reason — see `:feature:postdetail:api` and `:feature:profile:api`.
- *Fold into `:feature:profile:impl`* — wrong-module fit. The PostCard overflow is feed-side; routing it through `:feature:profile:impl` creates a feed-→-profile dependency that doesn't model how the report dialog is actually used.
- *Fold the NavKey into `:core:common:navigation`* — that module is for cross-cutting nav primitives (`Navigator`, `MainShellNavState`, qualifier annotations), not feature-specific NavKey types.

The two-module split is the cheapest variant that satisfies the existing conventions and leaves a clean seam for future moderation children (Block + Mute add their own NavKeys to `:feature:moderation:api` and their own dialogs/VMs to `:feature:moderation:impl`).

### Decision 2: Reason tokens as `String` constants in a `ReportReasons` object — not Kotlin enums, not lexicon-driven runtime resolution

**Choice:** `ReportReasons` exposes each granular `tools.ozone.report.defs` token (and each legacy `com.atproto.moderation.defs` fallback) as a `const val String`. Example: `const val REASON_SEXUAL_ABUSE = "tools.ozone.report.defs#reasonSexualAbuse"`. An `OTHER_REPORT_REASONS: Set<String>` collects the `*_OTHER` tokens. The UI's `ReportCategory` sealed sum groups them into the 9 cards but stores the raw token string; submission passes the string straight to `CreateReportRequest.reasonType`.

**Why this over alternatives:**

- *Kotlin enum (`enum class ReportReason { ... }`)* — locks the wire vocabulary to whatever is checked in. Future Ozone additions require a Kotlin-source release before they can be reported. Strings let an unknown reason round-trip through state if it ever appears (defensive). Also: enum entries can't represent the legacy fallback values cleanly without weird naming.
- *Generate the list at runtime from the lexicon* — over-engineered. Lexicon updates are infrequent; the one-file-PR review burden for adding a new token is zero. Runtime generation drags in a JSON-schema parser and a startup cost for no payoff.
- *Generate the list from atproto-kotlin's generated types* — atproto-kotlin doesn't generate the report-reason vocabulary as a single iterable type today. We'd be reinventing what we're trying to avoid.

The string-constants-with-explicit-OTHER-set shape is exactly what social-app uses in its `const.ts` (`OTHER_REPORT_REASONS` is the same name). Source compatibility with the reference client at the variable-name level is a small but real maintenance win.

### Decision 3: Dialog state — flat fields for independent toggles + a sealed `ReportDialogStep` for mutually-exclusive steps

**Choice:**

```kotlin
data class ReportDialogState(
    val subject: ReportSubject,
    val subjectPreview: SubjectPreview?,        // null = subject is being resolved (handle → display name)
    val step: ReportDialogStep,
    val selectedCategory: ReportCategory? = null,
    val selectedReason: String? = null,
    val details: String = "",
    val detailsRequired: Boolean = false,        // derived: selectedReason in OTHER_REPORT_REASONS
    val submission: SubmissionStatus = SubmissionStatus.Idle,
    val errorBanner: String? = null,
) : UiState

sealed interface ReportDialogStep : UiState {
    data object Subject : ReportDialogStep
    data object Category : ReportDialogStep
    data object SubReason : ReportDialogStep
    data object Details : ReportDialogStep
}

sealed interface SubmissionStatus {
    data object Idle : SubmissionStatus
    data object Submitting : SubmissionStatus
    data class Success(val sentAt: Instant) : SubmissionStatus
    data class Failed(val message: String) : SubmissionStatus
}
```

This follows the CLAUDE.md MVI guidance: the four-step lifecycle is mutually exclusive (the user is in exactly one step at a time), so `step` is a sealed sum; `submission` is also mutually exclusive (`Submitting` and `Failed` cannot coexist). `selectedCategory` / `selectedReason` / `details` are independent flat fields — the user can have a category but no reason, or a reason but empty details, and the reducer must keep them coherent through the step transitions.

`detailsRequired` is derived but persisted on state to keep the UI gating cheap (the Submit CTA reads one boolean field rather than running a `Set.contains` lookup on every recomposition).

**Why this over alternatives:**

- *Each step as a separate state class* (e.g. `SubjectStepState`, `CategoryStepState`, …) — would require a `sealed interface ReportDialogState` with per-step variants, breaking the "single per-screen state class with flat fields" rule and forcing the host Composable to write a `when` against the state shape itself. Rejected for symmetry with feed / profile.
- *Track everything in one `step: Int` field* — invalid by construction (the type system can't catch a wrong index).
- *Inline the `submission` status into `step` (`Submitting` becomes a 5th step)* — confuses "where in the form is the user" with "is the network call in flight". Two orthogonal axes; two state fields.

### Decision 4: Dialog presented as a navigated `ModalBottomSheet` sub-route, not an inline `ModalBottomSheet` invoked from the host screen

**Choice:** `:feature:moderation:impl` registers a `@Provides @IntoSet @MainShell EntryProviderInstaller` for the `Report` NavKey. The entry provider's Composable wraps the dialog in `androidx.compose.material3.ModalBottomSheet { … }` and returns. The sheet's `onDismissRequest` calls `LocalMainShellNavState.current.removeLast()` (or whatever the established back-pop API is on `MainShellNavState`). Back-button behavior collapses through the steps (Details → SubReason → Category → Subject → dismiss).

**Why this over alternatives:**

- *Inline `ModalBottomSheet` invoked from the host (Feed or Profile) screen* — duplicates the bottom-sheet scaffolding twice and doesn't benefit from `ListDetailSceneStrategy` on Medium+ widths. On Medium, an inline modal covers both panes; a sub-route lands cleanly in the detail pane. Future "Block confirmation" and "Mute confirmation" dialogs will want the same adaptive behavior; establishing the pattern here saves rework.
- *Full-screen `Composable` (no `ModalBottomSheet`)* — wrong visual treatment; bsky.app uses a bottom sheet. Bottom sheets convey "transient form, dismiss to return" better than a full-screen route for this kind of write.
- *Dialog rendered by the host but the host has no view-model awareness of the report state* — possible, but requires the host to thread the report VM through the navigation entry. Wraps too much knowledge into the host. The sub-route owns its VM via `hiltViewModel()` and the host stays oblivious.

### Decision 5: Success snackbar renders INSIDE the dialog before auto-dismiss, not on the host screen after dismiss

**Choice:** When `createReport` succeeds, the dialog transitions to `SubmissionStatus.Success`, swaps the form for a success card ("Report submitted. Thanks — Bluesky moderation will review it."), and auto-dismisses after 2.5 s (or on user tap-anywhere). The host screen never sees a snackbar for the success. Failure renders an inline error banner above the Submit CTA so the user can retry without losing their selection.

**Why this over alternatives:**

- *Pop the dialog on success, then show a snackbar on the host screen* — requires a one-shot result-passing channel from the popped sub-route back to the host, which `MainShellNavState` doesn't have today. Building one for a snackbar is over-engineering. The dialog already owns the user's attention; the success is best surfaced where they are.
- *Pop immediately on success, no acknowledgement* — silent success is anxiogenic for a moderation action. Users want explicit confirmation.
- *Snackbar inside the dialog before pop* — Snackbars compose awkwardly inside a `ModalBottomSheet`'s scrim. A success card replacing the form is cleaner.

### Decision 6: Repository accepts the raw `String` reason token, NOT a `ReportCategory` or `ReportReason` enum

**Choice:** `ModerationRepository.reportPost(uri, cid, reasonToken: String, details: String?)` and `reportAccount(did, reasonToken, details)`. The string is whatever `ReportReasons` constant the VM selected. The repository validates the string is non-blank but does NOT validate that it appears in any known set — the only authority on "valid reason" is the server.

**Why this over alternatives:**

- *Repository takes a `ReportReason` enum* — requires the enum (Decision 2 rejected this) and forces every reason-vocabulary change through a generated-types regeneration step.
- *Repository takes a sealed `ReportRequest` union (`ReportPost(uri, cid, reason)` | `ReportAccount(did, reason)`)* — equivalent expressiveness; two separate methods are clearer at the call site and easier to test independently. The current signature mirrors the bd issue's specification.

The repository's only behavior beyond the SDK call is graceme-truncating `details` to 2000 (the lexicon max). The UI's 300-graceme cap is a UI concern; the repository's 2000-graceme cap is the server's contract.

### Decision 7: `modTool.name = "nubecita/android"`, `modTool.meta = null` — versionName is NOT included in V1

**Choice:** The repository constructs `CreateReportModTool(name = "nubecita/android", meta = AtField.Absent())` on every submission. The app's `BuildConfig.VERSION_NAME` is intentionally NOT included.

**Why this over alternatives:**

- *Include the version name in `name` (e.g., `"nubecita/android@1.97.0"`)* — adds a build-time coupling to `BuildConfig` from `:feature:moderation:impl` for marginal observability value. If Bluesky's moderation team wants per-version filtering, they can do that on `name` prefix matching or we can revisit when there's a real ask.
- *Stuff the version into `meta`* — same concern; `meta` is a free-form `JsonObject` that's a magnet for accidental PII. Keep it empty until there's a deliberate signal to send.

### Decision 8: Back-button collapses through steps; only Subject → cancel actually dismisses the dialog

**Choice:** A `handleBack()` method on the VM transitions: `Details → SubReason → Category → Subject`. Back from `Subject` returns `false` to the Compose `BackHandler`, allowing the dialog's `onDismissRequest` to fire and pop the sub-route.

**Why this over alternatives:**

- *Back from any step dismisses the dialog immediately* — frustrating UX; users mid-flow accidentally lose their selection. The form is short enough that step-back is cheap to implement and matches bsky.app behavior.
- *Back behavior is implicit via the `ModalBottomSheet`'s built-in handling* — the built-in only handles dismiss, not multi-step undo. We have to wire `BackHandler` ourselves anyway.

### Decision 9: Validation lives on the VM; the Submit CTA is enabled iff the VM says it is

**Choice:** The VM exposes a derived `canSubmit: Boolean` field on state (computed in the reducer, not the UI). The rule:

- `selectedReason != null` (a granular reason has been chosen)
- AND `!detailsRequired || details.graphemeLength in 1..300` (when `_OTHER`, details are required and within 300 graphemes)

The Submit CTA reads `state.canSubmit` and disables itself otherwise. The 300-grapheme cap also gates `state.details` mutations — a longer paste is truncated before reaching state.

**Why this over alternatives:**

- *Compute `canSubmit` in the Composable* — duplicates logic across screens (preview fixtures, snapshot tests, real screen all have to recompute). VM-side keeps the truth in one place.
- *Validate on submit-click rather than gating the CTA* — confuses users; bsky.app gates the CTA and so should we.

The grapheme cap uses Compose's `androidx.compose.ui.text.intl.GraphemeIterator`-equivalent (or `BreakIterator`) — the same primitive `:feature:composer:impl` uses for its character counter. Single source of truth for grapheme counting across the app.

### Decision 10: `:designsystem` gains no new public composables for this change — all dialog primitives are M3 directly

**Choice:** The dialog content is built directly on `androidx.compose.material3.ModalBottomSheet`, `androidx.compose.material3.OutlinedTextField`, `androidx.compose.material3.Card`, `androidx.compose.material3.Text`, and the existing `NubecitaIcon` from `:designsystem`. No new `:designsystem` extraction — every primitive is single-consumer (this dialog), and the YAGNI rule from CLAUDE.md (`three similar lines is better than a premature abstraction`) applies.

**Why this over alternatives:**

- *Extract a `:designsystem/ReportCategoryCard` composable up-front* — premature. Block + Mute will both need confirmation dialogs but those are simpler shapes (single-button confirm sheets), not category pickers. Wait for a second category-picker consumer before extracting.
- *Extract a generic `:designsystem/BottomSheetWizard<Step>`* — over-abstracted. Future wizards will likely have different step counts and validation shapes; the abstraction would obstruct rather than help.

If `oftc.4` (Block) or `oftc.5` (Mute) end up reusing significant chunks of this dialog's structure (unlikely — those are single-tap confirmations), extraction can happen at that point.

## Risks / Trade-offs

- **`createReport` server-side failures are opaque** — the SDK throws a Ktor exception whose `.message` may be a transport error, a 401, a 400 with no actionable text, or a 500. → **Mitigation:** the repository wraps the call in `runCatching` and the VM maps any failure to a generic snackbar `"Couldn't submit report. Please try again."` while preserving the underlying exception for Timber logging. Defer per-status-code error UX until telemetry shows specific failure modes are common.

- **Grapheme counting in Kotlin is locale-sensitive** — the same string can have different grapheme counts under different `Locale` settings if extended-grapheme-cluster heuristics diverge. → **Mitigation:** use the same `BreakIterator`-based helper `:feature:composer:impl` already ships (single source of truth across the app). If composer's counter is wrong in a corner case, fix it once for both surfaces.

- **The `ReportReasons` constants drift from the lexicon over time** — Bluesky may add new `*_OTHER` tokens we don't know about, leaving users unable to pick them. → **Mitigation:** the bd issue explicitly notes that lexicon updates are one-file PRs to `ReportReasons` + the corresponding `ReportCategory` arm. The OpenSpec specs for `feature-moderation` capture that the vocabulary lives in `ReportReasons`; periodic audits against upstream `tools.ozone.report.defs` are part of the moderation epic's maintenance, not a build-time check.

- **The dialog as a sub-route changes the visual treatment vs. an inline modal** — under Medium+ widths the dialog lands in the detail pane, not centered over the entire viewport. → **Mitigation:** acceptable for V1 (`ListDetailSceneStrategy` is the established adaptive primitive); revisit if telemetry shows users routinely miss the dialog on Expanded widths. Screenshot tests at Medium width capture the placement so regressions are caught early.

- **The `ProfileEffect.ShowComingSoon(StubbedAction.Report)` path is the only branch we're tearing down — `Edit / Block / Mute` stay** — risk of inconsistent profile-overflow behavior (some rows route to dialogs, others to coming-soon snackbars). → **Mitigation:** a brief inconsistency window during the moderation epic (`oftc.4` Block lands next, then `oftc.5` Mute, then Edit either lands separately or stays stubbed). Each landing PR tears down its own row's stub. Documented in the proposal's "What Changes → Wiring entry points" section.

- **`StubbedAction.Report` removal is source-incompatible** — any test or call site that enumerates `StubbedAction.values()` and special-cases all four variants will break. → **Mitigation:** the `StubbedAction` enum is internal to `:feature:profile:impl`; grep shows no other module references it. The single Composable that maps `StubbedAction` → label string already has a `when` exhaustive over the four variants — removing `Report` from the enum drops the corresponding branch.

- **No back-channel from the dialog to the host means the host can't optimistically dim / hide the reported post** — V1 acceptable since report submission doesn't change feed visibility anyway (per the bd issue). → **Mitigation:** when Block / Mute land (which DO mutate feed visibility), they need a back-channel. Defer designing it until then; this change deliberately doesn't build it.

## Migration Plan

This change rolls out in 4 child PRs under `nubecita-oftc.3`. Each PR is one bd issue and one Conventional Commit; bullets within a PR can be split into multiple commits.

1. **PR 1 — `:feature:moderation:api` + `ReportReasons` + `ModerationRepository`** (foundational; no UI, no wiring). Lands the new `:feature:moderation:api` module with `Report` + `ReportSubject`; lands `:feature:moderation:impl` with `ReportReasons` constants, `ReportCategory` sealed sum, and `ModerationRepository` + `DefaultModerationRepository` calling `ModerationService.createReport`. Hilt module binds the repo. Unit-tested via fake `HttpClient`. The dialog screen does not yet exist; `:app` does not yet register the entry provider.

2. **PR 2 — Report dialog screen + VM** (uses the foundation). Lands `ReportDialogViewModel`, `ReportDialogScreen` (the stateful wrapper), `ReportDialogContent` (the stateless content used for previews / screenshot tests), all 9 category cards, all sub-reason rows, the details textarea, the submission flow with success / failure UI. Registers the `@MainShell` provider. Adds screenshot tests for each step + each category card in light + dark. No entry-point wiring yet — the dialog is reachable only via direct NavKey push from a debug fixture.

3. **PR 3 — Feed-side wiring** — `FeedViewModel` learns to translate `PostOverflowAction.ReportPost` into `FeedEffect.NavigateTo(Report(ReportSubject.Post(uri, cid)))`. The Feed screen's effect collector pushes the NavKey. Adds a feed VM unit test for the new event handler and an instrumentation test that PostCard-overflow-tap-Report actually lands on the dialog.

4. **PR 4 — Profile-side wiring + stub teardown** — Renames `ProfileEvent.StubActionTapped(Report)` to `ProfileEvent.OnReportAccountRequested` (or its closest fit) and removes the `Report` branch from `ProfileEffect.ShowComingSoon` and from the `StubbedAction` enum. Adds a corresponding `ProfileEffect.NavigateTo(Report(ReportSubject.Account(did)))` and the screen's effect collector handles it. Deletes the `profile_snackbar_report_coming_soon` string resource and any helper that referenced it. Adds profile VM tests for the new pathway.

**Rollback strategy.** PR 1 has no behavioral surface — reverting is a no-op for users. PR 2 introduces the dialog but it's unreachable without PRs 3/4. Reverting PR 3 leaves the PostCard overflow with no handler (same state as today after `oftc.2`). Reverting PR 4 restores the snackbar stub for Report — string resource needs to come back too. Each PR is independently revertable except that PR 4 should not land before PR 2 (the dialog must exist before profile routes to it).

**Sequencing constraint.** PR 1 → (PR 2 in parallel with experiment / fixture work) → PR 3 + PR 4 (parallel). If schedule pressure demands, PRs 3 and 4 can land together as a single PR — at that point the dialog is wired from both surfaces in lockstep.

## Open Questions

- **Should the Report dialog confirm before submitting, or submit on the first Submit tap?** bsky.app submits immediately. We follow suit unless there's a UX argument otherwise. Defer to PR 2 implementation review.
- **What does the success card show — just text, or text + the category they reported under?** Probably text only ("Report submitted") for V1 to keep the success card terse; bsky.app does the same. Defer to PR 2.
- **Where does the dialog live when the host is the Profile screen (which is itself rendered in either pane)?** Today the inner `NavDisplay`'s `ListDetailSceneStrategy` resolves this based on whether the navigation entry is marked `listPane{}` or `detailPane{}` metadata. The Report sub-route should NOT carry list/detail metadata — it's a transient overlay-style sub-route, not a list-detail entry. Verify the strategy treats unmarked entries as "fits wherever" during PR 2. Flag if it forces the dialog into the wrong pane.
- **Does the success-card auto-dismiss interfere with TalkBack / screen readers?** A 2.5s auto-dismiss is too fast for screen-reader announce-then-read flows. → Likely fix: extend to 5s when `AccessibilityManager.isEnabled` or honor a TalkBack-specific timeout. Defer to PR 2 a11y pass.
- **Does the modTool `name` value need to be coordinated with Bluesky's moderation team in advance?** Probably not — modTool is informational, not authenticated. But if there's a registry of expected modTool names, we should adopt the convention. Flag for the maintainer to surface during PR 1 review.
