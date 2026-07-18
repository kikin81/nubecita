## ADDED Requirements

### Requirement: Pooled playlist player with next-item prewarming

The engine SHALL provide a `VerticalVideoPlaylistPlayer` scoped to a vertical playlist surface, backed by a pool of at most **2** ExoPlayer instances (the active player plus one pre-warmed for the next item). It SHALL `prepare()` the next item's player before that item enters the viewport, and it MUST NOT retrofit or replace `SharedVideoPlayer`.

#### Scenario: Next item is pre-warmed before it is shown

- **WHEN** the active item at index N is playing
- **THEN** the engine SHALL have prepared the item at index N+1 on the second pooled player before the user swipes to it

#### Scenario: Swipe promotes the pre-warmed player

- **WHEN** the user advances from item N to item N+1
- **THEN** the pre-warmed player for N+1 becomes active and begins playback from its first frame without a fresh `prepare()`
- **AND** the player vacated by item N is recycled to pre-warm item N+2

### Requirement: Exactly one active playback

The engine SHALL keep at most one **active** (audible, playing) playback at any time; the pre-warmed player SHALL be prepared but not started or audible.

#### Scenario: Only the visible item plays

- **WHEN** two players are pooled (active + pre-warmed)
- **THEN** only the player for the currently visible item is playing and audible; the other is prepared and paused/muted

#### Scenario: Off-screen playback pauses immediately

- **WHEN** the active item scrolls out of the visible/playable zone
- **THEN** its playback SHALL pause immediately

### Requirement: Shared playback infrastructure

The engine SHALL extract the `SimpleCache`, track/codec selectors, `LoadControl` configuration, and ExoPlayer factory into shared `:core:video` building blocks consumed by **both** `SharedVideoPlayer` and `VerticalVideoPlaylistPlayer`. The `SimpleCache` MUST be constructed off the main thread. The extraction MUST NOT change `SharedVideoPlayer`'s observable behavior.

#### Scenario: Cache constructed off-main

- **WHEN** the shared `SimpleCache` is created
- **THEN** it SHALL be constructed on a background thread (its constructor touches disk and would risk an ANR on the main thread)

#### Scenario: SharedVideoPlayer behavior preserved

- **WHEN** the shared infra replaces `SharedVideoPlayer`'s previously-inline cache/selectors/LoadControl/factory
- **THEN** `SharedVideoPlayer`'s existing behavior (single-player invariant, playback modes, bitrate floor) SHALL remain unchanged, verified by its existing tests plus regression coverage

### Requirement: Decoder budget via lifecycle handoff

Entering a vertical playlist surface SHALL pause/release `SharedVideoPlayer` so its decoder is freed, and the pool SHALL hold at most 2 decoders. The pool SHALL release its players on `Lifecycle.ON_STOP` (backgrounding), not only when the route is popped, and SHALL re-prepare the active (and next) item on `Lifecycle.ON_START`.

#### Scenario: Navigating forward frees the pool's decoders

- **WHEN** the user navigates forward from the playlist surface to another screen (the playlist stays in the back stack)
- **THEN** the pool SHALL release its players on `ON_STOP` so the destination screen's `SharedVideoPlayer` does not push the device past its hardware-decoder limit

#### Scenario: Returning re-prepares playback

- **WHEN** the playlist surface returns to the foreground (`ON_START`)
- **THEN** the engine SHALL re-prepare the active item (and pre-warm the next) and resume the single active playback

#### Scenario: Handoff on entry

- **WHEN** a vertical playlist surface becomes active
- **THEN** `SharedVideoPlayer` SHALL be paused/released before the pool prepares its first item

### Requirement: Playback hardening

The engine SHALL apply short-video-oriented `LoadControl` tuning, prefetch only the next item, recover from decoder failures by excluding the failed decoder and retrying, key cached `MediaSource`es by player id, and enable software-decoder fallback.

#### Scenario: Short-video buffering

- **WHEN** the engine configures `LoadControl`
- **THEN** it SHALL use short-video values (e.g. `bufferForPlaybackMs` ≈ 1000, `min`/`max` buffer ≈ 20000, drip-feed) rather than the long-video defaults

#### Scenario: Decoder failure is excluded and retried

- **WHEN** a decoder init/decode failure occurs for an item
- **THEN** the engine SHALL exclude that decoder from selection and retry the playback once, keeping at least one decoder available

#### Scenario: MediaSource not shared across players

- **WHEN** a `MediaSource` is cached for reuse
- **THEN** it SHALL be keyed by player id so it is only reused by the player instance it was attached to (avoiding Media3 `PlaybackException` `ERROR_CODE_FAILED_RUNTIME_CHECK` / code 1004)

### Requirement: Playback analytics

The engine SHALL emit playback analytics for time-to-first-frame, rebuffering, playback started/stopped, and playback error, sourced from Media3 `AnalyticsListener` / `PlaybackStatsListener`.

#### Scenario: First-frame and error are reported

- **WHEN** an item renders its first frame, or a playback error occurs
- **THEN** the engine SHALL emit the corresponding analytics event (time-to-first-frame; error with its Media3 code)

### Requirement: Battery discipline

The engine SHALL never play video in the background, SHALL keep a single active playback, SHALL pause off-screen playback immediately, and SHALL honor the system data-saver setting.

#### Scenario: No background playback

- **WHEN** the app is backgrounded
- **THEN** no engine playback SHALL continue (excepting an explicit PiP session, which remains governed by `PipController`)
