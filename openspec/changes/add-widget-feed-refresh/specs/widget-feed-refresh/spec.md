## ADDED Requirements

### Requirement: Background refresh of the widget feed cache

The system SHALL refresh the home-screen widget's feed(s) into `:core:feed-cache` from a background WorkManager worker, so the widget can show fresh content without the app being opened. The MVP SHALL refresh the signed-in account's Following and fixed Discover partitions. Work SHALL only run for a signed-in user.

#### Scenario: Backgrounded refresh updates the cache
- **WHEN** the worker runs while the app is backgrounded and the user is signed in
- **THEN** it refreshes the configured feed partitions via `:core:feed-cache` and then triggers a widget re-render

#### Scenario: Signed out
- **WHEN** the worker runs while the user is signed out
- **THEN** it does nothing (success no-op) and performs no fetch or cache write

### Requirement: Foreground guard

The worker SHALL NOT write the cache while the app is foregrounded, because Room invalidation is table-level and a write would invalidate an actively-scrolling app `PagingSource`. While foregrounded the app's own `RemoteMediator` is the cache writer.

#### Scenario: Foregrounded run does not write
- **WHEN** the worker runs while the app is in the foreground
- **THEN** it returns success without refreshing or writing the cache

### Requirement: Periodic and on-demand scheduling, battery-cooperative

The system SHALL register a unique **periodic** refresh at the platform-minimum interval (15 minutes) with a network-connectivity constraint, and SHALL expose a unique **on-demand** one-time refresh (for widget add / manual refresh) since periodic work cannot be forced to run on demand. It MUST NOT use expedited work, a foreground service, exact alarms, or wakelocks, and SHALL accept Doze / App-Standby deferral.

#### Scenario: Scheduled when signed in
- **WHEN** the user is signed in
- **THEN** a unique periodic work request is enqueued at the 15-minute platform-minimum interval requiring network connectivity

#### Scenario: On-demand refresh
- **WHEN** a widget is added or a manual refresh is requested
- **THEN** a unique one-time work request is enqueued (network-constrained) without disturbing the periodic schedule, and a duplicate in-flight refresh is suppressed

#### Scenario: Cancelled on sign-out
- **WHEN** the user signs out
- **THEN** the periodic refresh work is cancelled

### Requirement: Refresh triggers off-scroll eviction

After refreshing a feed partition, the worker SHALL run the count-cap trim (`:core:feed-cache` `trimToCap`) off the active-scroll path to bound cache growth.

#### Scenario: Trim after refresh
- **WHEN** the worker successfully refreshes a feed partition
- **THEN** it trims that partition to the retention cap (off-scroll), leaving the partition's cursor intact

### Requirement: Widget update is triggered through a Glance-free seam

The worker SHALL trigger the widget re-render through a `WidgetUpdater` seam (not a direct Glance call), so this capability carries no `androidx.glance` dependency. A no-op default SHALL be provided; the Glance-backed implementation is supplied by the widgets sub-project.

#### Scenario: Updater invoked after a successful refresh
- **WHEN** a background refresh completes successfully
- **THEN** the worker invokes `WidgetUpdater.updateFeedWidgets()`

#### Scenario: No-op updater keeps the module Glance-free
- **WHEN** no Glance-backed updater is bound (this module alone)
- **THEN** the worker still runs to completion using the no-op updater, and the module declares no Glance dependency

### Requirement: Per-feed refresh with retry only on total failure

The worker SHALL refresh each feed independently and request a WorkManager retry **only when every feed failed**. A partial success (at least one feed refreshed) SHALL return success, so a retry never re-fetches an already-refreshed feed; the failed feed is picked up by the next periodic run.

#### Scenario: All feeds fail → retry
- **WHEN** every feed refresh fails transiently
- **THEN** the worker returns a retry result so WorkManager re-runs it later

#### Scenario: Partial failure → success without redundant re-fetch
- **WHEN** at least one feed refreshes successfully and another fails
- **THEN** the worker returns success (the succeeded feed is not re-fetched) and the failed feed is refreshed on the next periodic run
