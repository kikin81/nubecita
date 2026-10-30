## 1. W1 Wear foundation (epic nubecita-sn52)

- [ ] 1.1 Add `:core:wear-contract` with snapshot/action DTOs, `/v1/` paths, `schemaVersion`, and capability names (nubecita-sn52.1). Tests: JVM serialization round-trip and schema-version guard tests.
- [ ] 1.2 Add the `nubecita.android.wear.application` convention plugin and the `:wear:app` shell:
  - manifest: non-standalone, watch feature, unique `taskAffinity`, `targetSdk` ≥ 34;
  - splash screen, black theme, `AppScaffold`, and Nav3 with the swipe-dismiss scene strategy;
  - `wear.xml` capability.

  (nubecita-sn52.2) Tests: Wear screenshot tests for the empty shell under devices × font scales.
- [ ] 1.3 Publish `/v1/session` from the phone and wipe the watch's data on sign-out. Add the no-phone (install prompt), iPhone and signed-out states, and the `/v1/` listener filters (nubecita-sn52.3). Tests: unit tests for the session reducer and wipe; screenshot tests for each state.
- [ ] 1.4 Release pipeline (nubecita-sn52.4):
  - an independent Wear version-code scheme: `1_000_000_000 + the phone code`, computed in the convention plugin (1.351.1 → 1001351001);
  - release signing with the phone's upload key;
  - an `internal_wear` fastlane lane plus a `release.yaml` step that upload the Wear bundle to `wear:internal` after the phone upload;
  - the lane first verifies the track exists. Track ids were read via the Play API on 2026-10-25: `wear:internal`, `wear:beta`, `wear:production`; no Wear closed track exists.

  Closed testing and the `promote.yaml` Wear target move to 2.6, since both only matter for the Wear review opt-in at launch. Tests: the merged release manifest's versionCode; actionlint; Fastfile syntax.
- [ ] 1.5 Wear Play Store screenshots from a bench flavor (nubecita-sn52.5): bench fakes, a fixed clock, and the `wear_screenshots` lane writing raw 1:1 PNGs to `wearScreenshots/`. Tests: a device capture; lint of both flavors.

## 2. W2 Feed browsing (epic nubecita-jh9c)

- [ ] 2.1 Add the phone Settings → Watch page and a 3-feed picker in `:core:preferences`, with strings in en, es-419 and pt-BR (nubecita-jh9c.1). Tests: ViewModel unit tests for the max-3 rule and the default; screenshot tests for the page.
- [ ] 2.2 Refresh the selected feeds from the widget refresh, gated on the capability. Publish non-urgent feed snapshots with `Asset` thumbnails (nubecita-jh9c.2). Tests: `WidgetRefreshRunner` unit tests for the paired / not-paired cases; snapshot mapper tests.
- [ ] 2.3 Add the `:wear:data` Room DB and listener ingest, with an atomic per-feed replace (nubecita-jh9c.3). Tests: Room DAO tests and ingest tests.
- [ ] 2.4 Add the watch feed list, feed switcher and post screen, with rotary snap and the quality floors (nubecita-jh9c.4). Tests: ViewModel unit tests; screenshot tests for the top, middle and bottom anchors × font scales.
- [ ] 2.5 Pull-on-open: send a `/v1/sync/request` message; the phone republishes urgently and calls `refreshNow()` with `KEEP` (nubecita-jh9c.5). Tests: phone handler unit test for urgency and the single enqueue.
- [ ] 2.6 Production launch: feed screenshots via `wear_screenshots`, the localized listing, a Wear closed-testing track plus a closed release (required before the Wear OS opt-in), a `promote.yaml` Wear target, the opt-in and review, and a promote to production (nubecita-jh9c.6). Tests: verify the screenshot staging in fastlane.

## 3. W3 Likes, reposts and bookmarks (epic nubecita-hohv)

- [ ] 3.1 Action and result `DataItem` contract (urgent, `createdAt`, `accountDid`); the phone action listener with the `WearActionHandler` multibinding, which drops actions for other accounts (nubecita-hohv.1). Tests: dispatcher unit tests (account mismatch, result written, action deleted).
- [ ] 3.2 Like, unlike, repost, unrepost, bookmark and unbookmark handlers on the phone; the snapshot carries the like and repost URIs and the bookmark state (nubecita-hohv.2). Tests: handler unit tests with mocked repositories.
- [ ] 3.3 Watch `pending_action` overlay and the like / repost / bookmark icon-button row on the post screen, with a confirmation before a repost and a silent revert on failure; the row is hidden until the paired phone supports actions (nubecita-hohv.3). Tests: overlay merge unit tests; screenshot tests for the control states and the repost confirmation.
- [ ] 3.4 "Open on phone" edge button on the post screen: opens the post's `bsky.app` link on the phone through `RemoteActivityHelper`, with the Wear open-on-phone confirmation (nubecita-hohv.4). Tests: unit test for the post URL; screenshot test for the button.

## 4. W4 Messages and quick replies (epic nubecita-by5w)

- [ ] 4.1 DM snapshot from `DmPollRunner`: fetch only changed threads, seed once, keep a phone-side Room cache, and publish non-urgently, with the existing gates inherited (nubecita-by5w.1). Tests: `DmPollRunner` unit tests for quiet, changed, not-paired and message-checking-off.
- [ ] 4.2 Watch conversation and message tables, ingest, the conversation list and the thread screens (nubecita-by5w.2). Tests: DAO and ingest tests; screenshot tests.
- [ ] 4.3 Quick replies and reactions: the chats `WearActionHandler`, a 1-hour expiry, and the "Sending…" and retry UI (nubecita-by5w.3). Tests: expiry and handler unit tests; screenshot tests for sending and failed.

## 5. W5 Unread-DMs complication (epic nubecita-9o3a)

- [ ] 5.1 Push-only `SuspendingComplicationDataSourceService` with preview data, the bind permission, a tap that opens messages, and an update requested on DM ingest; mention it in the listing (nubecita-9o3a.1). Tests: unit tests for the complication data mapping.

## 6. W6 Voice and text (epic nubecita-rxsh)

- [ ] 6.1 Amend the design doc and this change for voice and text replies and watch posts (nubecita-rxsh.1).
- [ ] 6.2 `create-post` in the contract and the `nubecita_phone_post_actions` capability (nubecita-rxsh.2). Tests: codec round-trip; an unknown action stays unsupported.
- [ ] 6.3 Phone `WearActionHandler` for `create-post` through `PostingRepository`, with the 300-grapheme re-check and a 1-hour expiry (nubecita-rxsh.3). Tests: handler unit tests for the limit, the expiry and failure.
- [ ] 6.4 Watch text-input flow: `RemoteInput`, then a review screen with a grapheme counter (nubecita-rxsh.4). Tests: unit tests for the limit; screenshot tests across font scales.
- [ ] 6.5 Voice and typed DM replies from the thread, over the existing `dm-reply` path (nubecita-rxsh.5). Tests: ViewModel unit test; screenshot test for the reply entry.
- [ ] 6.6 Compose and send a short post from the feed, with a `watch_pending_post` table and "Posting…" / "Not posted — tap to retry" states (nubecita-rxsh.6). Tests: DAO, repository and ingest tests; screenshot tests for each state.
