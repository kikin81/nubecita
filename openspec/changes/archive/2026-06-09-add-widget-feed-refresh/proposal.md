## Why

Sub-project A built the offline feed cache (`:core:feed-cache`), but nothing populates or refreshes it in the background — today only the app feed (after sub-project E) writes it. For a home-screen widget to show fresh content **without the app being opened**, something must periodically refresh the cache off-app and tell the widget to re-render. This is sub-project **B** (bead `nubecita-lgoo.2`) of the Glance feed widgets epic (`nubecita-lgoo`); full rationale + decisions in `docs/superpowers/specs/2026-06-08-glance-feed-widgets-design.md`.

## What Changes

- **New `:core:widget-sync` module** — a Glance-free background worker that refreshes the widget's feed(s) into `:core:feed-cache` and triggers a widget re-render via a seam.
- **Periodic + on-demand WorkManager scheduling** (reusing the DM-notification worker patterns): a `PeriodicWorkRequest` at the 15-minute platform floor (`NetworkType.CONNECTED`, `ExistingPeriodicWorkPolicy.UPDATE`) **plus** a `OneTimeWorkRequest` for widget-add / manual refresh (unique work, `ExistingWorkPolicy.KEEP`) — periodic work can't be forced to run on demand. Battery-cooperative: no expedited work, no foreground service, no wakelocks; Doze/App-Standby deferral accepted.
- **Refresh + off-scroll eviction**: the worker drives a `:core:feed-cache` REFRESH for the widget's `FeedKey`s and then runs `trimToCap` off-scroll (the count-cap eviction A deferred to B).
- **Foreground guard**: the worker writes the cache **only when the app is backgrounded** (Room invalidation is table-level — writing while the app feed scrolls would invalidate its `PagingSource`). When foregrounded it skips the write (the app's own `RemoteMediator` is the writer).
- **`WidgetUpdater` seam**: an interface (`suspend fun updateFeedWidgets()`) the worker calls after a successful refresh, with a **no-op default** so B is **Glance-free** and independently testable. Sub-project C provides the real `GlanceAppWidget.update()`-backed implementation.
- **Reactive scheduling**: a `DmPollScheduler`-style observer registers/cancels the periodic work on signed-in state.
- MVP refreshes the **Following** + fixed **Discover** feeds (the free widgets); the Pro configurable feed arrives with C/D.

## Capabilities

### New Capabilities
- `widget-feed-refresh`: the `:core:widget-sync` module — the background worker, periodic + on-demand scheduling, the foreground-guarded cache refresh + off-scroll trim, and the `WidgetUpdater` seam.

### Modified Capabilities
<!-- None. :core:feed-cache is consumed unchanged (its public FeedRepository). No existing capability's requirements change. -->

## Impact

- **New module:** `:core:widget-sync` (`nubecita.android.library` + `nubecita.android.hilt`; depends on `:core:feed-cache`, `:core:auth`, `:core:common`, WorkManager). **No `androidx.glance` dependency** (the widget-update call is behind the `WidgetUpdater` seam).
- **`:app`:** registers the `DmPollScheduler`-style scheduler in the production-flavor `AppInitializer` (mirrors the DM worker); the `@HiltWorker` is picked up by the existing `HiltWorkerFactory` / `Configuration.Provider`.
- **Depends on:** sub-project A (`:core:feed-cache`, complete).
- **Bead:** `nubecita-lgoo.2`.
