## Context

Sub-project A shipped `:core:feed-cache` (`FeedRepository`: `pagedFeed`, `head(feedKey, n)`, `trimToCap`, `clearAccount`; a `RemoteMediator`; the DID-keyed Room cache). B is the background refresher that keeps the home-screen widget's feed fresh off-app. The DM-notification feature (`feature/chats/impl/.../worker`) established the battery-cooperative WorkManager pattern this reuses: `@HiltWorker` + `HiltWorkerFactory` + `Configuration.Provider`, a `DmWorkScheduler` seam over `WorkManager` for JVM-unit-testability, a `DmPollScheduler` reactive observer, and `AppForegroundSignal`. Epic decisions: `docs/superpowers/specs/2026-06-08-glance-feed-widgets-design.md` (D6, D9, D10, cross-writer note).

## Goals / Non-Goals

**Goals**
- Refresh the widget's feed(s) into `:core:feed-cache` on a battery-cooperative schedule and on demand, and trigger a widget re-render — without the app being open.
- Stay Glance-free (the widget-update call is behind a seam) so B ships + tests independently of sub-project C.
- Never invalidate an actively-scrolling app `PagingSource` (foreground guard).

**Non-Goals** (other sub-projects)
- The Glance widgets + config activity (C), Pro gating (D), app feed migration (E). **B must not depend on `androidx.glance`.**

## Decisions

### D-B1. New `:core:widget-sync` module (not in a feature module)
The worker is non-UI infra (depends on `:core:feed-cache` + `:core:auth` + WorkManager). A `:core:*` module keeps it **Glance-free by construction** and lets sub-project C (`:feature:widgets`, which owns the Glance UI) depend on it for the on-demand enqueue + provide the real `WidgetUpdater`. Layering: C (feature, Glance) → `:core:widget-sync` (B) → `:core:feed-cache` (A). *Alternative — put the worker in the C feature module (like the DM worker lives in `:feature:chats:impl`):* couples B to C's existence and risks an accidental Glance import; rejected since C doesn't exist yet and B must stay Glance-free.

### D-B2. Reuse the DM worker scaffolding
- `@HiltWorker WidgetRefreshWorker(... runner: WidgetRefreshRunner)` — a one-line delegate to a plain injectable `WidgetRefreshRunner` (logic + its unit tests live there), mirroring `DmPollWorker`/`DmPollRunner`.
- A `WidgetWorkScheduler` interface (real impl wraps `WorkManager`) — the schedule/cancel decision is JVM-unit-tested against a fake, mirroring `DmWorkScheduler`/`WorkManagerDmWorkScheduler`.
- A `WidgetRefreshScheduler` reactive observer (`combine(sessionState, …).distinctUntilChanged()`), started from the production `AppInitializer`, mirroring `DmPollScheduler`.

### D-B3. Periodic + on-demand, coordinated (epic D10)
- **Periodic**: `enqueueUniquePeriodicWork("widget-feed-refresh-periodic", UPDATE, PeriodicWorkRequest(15 min, NetworkType.CONNECTED))`. 15-minute platform floor; battery-cooperative (no expedited, no FGS, no wakelocks; Doze deferral accepted).
- **On-demand** (widget add / manual refresh, invoked by C): `enqueueUniqueWork("widget-feed-refresh-now", KEEP, OneTimeWorkRequest(NetworkType.CONNECTED))` — periodic work can't fire on demand. `KEEP` suppresses duplicate in-flight refreshes.
- The refresh op is idempotent (a `:core:feed-cache` REFRESH is a transactional clear+insert), so a periodic/one-time overlap can't corrupt the cache.

### D-B4. Foreground guard (epic cross-writer note)
Room's `InvalidationTracker` is table-level, so a cache write invalidates any active app `PagingSource`. The runner checks `AppForegroundSignal` first: **if foregrounded, return success WITHOUT writing** (the app's own `RemoteMediator` is the writer). It refreshes only when backgrounded, when no `PagingSource` is being collected.

### D-B5. `WidgetUpdater` seam — Glance-free
`interface WidgetUpdater { suspend fun updateFeedWidgets() }`. The runner calls it after a successful refresh. B binds a **no-op** default (`@Provides` unless C overrides), so B has no Glance dependency and is fully testable. Sub-project C provides the real implementation (calls `FeedWidget().updateAll(context)` / `GlanceAppWidget.update()`).

### D-B6. Which feeds + cadence
MVP refreshes the **Following** (`FeedKey.following(did)`) and fixed **Discover** (`FeedType.DISCOVER`, the known generator URI) partitions for the signed-in account — matching the free widgets. After each successful per-feed REFRESH the runner calls `trimToCap(feedKey)` (off-scroll eviction A deferred to B). The Pro configurable feed's key is added in C/D.

### D-B7. Runner outcome → WorkManager Result
The runner returns `SUCCESS` / `RETRY` (mirrors `DmPollRunner.Outcome`); the worker maps to `Result.success()` / `Result.retry()`. Gates first (signed-out → success no-op; foregrounded → success no-op), then per-feed refresh (a failed fetch → `RETRY`), then `trimToCap`, then `WidgetUpdater`.

## Risks / Trade-offs

- **Refresh while app foregrounded would invalidate the app feed** → foreground guard (D-B4); the worker writes only when backgrounded.
- **Battery** → 15-min floor, network constraint, no expedited/FGS/wakelocks, Doze-cooperative; identical posture to the DM worker (the established rule).
- **Widget exists only in C** → the `WidgetUpdater` no-op default (D-B5) makes B shippable + green now; C swaps in the real updater. Until C, the worker refreshes the cache and the update call is a no-op (harmless).
- **Authenticated background fetch** → `:core:feed-cache` already uses the `:core:auth` refresh-mutex `XrpcClientProvider`; no logout race.

## Migration Plan

1. Land `:core:widget-sync` with the worker, scheduler seam, reactive scheduler, foreground-guarded runner, and the no-op `WidgetUpdater`.
2. Wire the scheduler `start()` into the production `AppInitializer` (mirrors the DM scheduler). Bench flavor never contributes it.
3. Work-testing instrumentation pass (`WorkManagerTestInitHelper` / `TestListenableWorkerBuilder`) validates the real enqueue + worker run (run-instrumented).
4. Sub-project C provides the real `WidgetUpdater` + enqueues the on-demand refresh from the widget.

Rollback: revert the module + the `AppInitializer` line; no schema or data changes.

## Open Questions

- Exact periodic interval if we ever expose a user cadence setting (default = 15-min floor for now).
- Whether the on-demand `OneTimeWorkRequest` should be `KEEP` vs `APPEND_OR_REPLACE` for rapid manual taps (start with `KEEP`).
- Whether widget refresh should respect the existing `MessageCheckingPreference`-style gate or a new per-widget toggle (defer to C, where the widget settings surface lives).
