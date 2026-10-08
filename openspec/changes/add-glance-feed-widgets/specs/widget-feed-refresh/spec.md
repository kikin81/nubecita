## ADDED Requirements

### Requirement: Refresh triggers off-scroll image prefetch through a seam

After a feed partition refreshes successfully and is trimmed, the worker SHALL invoke a `WidgetImagePrefetcher` seam to decode post thumbnails off the active-scroll path. Prefetch SHALL run within per-feed isolation: a prefetch failure SHALL fail only that feed's images and SHALL NOT fail the refresh or affect worker retry outcome. `CancellationException` SHALL propagate. This module SHALL carry no `androidx.glance` dependency and SHALL provide a no-op default.

#### Scenario: Prefetch runs after a successful per-feed refresh
- **WHEN** a feed partition refreshes successfully and is trimmed
- **THEN** the worker invokes `WidgetImagePrefetcher.prefetch(feedKey)` for that feed off the active-scroll path

#### Scenario: A prefetch failure does not fail the refresh
- **WHEN** the image prefetch for one feed throws
- **THEN** the failure is logged, that feed's refresh outcome is unchanged, the other feeds are still processed, and the worker does not request a retry on account of the prefetch

#### Scenario: No-op prefetcher keeps the module Glance-free
- **WHEN** no Glance-backed prefetcher is bound (this module alone, or a bench build)
- **THEN** the worker runs to completion using the no-op prefetcher and the module declares no Glance dependency
