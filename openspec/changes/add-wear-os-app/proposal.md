## Why

Nubecita users with a Wear OS watch can't glance at their timeline, react to a post, or read a DM without pulling out the phone. That matters most when the phone isn't nearby at all (a run, the gym). A companion watch app covers those moments without a second Bluesky sign-in and without new background battery cost on either device.

## What Changes

- New Wear OS app, `:wear:app`, built only on Compose: Wear Compose Material 3 and Wear Navigation 3 (pre-release `1.8.0-alpha02` allowed), no XML layouts, no Horologist. It shares the phone's applicationId `net.kikin.nubecita` and signing key, and is marked non-standalone.
- New shared contract module, `:core:wear-contract` (DTOs, `/v1/` Data Layer paths, capabilities, schema version), plus a phone-side `:core:wear-sync` (publisher, action listener, sync-request handler).
- **The phone stays the only Bluesky client.** It publishes snapshots (session, up to 3 feeds, recent DMs) as Wear Data Layer `DataItem`s. It does this only from its existing 15-minute widget refresh and DM poll, plus a user-initiated pull when the watch app opens.
- The watch keeps the snapshots in its own Room database and works fully offline.
- Like/unlike, bookmark/unbookmark, DM quick replies and emoji reactions are written on the watch as urgent action `DataItem`s and run on the phone through the existing repositories.
- New phone **Settings → Watch** page: a feed picker (Following by default, max 3).
- The widget refresh also refreshes the selected watch feeds while a watch is paired. The DM poll also fetches messages for changed threads while a watch is paired.
- New unread-DMs complication.
- The release pipeline builds and uploads a Wear bundle: an independent version-code scheme, dedicated Wear form-factor tracks, and a closed-testing release before the Wear OS opt-in. The Wear app goes to production with feed browsing (W2).

## Non-goals

- The watch never signs in to Bluesky or calls it directly (no standalone / LTE mode).
- Video playback on the watch, free-text or voice replies, composing posts, reposts, quotes, follows.
- Feed tiles or Wear widgets.
- Inline-reply parity for phone notifications bridged to the watch.
- Offline browsing in the **phone's** Feed screen. That is a separate feature (`nubecita-j16x`).
- A Pro gate: the Wear app is free for every signed-in user.

## Capabilities

### New Capabilities
- `wear-app-shell`: Wear app module, manifest, splash, theme, navigation shell, quality floors, and the states shown when the phone is missing or signed out.
- `wear-data-sync`: the phone↔watch Data Layer contract: capabilities, paths, urgency, schema versioning, listener filters, session sync, pull-on-open.
- `wear-feed`: the phone Watch settings page and feed picker, feed snapshots with thumbnails, and the watch feed list and post screens.
- `wear-post-actions`: offline like and bookmark actions from the watch, with optimistic overlay and silent revert.
- `wear-messages`: the DM snapshot, the watch conversation list and thread, and quick replies and reactions with expiry.
- `wear-complication`: the push-only unread-DMs complication.

### Modified Capabilities
- `widget-feed-refresh`: also refreshes the user's selected watch feeds (and hands them to the publisher) while a watch with the app is paired.
- `dm-background-notifications`: while a watch is paired, the poll also fetches messages for the threads `getLog` reports as changed and publishes the DM snapshot.
- `play-track-promotion`: promotes the Wear bundle on its own form-factor tracks, held out of production until W2.

## Impact

- **New modules:** `:wear:app`, `:wear:data`, `:core:wear-contract`, `:core:wear-sync`, and a new `nubecita.android.wear.application` convention plugin.
- **Touched modules:** `:core:widget-sync`, `:feature:chats:impl` (the DM snapshot and DM action handler, behind a `WearActionHandler` multibinding), `:core:preferences`, the settings feature.
- **New dependencies:**
  - `androidx.wear.compose:compose-material3`, `compose-foundation` and `compose-navigation3` (`1.8.0-alpha02`)
  - `androidx.wear.watchface:watchface-complications-data-source-ktx`
  - `com.google.android.gms:play-services-wearable`
  - `androidx.wear:wear-remote-interactions` and `wear-phone-interactions`
  - `androidx.core:core-splashscreen:1.2.0`
- **Deviations from the baseline:**
  - Wear Compose M3 replaces phone Material 3 on the watch, and the watch has its own black theme rather than `:designsystem`.
  - The watch's Room DB is separate from `:core:database`.
  - The MVI, Hilt and Room conventions otherwise apply unchanged.
- **CI / release:** `release.yaml`, fastlane `internal`, `promote.yaml`, Play Console Wear form-factor enrollment (an owner step).
- **Battery:** no new periodic work, wakelock, expedited job or foreground service; no new `RefreshCaller`.
- **Tracking:**
  - Delivered as epics `nubecita-sn52` (W1), `nubecita-jh9c` (W2), `nubecita-hohv` (W3), `nubecita-by5w` (W4) and `nubecita-9o3a` (W5).
  - Each epic lands as one `gh stack`.
  - Design source: `docs/superpowers/specs/2026-10-25-wear-os-app-design.md`.
