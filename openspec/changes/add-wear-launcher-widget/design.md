# Design

Full brainstormed design: `docs/superpowers/specs/2026-11-12-wear-launcher-widget-design.md`. Step-level plan: `docs/superpowers/plans/2026-11-12-wear-launcher-widget.md`.

## Context

- The watch app starts on a landing screen (Home / Messages / New post, #1181). `MainActivity` already handles one deep link, `ACTION_OPEN_MESSAGES`, used by the unread-DMs complication via a `NEW_TASK | CLEAR_TASK` `PendingIntent`.
- Synced DMs live in the watch's Room store; `WatchDmRepository.latestUnreadCount()` returns the unread total or null before DMs ever sync. `WatchSyncIngester` notifies every `DmSyncObserver` multibinding when DMs change; the complication refreshes this way.
- `compileSdk = 37`, `minSdk = 33` (Wear OS 4). Tests are JUnit 5 on the JUnit Platform; the repo has no Robolectric.
- See proposal.md for why.

## Goals / Non-Goals

**Goals:** one surface that reaches every supported watch; zero background cost; regression tests for the widget document and launch intents.

**Non-Goals:** see proposal.md; additionally no shared `build-logic` test changes.

## Decisions

1. **Single service for widget + tile.** One `GlanceWearWidgetService` declares both `BIND_WIDGET_PROVIDER` and `BIND_TILE_PROVIDER`. *Alternative:* a separate ProtoLayout `TileService` beside the widget — two UIs to maintain for a launcher. The docs' compatibility mode renders the widget as a full-screen tile on Wear OS 4–6 and Pixel (renderer 1.6+), which is the owner's watch.
2. **Pure model, no ViewModel.** `launcherWidgetModel(unread: Int?)` maps the count to the card's state; the widget reads the repository on each update request. Remote Compose runs in the host process, so app-side state holders don't apply.
3. **Push via `DmSyncObserver`.** A `LauncherWidgetUpdater` observer calls `triggerUpdateAll`. *Alternative:* a periodic update — rejected (battery is the top priority).
4. **Launch targets as a tiny pure layer.** `WearLaunchTarget` + `launchTargetOf(action, restoring)` + `initialBackStack(target)` generalise today's `openMessages` boolean; the complication and widget share one `launchIntent(context, target)`. New post resolves with `resolvePendingNewPost(feedStatus, postingAvailable)` — wait while the feed is loading, then open or drop — so availability that arrives late can't pop the input open later.
5. **Messages icon, not avatar.** The watch's synced conversations carry no avatars; adding them is a sync-contract change outside this epic.
6. **Robolectric scoped to `:wear:app`.** JUnit 4 tests run through the JUnit vintage engine beside Jupiter, to test `provideWidgetData` + `captureRawContent` (catches pre-release library bumps that break the document) and the real `PendingIntent`s. *Fallback:* if the vintage engine can't coexist with Jupiter 6, drop it and rely on emulator checks rather than change shared test config.

## Risks / Trade-offs

- [Pre-release libraries churn] → render test per model state; versions pinned in the catalog.
- [Robolectric downloads the Android framework jar in CI, ~30–60 s per uncached run] → acceptable; a CI cache step can follow if it matters.
- [Widget picker preview drifts from the real UI] → the PNG is rendered from the real composable, not drawn by hand.
- [DM clear on sign-out may not notify observers] → child 3 pins it with an ingester test and fixes the ingester if needed.

## Migration Plan

New surface; nothing to migrate. Rollback = revert the stack; users re-add nothing (the surface simply disappears from the picker).
