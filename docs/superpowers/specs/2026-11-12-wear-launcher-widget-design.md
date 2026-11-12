# Wear launcher widget — design

Date: 2026-11-12 · Status: approved · Epic `nubecita-hzfs` · OpenSpec `add-wear-launcher-widget` · Follows `nubecita-o7rr`

## Intent

Put Nubecita one swipe from the watch face. Today, reaching the watch app means
pressing the crown, finding Nubecita in the app list, and tapping it. A Wear OS
widget in the widget carousel (swipe from the watch face) makes the app — and
its three main destinations — one swipe and one tap away, with the unread DM
count visible on the way.

This is deliberately the **basic** version: a launcher with a glance, not a
content surface. The owner weighed that it largely mirrors the landing screen
(#1181) and the unread-DMs complication, and chose it for the reachability.

## What it looks like

One **large** widget, laid out like the system Weather widget (reference:
"Layout A" from the brainstorm):

```
        ( logo )
        Nubecita
  ┌──────────┐ ╭────────╮
  │      ✉   │ │ ⌂ Home │
  │          │ ╰────────╯
  │  3       │ ╭────────╮
  │  new     │ │ ✎ Post │
  └──────────┘ ╰────────╯
```

- **Header:** the Nubecita logo and name.
- **Messages — tall card (left):** the synced unread DM total as a large number
  with a "new messages" label (pluralized, localized), and the messages icon.
  With nothing unread it shows the icon and "No new messages".
  Tap → the conversation list (same destination as the complication).
- **Home — pill (top right):** tap → the feed.
- **New post — pill (bottom right):** tap → the app opens straight into the
  post text input (dictation / keyboard), exactly as the landing screen's
  New post button does.
- No bottom text line. (The brainstorm's message-preview line is out of scope.)
- Colours, shapes and type follow the Wear Material 3 / Remote Material 3
  defaults on the app's existing brand palette; the Messages card is the
  contrasting surface, matching the landing screen's middle segment.

## Goals

1. Swipe from the watch face to the widget, tap, and land in the right place.
2. See the unread DM count on the way.
3. Zero background cost: the widget never polls and never wakes the watch on a
   timer.

## Non-goals (this epic)

- Message previews, sender names or avatars.
- Feed content (top post, thumbnails).
- A small-container variant (the "Steps-style" single card).
- Any phone-side or sync-contract change.

## Anti-goals (would make it a failure even if it works)

- Any periodic refresh or background work that costs battery
  (battery is the project's top priority).
- A widget that shows a stale or wrong count after sign-out or an account
  switch.
- Tapping a tile and landing somewhere other than its destination (e.g. on the
  landing screen when the user asked for Messages).

## Constraints and existing building blocks

- **Libraries** (pre-release, consistent with the Wear app's design D8 choice of
  pre-release Wear Compose): `androidx.glance.wear:wear` + `wear-core` +
  `wear-tooling-preview`, `androidx.compose.remote:remote-creation-compose` /
  `remote-creation` / `remote-core` / `remote-tooling-preview`,
  `androidx.wear.compose.remote:remote-material3`. Latest at time of writing:
  glance-wear 1.0.0-alpha22, compose-remote 1.0.0-alpha23, wear remote-material3
  1.0.0-alpha15. Follow the `wear-widgets` skill (android CLI) for setup,
  manifest/provider XML, preview PNG and verification.
- **Where it shows up — one service, both surfaces.** Wear widgets render as
  partial-height cards only on Wear OS 7 watches that support them (e.g.
  Galaxy Watch). Everywhere else the system shows tiles — including Pixel
  Watch, even on Wear OS 7. Use the documented *single service* setup: one
  `GlanceWearWidgetService` declaring both `androidx.glance.wear.action.BIND_WIDGET_PROVIDER`
  and `androidx.wear.tiles.action.BIND_TILE_PROVIDER`, so Wear OS 4–6 and
  Pixel Watch show it as a **full-screen tile** (requires tile renderer 1.6+)
  and partial-height watches show the large widget card. The app's
  `minSdk = 33` (Wear OS 4) is the floor; Wear OS 3 isn't supported anyway.
  The layout must read well in both forms — the full-screen tile is the form
  the owner's Pixel Watch 4 shows, and matches the reference tiles.
- `compileSdk = 37` is already set by `nubecita.android.wear.application`.
  The Wear forbidden-dependencies guard (`WearForbiddenDependencies.kt`) must
  still pass with the new dependencies.
- **Data:** `WatchDmRepository.latestUnreadCount()` (null before DMs ever sync),
  already used by `UnreadDmsComplicationService`.
- **Push updates:** `WatchSyncIngester` notifies every `DmSyncObserver`
  multibinding when synced DMs change; `UnreadDmsComplicationUpdater` is the
  existing observer. The widget adds its own observer that requests a widget
  update — no other refresh path.
- **Deep links:** `MainActivity.ACTION_OPEN_MESSAGES` already opens the
  conversation list above the landing screen. The widget needs two more:
  open the feed, and open New post. Both follow the same pattern — handled only
  on a fresh launch (`savedInstanceState == null`), the back stack starts with
  the landing screen underneath so back returns there, and no trampoline
  activity (`WO-V15`).
- **Bench flavor:** the bench build must render the widget from its fake
  repositories with no network, so store screenshots and smoke tests work.
- **Store quality:** the original spike noted a tile/widget needs Wear quality
  items `WO-V9` and `WO-V10` and a mention in the Play listing. The builder
  checks both against the current Wear app quality guidelines.

## Decided

- Single service serving both the widget and tile protocols — *my call*,
  from the docs' compatibility table; it's the only way the owner's Pixel
  Watch shows it at all.
- Layout A, large container only, three tiles, no preview text (owner).
- Ship it as one epic, landed as a `gh stack` (owner).
- Messages icon instead of the newest sender's avatar — *my call*: the watch's
  synced conversations carry no avatars, and adding them is a sync-contract
  change, out of scope for the basic version.
- Tiles are always tappable — *my call*: the widget doesn't know whether the
  phone currently supports posting or has synced DMs; the app already handles
  those states (Messages / New post are disabled on the landing screen, gate
  screens explain sign-in). If New post isn't available, the deep link lands on
  the landing screen instead of opening the input.
- Before DMs ever sync (or signed out), the Messages card shows the icon and
  "Messages" with no count — *my call*, mirroring the complication's no-data.
- Counts above 99 show as "99+" — *my call*, reusing `unreadDmsLabel`.

## Proposed stack (one PR per bd child)

1. **Deep links for feed and New post.** `MainActivity` actions to open the feed
   and to open New post, with landing underneath; unit-testable back-stack
   start. Useful on its own, no widget dependency.
2. **Widget skeleton.** Dependencies, `GlanceWearWidgetService` + provider XML
   (large container), picker preview PNG, static Layout A wired to the three
   deep links, with a placeholder count.
3. **Live unread count.** Read `latestUnreadCount()`, the empty and no-data
   states, and a `DmSyncObserver` that requests a widget update on sync; account
   switch / sign-out clears it.
4. **Release readiness.** Bench rendering, localized strings (es-419, pt-BR),
   quality checklist (`WO-V9`, `WO-V10`), the Play listing mention.

The release type is `feat` (the widget is user-visible).

## Left to the builder

- Exact Remote Compose composition, sizes and the logo asset for the header.
- How the New post deep link triggers the text input (e.g. a one-shot flag the
  landing entry consumes).
- Verification needs the Wear OS 7 emulator image for the widget form (the
  `wear-widgets` skill says lower images aren't suitable) plus a Wear OS 4–6
  or Pixel image for the tile form; the existing `Wear_OS_Large_Round` AVD's
  version must be checked.
- Test strategy per child, within the repo's conventions (JUnit 5 unit tests for
  pure logic, screenshot/preview where the tooling supports it, a bench device
  pass on a Wear emulator).
