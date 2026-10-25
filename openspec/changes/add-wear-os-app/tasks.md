## 1. W1 Wear foundation (epic nubecita-sn52)

- [ ] 1.1 Add `:core:wear-contract` with snapshot/action DTOs, `/v1/` paths, `schemaVersion`, and capability names (nubecita-sn52.1). Tests: JVM serialization round-trip and schema-version guard tests.
- [ ] 1.2 Add the `nubecita.android.wear.application` convention plugin and the `:wear:app` shell:
  - manifest: non-standalone, watch feature, unique `taskAffinity`, `targetSdk` ≥ 34;
  - splash screen, black theme, `AppScaffold`, and Nav3 with the swipe-dismiss scene strategy;
  - `wear.xml` capability.

  (nubecita-sn52.2) Tests: Wear screenshot tests for the empty shell under devices × font scales.
- [ ] 1.3 Publish `/v1/session` from the phone and wipe the watch's data on sign-out. Add the no-phone (install prompt), iPhone and signed-out states, and the `/v1/` listener filters (nubecita-sn52.3). Tests: unit tests for the session reducer and wipe; screenshot tests for each state.
- [ ] 1.4 Release pipeline:
  - an independent Wear version-code scheme;
  - the Wear track names, verified first by a read-only `workflow_dispatch` CI job using the existing workload-identity Play identity (`google_play_track_version_codes`; no JSON key, no local credentials), run after the Wear form factor is enabled;
  - the fastlane and `release.yaml` Wear bundle upload;
  - a closed-testing release;
  - a `promote.yaml` Wear target, kept out of production.

  (nubecita-sn52.4) Tests: unit test of the version-code function; a dry-run of the lane.

## 2. W2 Feed browsing (epic nubecita-jh9c)

- [ ] 2.1 Add the phone Settings → Watch page and a 3-feed picker in `:core:preferences`, with strings in en, es-419 and pt-BR (nubecita-jh9c.1). Tests: ViewModel unit tests for the max-3 rule and the default; screenshot tests for the page.
- [ ] 2.2 Refresh the selected feeds from the widget refresh, gated on the capability. Publish non-urgent feed snapshots with `Asset` thumbnails (nubecita-jh9c.2). Tests: `WidgetRefreshRunner` unit tests for the paired / not-paired cases; snapshot mapper tests.
- [ ] 2.3 Add the `:wear:data` Room DB and listener ingest, with an atomic per-feed replace (nubecita-jh9c.3). Tests: Room DAO tests and ingest tests.
- [ ] 2.4 Add the watch feed list, feed switcher and post screen, with rotary snap and the quality floors (nubecita-jh9c.4). Tests: ViewModel unit tests; screenshot tests for the top, middle and bottom anchors × font scales.
- [ ] 2.5 Pull-on-open: send a `/v1/sync/request` message; the phone republishes urgently and calls `refreshNow()` with `KEEP` (nubecita-jh9c.5). Tests: phone handler unit test for urgency and the single enqueue.
- [ ] 2.6 Production launch: raw 1:1 Wear screenshots, the localized listing, the Wear OS opt-in and review, and a promote to production (nubecita-jh9c.6). Tests: verify the screenshot staging in fastlane.

## 3. W3 Likes and bookmarks (epic nubecita-hohv)

- [ ] 3.1 Action and result `DataItem` contract (urgent, `createdAt`, `accountDid`); the phone action listener with the `WearActionHandler` multibinding, which drops actions for other accounts (nubecita-hohv.1). Tests: dispatcher unit tests (account mismatch, result written, action deleted).
- [ ] 3.2 Like, unlike, bookmark and unbookmark handlers on the phone; the snapshot carries the like URI and bookmark state (nubecita-hohv.2). Tests: handler unit tests with mocked repositories.
- [ ] 3.3 Watch `pending_action` overlay and the like/bookmark controls, with a silent revert on failure (nubecita-hohv.3). Tests: overlay merge unit tests; screenshot tests for the control states.

## 4. W4 Messages and quick replies (epic nubecita-by5w)

- [ ] 4.1 DM snapshot from `DmPollRunner`: fetch only changed threads, seed once, keep a phone-side Room cache, and publish non-urgently, with the existing gates inherited (nubecita-by5w.1). Tests: `DmPollRunner` unit tests for quiet, changed, not-paired and message-checking-off.
- [ ] 4.2 Watch conversation and message tables, ingest, the conversation list and the thread screens (nubecita-by5w.2). Tests: DAO and ingest tests; screenshot tests.
- [ ] 4.3 Quick replies and reactions: the chats `WearActionHandler`, a 1-hour expiry, and the "Sending…" and retry UI (nubecita-by5w.3). Tests: expiry and handler unit tests; screenshot tests for sending and failed.

## 5. W5 Unread-DMs complication (epic nubecita-9o3a)

- [ ] 5.1 Push-only `SuspendingComplicationDataSourceService` with preview data, the bind permission, a tap that opens messages, and an update requested on DM ingest; mention it in the listing (nubecita-9o3a.1). Tests: unit tests for the complication data mapping.
