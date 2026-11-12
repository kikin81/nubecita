# Proposal

## Why

Reaching the Nubecita watch app means pressing the crown, finding it in the app list and tapping it. Users want it one swipe from the watch face — the way Weather, Steps or Maps sit in the carousel — with their unread DM count visible on the way. Wear OS now offers widgets (Glance Wear + Remote Compose) that the system also shows as full-screen tiles on Pixel and older Wear OS watches, so one surface reaches every supported watch.

## What Changes

- A Nubecita launcher widget (large container): a header, a Messages card with the synced unread DM count ("No new messages" at zero, no count before DMs ever sync, "99+" above 99), and Home and New post pills. Each tile opens the app at its destination.
- One service serves both the widget and tile protocols, so Pixel Watch and Wear OS 4–6 show it as a full-screen tile and partial-height Wear OS 7 watches show the widget card.
- The widget refreshes only when the phone syncs DMs (push via the existing `DmSyncObserver` hook) — no periodic updates.
- The watch app gains launch targets for the feed and New post, beside the existing Messages one; deep links open on a fresh launch with the landing screen underneath, and New post falls back to the landing screen when posting isn't available.
- Robolectric (JUnit 4 via the vintage engine) is added to `:wear:app` tests only, to pin widget rendering and launch intents.

## Non-goals

- Message previews, sender names or avatars (would need a phone↔watch sync-contract change).
- Feed content on the widget.
- A small-container variant.
- Any phone-side change.

## Capabilities

### New Capabilities

- `wear-launcher-widget`: the watch's carousel widget/tile — what it shows for each unread state, where each tile leads, how it refreshes — and the app launch targets it relies on.

### Modified Capabilities

_None._ (The Wear app's existing capabilities are still in the in-flight `add-wear-os-app` change, not archived specs.)

## Impact

- `:wear:app` only: new `widget/` and `launch/` packages, `MainActivity` actions, `NubecitaWearApp` start back stack, the complication's tap intent, manifest service + provider XML + picker PNG, strings in three locales.
- New dependencies (pre-release, consistent with the Wear app's existing pre-release Wear Compose choice): `androidx.glance.wear`, `androidx.compose.remote`, `androidx.wear.compose.remote:remote-material3`; test-only `robolectric` and `junit-vintage-engine`.
- Deviation from the baseline: the widget is Remote Compose, not app Compose, and holds no ViewModel — it renders a pure model from the repository on each update request. Robolectric is new to the repo (scoped to `:wear:app`).
- Play: the Wear listing mentions the widget; Wear quality items `WO-V9` / `WO-V10` apply.
- Tracking: epic `nubecita-hzfs` (children `.1`–`.4`), landed as one `gh stack`.
