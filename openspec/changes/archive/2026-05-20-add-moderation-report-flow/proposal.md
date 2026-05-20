## Why

Google Play's UGC policy requires a working "Report content" affordance for any feed of user-generated content. Without it, the Closed Alpha track (and every track above Internal) instantly rejects the AAB. This is the gating piece of the `nubecita-oftc` Play Store moderation epic.

Two earlier children in the epic already shipped the prerequisites: `nubecita-oftc.1` surfaced `viewer.muted` / `viewer.blocking` / `viewer.blockedBy` on `PostUi` and `ProfileHeaderUi`, and `nubecita-oftc.2` added the PostCard overflow menu scaffolding with a `PostOverflowAction.ReportPost` item whose tap is currently unwired on the feed side. The ProfileHero already has a `ProfileEvent.StubActionTapped(StubbedAction.Report)` path that surfaces a `profile_snackbar_report_coming_soon` snackbar. This change is what makes both entry points actually do something — open a full report dialog, collect a reason, and submit `com.atproto.moderation.createReport`.

The Bluesky moderation lexicon ships a granular reason hierarchy under `tools.ozone.report.defs` (9 categories, dozens of sub-reasons) plus the legacy 7-value flat list under `com.atproto.moderation.defs` as a wire fallback. The official `bsky.app` client uses the granular flow with a multi-step picker. We follow suit — flat lists are a regression from current Bluesky UX and a foreseeable inflexibility once Ozone evolves further.

## What Changes

### New modules

- **NEW** `:feature:moderation:api` — Navigation 3 module owning a single `Report : NavKey` parameterized by a `ReportSubject` sealed sum (`Post(uri: String, cid: String)` or `Account(did: String)`). Plain `String` fields per the codebase's nav-layer convention (`PostDetailRoute.postUri`, `PostUi.id` / `PostUi.cid`); SDK value-class wrapping happens at the XRPC boundary inside `:impl`. No DI, no Compose. Mirrors `:feature:postdetail:api` and `:feature:profile:api`.
- **NEW** `:feature:moderation:impl` — applies `nubecita.android.feature` (the convention plugin from `build-logic/`). Owns the report dialog screen, `ReportDialogViewModel`, `ModerationRepository` + impl, `ReportReasons` token constants, Hilt modules.

### Reason hierarchy as constants

- **NEW** `ReportReasons` object in `:feature:moderation:impl` holds the granular `tools.ozone.report.defs` token strings (`REASONSEXUALABUSE`, `REASONVIOLENCEGRAPHIC`, `REASONCHILDSAFETYCSAM`, etc.) and the legacy `com.atproto.moderation.defs` fallbacks (`reasonSpam`, `reasonOther`) as `String` constants. Reason tokens are NOT Kotlin enums — keeping them as strings makes a future lexicon-update PR a one-file diff and keeps unknown reasons round-trippable.
- **NEW** `OTHER_REPORT_REASONS: Set<String>` enumerates the `*_OTHER` tokens (`REASONSEXUALOTHER`, `REASONVIOLENCEOTHER`, `REASONHARASSMENTOTHER`, `REASONCHILDSAFETYOTHER`, `REASONMISLEADINGOTHER`, `REASONRULEOTHER`, `REASONSELFHARMOTHER`, `reasonOther`). Tokens in this set force-require the optional details textarea.
- **NEW** A `ReportCategory` sealed sum models the 9 top-level cards (Spam, Sexual content, Violence, Child safety, Harassment, Misleading, Rule violation, Self-harm, Other) along with their child reasons. This is local to the UI / VM layer — the repository accepts a `ReportReasons` token string, not a `ReportCategory`.

### Dialog flow

- **NEW** `ReportDialogScreen` — a Material 3 `ModalBottomSheet` driving a 3-or-4-step state machine:
  1. **Subject confirmation** — header card identifying the subject (post snippet + author for `Post`, profile handle + display name for `Account`) with a Cancel `×`.
  2. **Category card pick** — 9 category cards with `NubecitaIcon` + label; tap → step 3 with that category selected.
  3. **Sub-reason pick** — list of granular reasons within the chosen category; tap a non-`_OTHER` → optional step 4 or direct submit; tap a `_OTHER` → step 4 required.
  4. **Optional / required details + submit** — 300-grapheme textarea with a live counter, plus a primary "Submit report" CTA. Validation: required when the selected reason is in `OTHER_REPORT_REASONS`, otherwise optional.
- Submit calls `ModerationService.createReport` with the correct subject union variant (`RepoRef(did)` for account; `StrongRef(uri, cid)` for post) and `modTool = ModTool(name = "nubecita/android", meta = null)` per the bd issue's lexicon fingerprint.
- Success → the dialog transitions to `SubmissionStatus.Success` and swaps its form content for an in-dialog success card (`"Report submitted. Thanks — Bluesky moderation will review."`), then auto-dismisses after ~2.5 s via `ReportDialogEffect.RequestDismiss` (the screen pops the `Report` NavKey off `LocalMainShellNavState`). The host Feed / Profile screen does NOT receive a separate snackbar — the success acknowledgement lives entirely inside the dialog. No optimistic state change on the feed (reports don't change feed visibility). See design Decision 5 for the rationale.
- Failure → `ReportDialogEffect.SubmissionFailed(message)` → dialog stays open on step 4 so the user can retry without losing the selected reason + details.

### Repository

- **NEW** `ModerationRepository` interface in `:feature:moderation:impl` with `suspend fun reportPost(uri: String, cid: String, reasonToken: String, details: String?): Result<Unit>` and `suspend fun reportAccount(did: String, reasonToken: String, details: String?): Result<Unit>`. Plain `String`s — the implementation wraps to lexicon-typed values at the XRPC boundary. Both internally truncate `details` to 2000 graphemes (lexicon max); the UI's 300-grapheme cap stays at the UI layer for parity with bsky.app.
- **NEW** `DefaultModerationRepository` calls `ModerationService(client).createReport(...)` and translates the `KtorResult` shape into `Result<Unit>`. The repository is the only call site that mentions `ModTool` — call sites never construct it.

### Wiring entry points

- **Feed side** — `FeedViewModel` gains a `FeedEvent.OnPostOverflow(post: PostUi, action: PostOverflowAction)` event (or a more specific `OnReportPostRequested(post)` event if the existing OnPostOverflow plumbing from `oftc.2` already discriminates). The handler emits a new `FeedEffect.NavigateTo(Report(ReportSubject.Post(uri = post.uri, cid = post.cid)))`. The screen's effect collector pushes the NavKey onto `LocalMainShellNavState.current`.
- **Profile side** — `ProfileContract.StubbedAction.Report` is removed; a new `ProfileEvent.OnReportAccountRequested` and `ProfileEffect.NavigateTo(Report(ReportSubject.Account(did = ...)))` replaces the `ShowComingSoon(Report)` pathway. The string resource `profile_snackbar_report_coming_soon` is deleted along with any plumbing that referenced it. `StubbedAction.Edit`, `Block`, and `Mute` remain stubbed for now — only the `Report` row migrates here.
- **Navigation shell** — `:feature:moderation:impl` registers a `@Provides @IntoSet @MainShell EntryProviderInstaller` for the `Report` NavKey. The dialog is a sub-route of `MainShell`'s inner `NavDisplay`, like `PostDetailRoute` and `Profile(handle = ...)`.

### Out of scope for this change

- Labeler picker (which moderation service to route the report to). V1 routes to Bluesky's default labeler only. A future change adds `app.bsky.labeler.getServices` resolution.
- Reporting embedded media (images / videos) as separate subjects from the parent post.
- Reporting chat messages — lexicon has no chat-message subject variant today.
- The "Report this account from a post" affordance (a separate menu item beyond "Report post" — bsky.app has both per post).
- The Block and Mute flows (`nubecita-oftc.4` / `oftc.5`) — separate changes that will delta the same `feature-moderation` capability later.

## Capabilities

### New Capabilities

- `feature-moderation`: The moderation surface — covers the Report dialog, `ReportDialogViewModel`, `ModerationRepository`, the `ReportReasons` token model, the `Report` NavKey contract, the createReport request shape, and the entry-point wiring rule that both PostCard overflow and ProfileHero overflow MUST route through `LocalMainShellNavState.current` to the dialog rather than rendering inline modals or coming-soon stubs. Future moderation children (`oftc.4` Block, `oftc.5` Mute) will delta this capability rather than introduce new ones.

### Modified Capabilities

- `app-navigation-shell`: Adds a requirement that `:feature:moderation:impl` provides a `@MainShell`-qualified `EntryProviderInstaller` for the `Report` `NavKey`. Parallels how `add-profile-feature` added the `Profile` and `Settings` providers — `:app`'s `MainShellPlaceholderModule` never held a placeholder for `Report` (it's a sub-route, not a top-level destination), so no placeholder removal is needed here.

## Impact

- **Affected modules**: NEW `:feature:moderation:api` + `:feature:moderation:impl`; `:feature:feed:impl` (FeedViewModel + FeedScreen effect-collector — wire the new overflow → nav effect); `:feature:profile:impl` (ProfileContract event/effect renaming, `profile_snackbar_report_coming_soon` deletion); `:app` (DI graph picks up the new module's providers automatically via the existing `EntryProviderInstaller` multibinding; no `MainShellPlaceholderModule` change).
- **Affected specs**: `feature-moderation` (new), `app-navigation-shell` (delta).
- **Dependencies**: no new external libraries. `ModerationService` + the createReport request types are already generated in `atproto-kotlin` v5.5.0 (current pinned version). String constants for the Ozone reason hierarchy come from this change directly — not the SDK.
- **Out of scope for this change**: see the "Out of scope for this change" list under What Changes above.
- **Backwards compatibility**: removing `profile_snackbar_report_coming_soon` and the `StubbedAction.Report` enum variant is a source-incompatible change to `:feature:profile:impl` symbols only — no public API exits this module. The `PostOverflowAction.ReportPost` enum variant in `:designsystem` is unchanged; only the feed-VM handler for it changes.
- **Behavior under feature flags / build variants**: none. No flag gates. The report flow is always-on once the change merges.

## Non-goals

- **Inline (in-feed) Report dialog without navigation.** The dialog is a sub-route pushed onto `LocalMainShellNavState`. Rationale: adaptive layouts (Medium / Expanded widths) expect sub-routes to potentially land in the detail pane via `ListDetailSceneStrategy`; building a feed-internal modal duplicates that scaffolding and inverts the established pattern. Defer to a future change if telemetry shows the navigation transition feels heavy.
- **Animated category icons in the picker.** M3 `NubecitaIcon` static glyphs only; expressive motion graphs on the cards are explicitly out of scope for the V1 Play Store gate.
- **Report history / "your past reports" surface.** No local persistence of submitted reports beyond the immediate "submitted" snackbar.
- **Server-side validation pre-flight.** The dialog does not call any pre-validation RPC before submit; the `createReport` call IS the validation. Failure surfaces the server's error text in the failure snackbar.
- **Optimistic mute on report.** bsky.app offers an opt-in "mute this account after reporting" affordance. Out of scope for V1 — surfaces under the Mute epic child (`oftc.5`) once muting itself is wired.
- **Custom shapes / shape morphing / hand-rolled UI primitives.** Same baseline prohibition as every prior change — M3 / Compose / `:designsystem` primitives only.
