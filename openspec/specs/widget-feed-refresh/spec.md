# widget-feed-refresh Specification

## Purpose
`:core:widget-sync` keeps the home-screen widget's feed fresh **off-app**: a battery-cooperative WorkManager worker refreshes the widget's feed(s) into `:core:feed-cache` and triggers a widget re-render through a **Glance-free `WidgetUpdater` seam**. Scheduling is periodic (15-minute platform floor) + on-demand, **foreground-guarded** (never write while the app's own feed `PagingSource` is live — Room invalidation is table-level), **per-feed-independent**, and retries only on total failure. Single-account MVP refreshing Following + a fixed Discover feed. Implemented in `nubecita-lgoo.2` (sub-project B of the Glance feed widgets epic); the Glance-backed `WidgetUpdater` impl is supplied by sub-project C. Reuses the DM-notification worker patterns; battery is the non-negotiable constraint (no expedited work / foreground service / wakelocks; Doze-cooperative).

## Requirements

### Requirement: Background refresh of the widget feed cache

The system SHALL refresh the home-screen widget's feed(s) into `:core:feed-cache` from a background WorkManager worker, so the widget can show fresh content without the app being opened. The MVP SHALL refresh the signed-in account's Following and fixed Discover partitions. Work SHALL only run for a signed-in user.

#### Scenario: Backgrounded refresh updates the cache
- **WHEN** the worker runs while the app is backgrounded and the user is signed in
- **THEN** it refreshes the configured feed partitions via `:core:feed-cache` and then triggers a widget re-render

#### Scenario: Signed out
- **WHEN** the worker runs while the user is signed out
- **THEN** it does nothing (success no-op) and performs no fetch or cache write

### Requirement: Foreground guard

The worker SHALL NOT write the cache while the app is foregrounded, because Room invalidation is table-level and a write would invalidate an actively-scrolling app `PagingSource`. While foregrounded the app's own `RemoteMediator` is the cache writer. (The signal is process-wide, so a refresh is also skipped while the app is foregrounded on a non-feed screen — accepted; the next periodic run / app-background catches up.)

#### Scenario: Foregrounded run does not write
- **WHEN** the worker runs while the app is in the foreground
- **THEN** it returns success without refreshing or writing the cache

### Requirement: Periodic and on-demand scheduling, battery-cooperative

The system SHALL register a unique **periodic** refresh at the platform-minimum interval (15 minutes) with a network-connectivity constraint, and SHALL expose a unique **on-demand** one-time refresh (for widget add / manual refresh) since periodic work cannot be forced to run on demand. It MUST NOT use expedited work, a foreground service, exact alarms, or wakelocks, and SHALL accept Doze / App-Standby deferral. Scheduling is registered while signed in and cancelled on sign-out.

#### Scenario: Scheduled when signed in
- **WHEN** the user is signed in
- **THEN** a unique periodic work request is enqueued at the 15-minute platform-minimum interval requiring network connectivity

#### Scenario: On-demand refresh
- **WHEN** a widget is added or a manual refresh is requested
- **THEN** a unique one-time work request is enqueued (network-constrained) without disturbing the periodic schedule, and a duplicate in-flight refresh is suppressed (WorkManager `ExistingWorkPolicy.KEEP`)

#### Scenario: Cancelled on sign-out
- **WHEN** the user signs out
- **THEN** the periodic refresh work is cancelled

#### Scenario: A scheduling error does not freeze the scheduler
- **WHEN** enqueuing or cancelling the work throws (e.g. a WorkManager init/DB blip)
- **THEN** the failure is logged and the scheduler keeps reacting to subsequent sign-in / sign-out events

### Requirement: Per-feed refresh with retry only on total failure

The worker SHALL refresh each feed independently and request a WorkManager retry **only when every feed failed**. A partial success (at least one feed refreshed) SHALL return success, so a retry never re-fetches an already-refreshed feed; the failed feed is picked up by the next periodic run.

#### Scenario: All feeds fail → retry
- **WHEN** every feed refresh fails transiently
- **THEN** the worker returns a retry result so WorkManager re-runs it later

#### Scenario: Partial failure → success without redundant re-fetch
- **WHEN** at least one feed refreshes successfully and another fails
- **THEN** the worker returns success (preventing a WorkManager retry from re-fetching the succeeded feed) and the failed feed is refreshed on the next periodic run

#### Scenario: An unexpected error in one feed does not abort the others
- **WHEN** refreshing one feed throws an unexpected error
- **THEN** that feed is treated as failed and the remaining feeds are still refreshed

### Requirement: Refresh triggers off-scroll eviction

After refreshing a feed partition, the worker SHALL run the count-cap trim (`:core:feed-cache` `trimToCap`) off the active-scroll path to bound cache growth.

#### Scenario: Trim after refresh
- **WHEN** the worker successfully refreshes a feed partition
- **THEN** it trims that partition to the retention cap (default 500 posts, off-scroll), leaving the partition's cursor intact

### Requirement: Widget update is triggered through a Glance-free seam

The worker SHALL trigger the widget re-render through a `WidgetUpdater` seam (not a direct Glance call), so this capability carries no `androidx.glance` dependency. A no-op default SHALL be provided; the Glance-backed implementation is supplied by the widgets sub-project. A failure of the widget update SHALL NOT fail the refresh or trigger a retry — the cache is already refreshed.

#### Scenario: Updater invoked after a successful refresh
- **WHEN** a background refresh completes successfully
- **THEN** the worker invokes `WidgetUpdater.updateFeedWidgets()`

#### Scenario: No-op updater keeps the module Glance-free
- **WHEN** no Glance-backed updater is bound (this module alone)
- **THEN** the worker still runs to completion using the no-op updater, and the module declares no Glance dependency

#### Scenario: Updater failure does not trigger a retry
- **WHEN** the widget update throws (e.g. a Glance / AppWidgetManager IPC error)
- **THEN** the failure is logged and the worker still returns success (no network-heavy retry)
