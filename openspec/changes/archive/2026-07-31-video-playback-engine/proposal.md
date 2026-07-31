## Why

Nubecita's `:core:video` today owns `SharedVideoPlayer` — a **single** process-scoped ExoPlayer, rebound as the user scrolls (one active playback at a time). It backs feed-preview autoplay, the fullscreen route, and PiP, and it deliberately enforces a single-player invariant. That model matches the Reddit playbook's "one active playback," but it **cannot prewarm the next video** — the defining feel of a vertical/TikTok-style feed (epic nubecita-zdv8).

Rather than destabilize the working single player, we introduce a **reusable playback engine**: a separate pooled player for playlist surfaces plus shared playback infrastructure both players consume. Extracting this as its own `:core:video` capability (not a feature detail) means it outlives the "Videos" feature — any future video surface builds on it, and the perf optimizations live in one place.

This change is the **design + spec** deliverable of epic slice S1 (bd `nubecita-zdv8.1`). The pooled-player implementation is Slice 2 (bd `nubecita-zdv8.3`); the vertical-feed screen is Slice 3.

## What Changes

- **New `VerticalVideoPlaylistPlayer`** — a pool of **2** ExoPlayers (active + next-prewarmed) scoped to a vertical playlist surface, distinct from `SharedVideoPlayer`. **Prewarms** the next item via `prepare()` before it enters the viewport so a swipe yields an instant first frame. Exactly **one active** (audible/playing) playback at a time.
- **New reusable playback infra** in `:core:video` — off-main `SimpleCache`, a cache-backed `MediaSource.Factory`, track/codec selectors, `LoadControl` config, and the ExoPlayer factory — built for the pool and reusable by any `:core:video` player. `SharedVideoPlayer` (a bare ExoPlayer today) is **left untouched** here; adopting the shared cache is a deferred, separately-measured migration.
- **Decoder budget via lifecycle handoff** — entering a playlist surface pauses/releases `SharedVideoPlayer`; the pool holds ≤2 decoders and **releases on `Lifecycle.ON_STOP`** (not just route-pop), re-preparing on `ON_START`.
- **Playback hardening (Reddit playbook)** — short-video `LoadControl` tuning, lazy-prefetch of the next item only, decoder-exclusion retry via a custom `MediaCodecSelector`, `MediaSource`es keyed by player id, software-decoder fallback.
- **Playback analytics** — first-frame time, rebuffer, started/stopped, error via Media3 `AnalyticsListener`/`PlaybackStatsListener`.
- Battery: pause off-screen immediately, single active playback, no background playback, honor data-saver.

Not a breaking change to app behavior — additive; `SharedVideoPlayer`'s contract is preserved.

## Capabilities

### New Capabilities
- `video-playback-engine`: The `:core:video` playback engine — a single-active-playback pooled player with next-item prewarming for playlist surfaces, shared playback infrastructure (cache/selectors/LoadControl/factory), a decoder-budget lifecycle handoff, playback hardening, and playback analytics.

### Modified Capabilities
<!-- None — no existing openspec capability covers :core:video playback. SharedVideoPlayer's behavior is preserved, not respecified. -->

## Impact

- `:core:video` — new `VerticalVideoPlaylistPlayer` + pool management; extracted infra (`SimpleCache`/selectors/`LoadControl`/factory) refactored out of `SharedVideoPlayer` and shared. `PipController` interplay with the handoff.
- Consumers (later slices): the vertical-feed screen (Slice 3) drives the pool; feed previews / fullscreen route / PiP are untouched (`SharedVideoPlayer` source unchanged).
- External: Media3/ExoPlayer only; no new libraries. HLS delivery unchanged.
- Analytics: new playback events (Firebase Analytics, already integrated).

## Non-goals

- The `VideoFeedSource` / data layer — already shipped (Slice 1, `:core:video-feed`).
- The vertical-feed screen UI (Slice 3) and the Trending carousel (Slice 4).
- Simultaneous multi-video playback; changing HLS delivery.
- A full ExoKit-style global-playback-state rewrite — single-active + a 2-player pool is sufficient.
