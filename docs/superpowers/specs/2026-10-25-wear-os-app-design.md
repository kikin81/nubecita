# Nubecita for Wear OS — design

Date: 2026-10-25 · Status: draft for approval

## Intent

Put the parts of Nubecita that make sense on a wrist onto a Wear OS watch:
glance at the timeline, like or bookmark a post, read recent DMs and fire off
a quick reply — including when the phone is out of reach (a run, the gym).
The watch is a **companion**, not a second client: the phone stays the only
thing that talks to Bluesky.

## Goals

- A Wear OS app built only on the newest Compose APIs: Wear Compose
  Material 3 and Wear Navigation 3, **pre-release versions allowed**
  (currently `1.8.0-alpha02` for both `androidx.wear.compose:compose-material3`
  and `compose-navigation3`). No XML layouts, no View-based UI, no
  Horologist, no Material 2.5.
- Offline feed browsing from a snapshot the phone sends.
- Like / unlike and bookmark / unbookmark on the watch, working offline and
  delivered when the phone is next in reach.
- Read recent DMs, plus quick replies (canned phrases and emoji reactions)
  delivered the same way.
- An unread-DMs complication.
- Free for every signed-in user — no Pro gate.

## Non-goals

- The watch never signs in to Bluesky and never makes a network call to it
  (no standalone/LTE mode, no second OAuth session, no second token-refresh
  path).
- Video playback on the watch (a video shows its poster frame only).
- Free-text or voice replies, composing posts, reposts, quotes, follows.
- Tiles / Wear widgets for the feed (possible follow-up).
- Offline browsing in the **phone's** Feed screen. That is a real gap — the
  Room cache `:core:feed-cache` exists and the widgets use it, but the Feed
  screen still reads from the network — but it is a **separate feature** and
  does not block this work.

## Anti-goals (failure even if it "works")

- Any new background wakeup, wakelock, expedited work or foreground service
  on the phone just for the watch. Battery beats freshness.
- A new caller of the OAuth token refresh. The phone already has two
  background callers (widget refresh, DM poll); the watch must only ride on
  them.
- A watch UI that is a shrunken phone UI.

## Decided by the user

| Decision | Choice |
|---|---|
| Data source | Phone syncs to watch over the Wear Data Layer. Watch is offline-only. |
| Feeds | Following by default; a phone setting lets the user check **up to 3** feeds to sync. |
| Messages | Read recent convos **+ quick replies** (canned phrases / emoji reactions), sent via the phone. |
| Sync timing | Piggyback the existing 15-min widget refresh and DM poll, plus a pull when the watch app opens with the phone in reach. No new schedule. |
| Glanceable surfaces | Unread-DMs complication only. |
| Pricing | Free for everyone. |
| UI stack | Latest Wear Compose M3 + Navigation 3, betas/alphas allowed, no XML. |
| Delivery | Umbrella OpenSpec; each flow is its own bd epic and `gh stack`. |
| DM content | Only threads the DM poll's `getLog` shows as changed get their last ~20 messages fetched, and only while a watch with the app is paired. Quiet convos cost nothing. |
| Message checking off | Watch DMs freeze too. The watch never keeps the DM poll alive, so watch DMs stay at the last snapshot until the setting is back on. |
| Stale actions | Likes / bookmarks never expire. A quick reply or reaction older than **1 hour** is not sent; it shows "Not sent — tap to retry". Any action for an account that has since signed out is dropped. |
| Launch | The Wear app is not public until W2 (feed browsing). W1 ships to the internal track. Play only allows the Wear OS opt-in and review after a build has a **closed-testing** release, so W1 or early W2 also puts a Wear build on closed testing. Production follows with W2. From then on every release builds and uploads both bundles. |

## What exists today (relevant)

- `:core:feed-cache` — Room-backed `FeedRepository` + `FeedRemoteMediator`,
  partitioned by `(account_did, feed_type, feed_uri)`, storing the full wire
  `postView` JSON blob. Supports FOLLOWING / DISCOVER / CUSTOM / LIST.
- `:core:widget-sync` — `WidgetRefreshScheduler` enqueues a 15-min periodic
  `WidgetRefreshWorker` **whenever the user is signed in** (cancelled on
  sign-out, never in the bench flavor). `WidgetRefreshRunner` refreshes the
  widgets' feeds (Following, Discover) into `:core:feed-cache` and prefetches
  thumbnails (`WidgetImagePrefetcher`).
- `:feature:chats:impl` — `DmPollWorker`, 15-min periodic, plus
  `ChatRepository` for convos/messages/send/reactions.
- `LikeRepostRepository.like(StrongRef) / unlike(likeUri)`,
  `BookmarkRepository.bookmark(StrongRef)` / remove.
- `RefreshCaller` telemetry tags (`Widget`, `DmPoll`) — the watch sync must
  not add a new value.

## How it works

### Shape

```
 phone (net.kikin.nubecita)                         watch (net.kikin.nubecita, Wear APK)
 ┌───────────────────────────────┐   Data Layer    ┌──────────────────────────────┐
 │ WidgetRefreshWorker ──► feed-cache ──┐           │ WearSyncListenerService      │
 │ DmPollWorker ──► ChatRepository ─────┼─► WearSnapshotPublisher ──DataItems──► │  └► watch Room DB         │
 │                                      │           │      ▲                       │
 │ WearActionListenerService ◄──────────┼── action DataItems ── pending_action    │
 │   └► LikeRepostRepository / Bookmark / ChatRepository                          │
 │ WearSyncRequestHandler ◄── MessageClient "/sync/request" (watch app opened)    │
 └───────────────────────────────┘                 └──────────────────────────────┘
```

Both APKs share the applicationId `net.kikin.nubecita` and signing key — the
Data Layer only pairs apps with the same package and signature. The Wear APK
ships through the same Play listing on the Wear form-factor track.

### Modules (proposed)

- `:wear:app` — the Wear application module (`nubecita.android.wear.application`
  convention plugin, new). Hosts the Activity, `AppScaffold`, Wear `NavDisplay`
  with `rememberSwipeDismissableSceneStrategy()`, Hilt graph, theme.
- `:wear:data` — watch-side Room database + DAOs + Data Layer listener.
- `:wear:feature:*` — feed, post detail, messages (mirroring the phone's
  api/impl split only if it earns its keep; flat modules are fine at this size).
- `:core:wear-contract` — **shared** by phone and watch: kotlinx.serialization
  DTOs for snapshots and actions, Data Layer paths, schema version, capability
  name. No atproto runtime/OAuth deps (same rule as `:data:models`).
- `:core:wear-sync` — phone-side publisher, action listener, sync-request
  handler, feed-selection preference.

### Contract

- **Capabilities** (declared in each app's `res/values/wear.xml`):
  - `nubecita_wear`, declared by the watch app. The phone uses
    `CapabilityClient` to check whether any paired node has the app. That
    gates the phone's Watch settings page and whether publishing does
    anything.
  - `nubecita_phone`, declared by the phone app. The watch uses it to detect
    a paired phone that lacks Nubecita (see "Watch side → states").
- **Urgency**. A non-urgent `DataItem` can wait **up to 30 minutes** before it
  syncs (usually a few minutes), so:
  - Watch-written **actions** use `setUrgent()`. They are user-initiated, and
    a like or reply must not sit for half an hour.
  - The phone's **pull-on-open** republish uses `setUrgent()`, because the
    user is looking at the watch.
  - The **periodic** snapshot publishes from the 15-min workers stay
    non-urgent, so the system can batch them while the radio is already
    awake. This is the battery-friendly default.
- **Listener filters**. Both apps' `WearableListenerService`s declare
  `DATA_CHANGED` / `MESSAGE_RECEIVED` intent filters with
  `scheme="wear" host="*" pathPrefix="/v1/"`, never a catch-all. That keeps
  stray process wakeups to a minimum.
- **Snapshots** are `DataItem`s (durable, delivered on reconnect, last write
  wins):
  - `/v1/<accountDid>/feed/<feedKey>` — ordered posts for one synced feed.
  - `/v1/<accountDid>/dms` — recent convos with their last messages + unread
    count.
  - `/v1/session` — signed-in account DID / handle / avatar, or signed-out.
  - Thumbnails travel as `Asset`s referenced from the DataItem (DataItem
    payloads are capped at 100 KB; images must not be inlined).
  - Every payload carries `schemaVersion`. A watch that sees a newer major
    version shows "Update Nubecita on your watch" and keeps its last data.
- **Actions** are `DataItem`s the watch writes at
  `/v1/<accountDid>/action/<uuid>` (like, unlike, bookmark, unbookmark,
  dm-reply, dm-react). DataItems persist across disconnection, which is what
  makes offline actions work without a custom queue on the transport. The
  phone executes each one through the existing repositories, writes a result
  DataItem `/v1/<accountDid>/action-result/<uuid>` (ok / failed + reason),
  and deletes the action item. The watch deletes the result after applying it.
- **Pull on open**: the watch sends a `MessageClient` message `/v1/sync/request`;
  the phone answers by republishing from its **existing caches** immediately
  and enqueuing the existing one-time refresh (`WidgetWorkScheduler.refreshNow()`,
  network-constrained, `KEEP` policy). User-initiated, so not a new background
  schedule. That refresh runs inside the same worker, so it stays tagged
  `RefreshCaller.Widget`, with no new tag. **Pull-on-open covers feeds only.** DMs
  have no on-demand refresh. The watch already holds the last DM snapshot
  (DataItems persist), and it updates on the next 15-min poll.

### Phone side

- `WidgetRefreshRunner` (or a sibling runner invoked by the same worker)
  also refreshes the user's selected watch feeds, then calls
  `WearSnapshotPublisher.publishFeeds()`. Skipped entirely when no node has
  `nubecita_wear`.
- `DmPollRunner` (in `:feature:chats:impl`), after its `listConvos` +
  `getLog`, and only when a watch node is present: fetches `getMessages`
  (last ~20) for the convos `getLog` reported as changed, merges them into a
  small phone-side Room cache of the watch's DM snapshot (so unchanged threads
  keep their messages across process death), and publishes `/v1/<did>/dms`.
  It inherits the poll's existing gates: no work while the app is in the
  foreground (the next background poll catches up) and none while message
  checking is off. The first poll after pairing seeds the ~10 most recent
  convos once.
- **Module ownership**: everything DM-shaped (the snapshot builder, the
  dm-reply / dm-react executor) lives in `:feature:chats:impl`, next to
  `ChatRepository`. `:core:wear-sync` defines a `WearActionHandler` interface;
  feature modules contribute `@IntoSet` handlers (chats for DM actions,
  post-interactions/bookmarks wiring for like/bookmark), so no `:core` module
  depends on a feature `impl`.
- **Action expiry**: every action carries `createdAt` and `accountDid` from
  W3 onward. The phone drops actions whose `accountDid` isn't the signed-in
  account, and drops dm-reply / dm-react older than 1 h, answering `failed:
  expired` so the watch shows retry. Like / bookmark actions have no age limit.
- Sign-out publishes `/v1/session` = signed-out and deletes the account's
  snapshot items; the watch wipes its DB for that account.
- New **Settings → Watch** sub-page (visible only when a watch with the app
  is paired), built with `NubecitaListGroup` like the other settings
  sub-pages: checkboxes over the user's pinned feeds, Following checked by
  default, a 4th check is disabled with a "Up to 3 feeds" supporting line.
  Stored in `:core:preferences`. Strings in en / es-419 / pt-BR.
- Thumbnail sizing: reuse the widget prefetch pipeline, downscale to watch
  size (≈ 320 px on the long edge, WebP). Avatars small and shared across
  posts.

### Watch side

- Room DB (`:wear:data`): `synced_post` (per feed, ordered), `convo`,
  `message`, `pending_action`, `session`. Snapshot ingest replaces a feed's
  rows atomically in one transaction.
- **Optimistic overlay**: a pending like/bookmark is shown immediately by
  overlaying `pending_action` on the synced viewer state — same idea as the
  phone's post-interactions cache overlay. On an `ok` result the next snapshot
  confirms it; on `failed` the overlay is dropped and the post silently
  reverts. Liking needs the post's `uri` + `cid`; unliking needs the existing
  like record URI from the viewer state, so the snapshot must carry it.
- **Quick replies**: a fixed, localized list of short phrases ("On my way",
  "Sounds good", "Can't talk now", "👍" …) plus the emoji reactions the phone
  already supports. A queued reply shows in the thread as "Sending…" and
  turns into "Not sent — tap to retry" on a failed result.
- **States**: no paired node advertises `nubecita_phone`, meaning the user
  installed the watch app first (Play allows this even for non-standalone
  apps):
  - On an Android phone (`PhoneTypeHelper`), offer a one-tap install via
    `RemoteActivityHelper.startRemoteActivity()` to
    `market://details?id=net.kikin.nubecita`.
  - On an iPhone, show a plain "Nubecita for Wear needs the Android phone
    app" screen.

  When the phone app is present but not signed in, use the
  `OpenOnPhoneDialog` path ("Sign in on your phone"); synced data present → always show it with
  a "Updated 12 min ago" line; stale beyond a threshold → keep showing it, the
  label just says so.
- **Manifest / launch (W1)**:
  - `<uses-feature android:name="android.hardware.type.watch" />`
  - `<meta-data android:name="com.google.android.wearable.standalone" android:value="false" />`.
    The app is non-standalone, so Play hides it on watches with no paired
    phone.
  - Splash screen via `androidx.core:core-splashscreen:1.2.0`:
    `windowSplashScreenBackground = @android:color/black`, a 48 dp icon that
    matches the launcher icon, and `installSplashScreen()` before
    `super.onCreate()` (`WO-V15`). No trampoline activity.
  - The launcher activity gets a **unique, explicit** `taskAffinity` (e.g.
    `net.kikin.nubecita.wear.main`), shared by that task's children, so
    Recents shows one correct entry (`WO-V6`). Avoid `FLAG_ACTIVITY_NEW_TASK`
    / `CLEAR_TOP` starts.
  - `targetSdk` ≥ 34. Play requires it for new apps, and apps targeting
    ≤ 31 aren't discoverable on newer Wear OS.
- **UI rules**. Follow the `wear-compose-m3` skill's checklist. In particular:
  - Use one `AppScaffold`, a `ScreenScaffold` per screen, and `TimeText`.
  - Every scrolling list is a `TransformingLazyColumn` using
    `SurfaceTransformation` + `transformedHeight` +
    `minimumVerticalContentPadding`, with the scaffold's `contentPadding`
    passed through.
  - Snapping lists set **both** `TransformingLazyColumnDefaults.snapFlingBehavior`
    and `RotaryScrollableDefaults.snapBehavior`, so touch and rotary behave
    alike.
  - `EdgeButton` lives in the `ScreenScaffold` slot (never as the last list
    item), with the reverse-direction `Modifier.scrollable(…,
    overscrollEffect = rememberOverscrollEffect())`.
  - The `ScrollIndicator` is guarded with `!LocalScrollCaptureInProgress.current`.
  - Screens accept an initial anchor index, so previews can show top /
    middle / bottom (with the `EdgeButton` visible).
- **Quality floors** (Play review):
  - pure black background (`WO-V13`); don't carry over the phone's tonal
    surfaces;
  - touch targets of at least 48×48 dp, including the like / bookmark
    controls (`WO-V2`);
  - text of at least 12 sp for essential content and 10 sp for the rest
    (`WO-V14`);
  - no clipping or overlap at the largest font scale (`WO-V1`), checked with
    `@WearPreviewDevices` + `@WearPreviewFontScales` previews and screenshot
    tests.
- **Complication (W5)**:
  - Extend `SuspendingComplicationDataSourceService` (from
    `watchface-complications-data-source-ktx`).
  - Offer `SHORT_TEXT` (plus `MONOCHROMATIC_IMAGE`) with the unread DM count,
    and implement `getPreviewData()` so the watch-face editor shows a sample.
  - Make it push-only with `UPDATE_PERIOD_SECONDS = 0`: the system never
    polls it on a timer, and `ComplicationDataSourceUpdateRequester.requestUpdate()`
    runs when a DM snapshot lands.
  - The service is protected by `BIND_COMPLICATION_PROVIDER` and uses a
    single-color white vector icon.
  - Tapping it opens the messages list.

## Epics (each one bd epic → one `gh stack`)

| # | Epic | Depends on |
|---|---|---|
| W1 | **Wear foundation** — `:wear:app` + convention plugin, theme, Nav3 shell, `:core:wear-contract`, session snapshot + signed-out state, capability, Play release pipeline for the Wear AAB | — |
| W2 | **Feed browsing** — Watch settings page + feed picker, extended refresh, feed snapshot + thumbnails, watch feed list + post screen | W1 |
| W3 | **Likes & bookmarks** — action/result contract, phone `WearActionListenerService`, watch `pending_action` overlay | W2 |
| W4 | **Messages + quick replies** — DM snapshot from `DmPollWorker`, convo list + thread, quick replies over W3's action path | W1, W3 |
| W5 | **Unread-DMs complication** | W4 |
| — | Phone offline Feed screen (separate feature, not part of this spec) | — |

Each epic ships as one stack with at least one `feat:` child so it reaches Play.

### Release pipeline (W1)

- **Version code**: the Wear `versionCode` must be unique across all form
  factors. Following the Wear packaging docs, use a scheme **independent of
  the phone's**: a target-SDK prefix followed by the same
  `MAJOR/MINOR/PATCH` (e.g. `36` + version digits). Codify it in the
  `nubecita.android.wear.application` convention plugin. It must stay
  monotonic and below Play's 2,100,000,000 ceiling.
- **Tracks**: Play gives Wear OS **dedicated form-factor tracks** (named
  like `wear:internal` / `wear:production`) instead of mixing the Wear bundle
  into the phone track. **Verify the exact track names against the Play
  Developer API in W1** before changing the lanes. fastlane `supply` passes
  `track` through as a string. So `release.yaml` / the `internal` lane builds
  `:wear:app:bundleRelease` next to the phone bundle and uploads it to the
  Wear internal track. `promote.yaml` gains a Wear target, kept out of
  production until W2.
- **Closed testing gate**: Play only lets you opt in to Wear OS review
  after a Wear build has a closed-testing release. W1 (or early W2) therefore
  puts one on the Wear closed track, so the review finishes before W2's
  production push.
- **Store listing**:
  - Wear screenshots are raw 1:1 captures, with no device frames, no
    transparent backgrounds or masking, and no added graphics (`WO-G5`).
    They are staged separately from the framed phone screenshots, under
    `metadata/android/<locale>/images/wearScreenshots/`.
  - The listing description lists the main Wear features in every shipped
    locale, and mentions the complication once W5 ships (`WO-G2`).
- **Owner step (blocks W1's release part, not its code):** in Play Console,
  go to **Test and release → Advanced settings → Form factors → Add form
  factor → Wear OS**, add the Wear screenshots, and after the closed-testing
  release, opt in and accept the Wear review policy.

## Left to the builder

- Exact module split under `wear/` (flat vs api/impl), as long as the
  shared contract stays free of atproto runtime deps.
- Wear `minSdk` (suggest 33 / Wear OS 4), within the Wear Compose 1.8
  floors (compileSdk 37, AGP ≥ 9.1, both already met). `targetSdk` must be at
  least 34 (see Manifest / launch).
- Snapshot sizes (suggest ~50 posts per feed, ~10 convos × ~20 messages),
  the staleness threshold, and the exact quick-reply phrase list.
- Whether the watch gets Crashlytics/Analytics in W1 or later.
- Thumbnail format/size details and Asset cleanup.
- Bench/fixture strategy for the watch (a canned snapshot for previews,
  screenshot tests and an offline demo mode).

## Calls I made (check these)

- Silent revert when the phone's like/bookmark fails (no error toast on the
  watch); visible "Not sent — tap to retry" for DM replies only.
- Sharing the applicationId with the phone app (required by the Data Layer).
- Pull-on-open triggers the existing one-time refresh — counted as
  user-initiated, not a new background schedule.
- Following stays the default synced feed and counts toward the 3.
- DataItems as the offline action queue instead of a custom retry loop.
- A small phone-side Room cache for the watch's DM snapshot (needed so
  "changed threads only" keeps unchanged threads' messages).
- DM code stays in `:feature:chats:impl` behind a `WearActionHandler`
  multibinding. Chats is not moved into `:core`.
- No DM refresh on pull-on-open; the pull refresh keeps the `Widget` tag.
- `setUrgent()` only for watch actions and the pull-on-open republish;
  periodic snapshots stay non-urgent.
- An iPhone-paired watch gets an "needs the Android phone app" screen, not
  an App Store link (there is no iOS app).
- Phone notifications already show on the watch, and no inline-reply work
  is planned for them. Watch quick replies live in the app only.

## Review log

- 2026-10-25: a builder-check subagent raised DM fetching, pull-on-open for
  DMs, action expiry, release staging and module ownership, which were
  answered above.
- 2026-10-25: a Gemini review was checked against the Android docs
  (`android docs fetch`) and the `wear-compose-m3` skill.
  - **Adopted:** `setUrgent` for actions, non-standalone manifest +
    phone-app install prompt, listener path filters, splash / quality floors,
    complication details, Wear screenshot rules.
  - **Corrected:**
    - `taskAffinity` is a unique name, not `""`;
    - Wear uses dedicated form-factor tracks (track names still to verify in
      W1), not the same track as the phone bundle;
    - the version-code scheme is independent of the phone's, per the
      packaging docs.
  - **Rejected:** notification inline-reply parity (out of scope).
  - **Added from the docs:** the closed-testing gate before Wear opt-in, and
    `targetSdk` ≥ 34.
