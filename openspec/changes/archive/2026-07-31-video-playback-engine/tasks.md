## 1. Interfaces + infra boundary (S1 design deliverable)

- [ ] 1.1 Finalize the `VerticalVideoPlaylistPlayer` interface (active-player + playback-state flows, `bind` / `onActiveIndexChanged` / `setMuted` / `onStop` / `onStart` / `release`) and the `VideoSource` input type it accepts.
- [ ] 1.2 Define the shared-infra surface to extract from `SharedVideoPlayer`: `VideoCache` (off-main `SimpleCache`, dedicated folder, LRU evictor, custom cache-key factory), `ExoPlayerFactory`, `shortVideoLoadControl()`, and the custom `MediaCodecSelector` — as `:core:video` building blocks consumed by both players.
- [ ] 1.3 Audit `SharedVideoPlayer` for every inline cache/selector/LoadControl/factory usage and write down the behavior-preserving extraction plan + the regression assertions that pin its current behavior (single-player invariant, PlaybackMode, bitrate floor).
- [ ] 1.4 Define the playback analytics event set (first-frame time, rebuffer, started/stopped, error+Media3 code) and the `PlaybackStatsListener` wiring point.

## 2. Design spikes (resolve before Slice 2 implementation)

- [ ] 2.1 Prefetch approach spike: `DownloadManager` vs `PreloadManager` for HLS next-item prefetch, and the `PriorityTaskManager` gate that serializes prefetch → `prepare()` (prewarm) to avoid the 1004 race.
- [ ] 2.2 Decoder-budget spike on a low-decoder device/emulator: confirm the entry handoff (pause/release `SharedVideoPlayer`) + `ON_STOP` pool release prevents exhaustion when navigating forward (playlist → a screen with a feed-preview video).
- [ ] 2.3 Decide pool pre-creation (androidx.startup, Reddit Milestone 2) vs lazy — defer the measurement to the Slice 5 perf pass; record the decision hook here.

## 3. Handoff to implementation (bd nubecita-zdv8.3 / Slice 2)

- [ ] 3.1 Confirm the spec's requirements + scenarios are implementable against the finalized interfaces; open any follow-up bd tasks discovered.
- [ ] 3.2 Cross-link this change to the epic: bd `nubecita-zdv8.1` (this design) and bd `nubecita-zdv8.3` (the `VerticalVideoPlaylistPlayer` implementation + infra extraction, with `SharedVideoPlayer` regression coverage as an acceptance gate).
