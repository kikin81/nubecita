## Context

`:core:video` owns `SharedVideoPlayer` — a single, process-scoped ExoPlayer rebound as the user scrolls, with a Mutex-enforced single-player invariant, `PlaybackMode` (FeedPreview / Fullscreen), and a bitrate floor. It backs feed-preview autoplay, the fullscreen route, and PiP (`PipController`). Bluesky serves HLS (`.m3u8`); `EmbedUi.Video` carries `playlistUrl` + `aspectRatio`; the app already plays HLS.

The vertical video feed (epic `nubecita-zdv8`) needs **prewarming of the next item** for an instant swipe — which the single-player invariant cannot do. This design defines a reusable playback engine that adds a pooled player without destabilizing the working single player.

The performance requirements below are motivated by the Reddit ExoPlayer series (local digest: `docs/references/reddit-exoplayer-playbook.md`; source URLs in that folder's README). Production results cited as motivation: prewarm-next → **+19%** starts <250ms; short-video `LoadControl` → **−17.7%** starts >2s and **−4.8%** rebuffer; player-pool pre-create → **−2%** frozen frames globally; decoder-exclusion retry → 4001/4003 errors **100k→30k/day**; Choreographer seekbar → **−7.3%** slow frames.

## Goals / Non-Goals

**Goals:**
- An instant-swipe vertical playlist player via a 2-player pool with next-item prewarm.
- Extract shared playback infra so both players share one set of optimizations.
- Stay within the device hardware-decoder budget via a lifecycle handoff.
- Preserve `SharedVideoPlayer`'s behavior exactly.

**Non-Goals:**
- The data layer (`:core:video-feed`, shipped) and the vertical-feed UI (Slice 3).
- Simultaneous multi-video playback; changing HLS delivery.
- A full ExoKit-style global-playback-state rewrite — single-active + a 2-player pool is enough.

## Decisions

### D1 — Separate `VerticalVideoPlaylistPlayer`, not a retrofit of `SharedVideoPlayer`

Retrofitting a pool into `SharedVideoPlayer` would risk feed previews, the fullscreen route, and PiP (all working). Instead, a distinct pooled player scoped to a playlist surface. Proposed shape:

```kotlin
// :core:video
interface VerticalVideoPlaylistPlayer {
    /** ExoPlayer bound to the currently active item, for the Compose PlayerSurface. */
    val activePlayer: StateFlow<ExoPlayer?>
    val playbackState: StateFlow<PlaylistPlaybackState>   // buffering / playing / error per active item

    /** Attach a playlist of HLS sources; index 0 becomes active, index 1 pre-warms. */
    fun bind(items: List<VideoSource>, startIndex: Int)
    /** Advance/settle to [index]; promotes the pre-warmed player and re-warms index+1. */
    fun onActiveIndexChanged(index: Int)
    fun setMuted(muted: Boolean)
    /** Lifecycle-driven release/re-prepare (see D3). */
    fun onStop()
    fun onStart()
    fun release()
}
```

*Alternative rejected:* one player rebound per swipe — no prewarm, black/loading frame each swipe.

### D2 — New reusable infra for the pool; `SharedVideoPlayer` untouched

**Discovered during implementation:** `SharedVideoPlayer` is a **bare** `ExoPlayer.Builder(context).setTrackSelector(ts).build()` with `MediaItem.fromUri` + `prepare()` — it has **no** `SimpleCache`, `LoadControl`, `MediaCodecSelector`, or `MediaSource.Factory`. So there is **nothing to extract**; the shared infra is **net-new**, built for the pool. Forcing it onto `SharedVideoPlayer` would *change* its behavior (add caching, alter buffering/decoder selection) — out of scope and a regression risk. So this change leaves `SharedVideoPlayer`'s source **untouched** (zero regression surface) and adds new, reusable `:core:video` building blocks:
- `VideoCache` — a process-singleton `SimpleCache` **built off-main via the injected `IoDispatcher`** (its constructor touches disk), dedicated folder, explicit LRU evictor, custom cache-key factory (Bluesky HLS URLs may carry query params). Exposed through a shared cache-backed `DataSource.Factory` → `MediaSource.Factory`.
- `VideoTrackSelectorFactory` / a custom `MediaCodecSelector` (decoder-exclusion — D4).
- `shortVideoLoadControl()` — `bufferForPlaybackMs`≈1000 and a low min/max buffer (≈20000). Reddit's drip-feed uses `min`=`max` (best rebuffering on unstable networks); a small `min`<`max` **hysteresis gap** (e.g. 15000/20000) trades a little rebuffering for fewer loader on/off cycles (lower CPU). Final values are validated in the Slice 5 perf pass — battery-vs-rebuffer, given battery is a top priority.
- `ExoPlayerFactory` — one place that wires cache + selectors + LoadControl + software-decoder fallback.

The blocks are designed for reuse so `SharedVideoPlayer` can adopt them later. *Alternative rejected:* duplicate the infra inside the pool with no reusable seam — optimizations would drift.

### D2a — Two players coexist; not a replacement

`VerticalVideoPlaylistPlayer` does **not** replace `SharedVideoPlayer`; they are two playback models for two surface shapes:
- `SharedVideoPlayer` — a single player rebound across a **heterogeneous feed** (video interspersed with text; "next" is unpredictable, prewarm N/A) plus the single-video fullscreen route and PiP.
- `VerticalVideoPlaylistPlayer` — a **homogeneous playlist** where "next" is a known video → a prewarmed pool.

The convergence point is the **infra**, not the player. **Future (deferred, tracked):** migrate `SharedVideoPlayer` to consume the shared `VideoCache` so feed previews and the pool share cache hits — a separate, independently-measured change (it alters the feed player's caching/buffering behavior). A full single-engine (ExoKit-style) unification remains a non-goal.

### D3 — Decoder budget via lifecycle handoff, released on `ON_STOP`

Hardware decoders are finite (low-end ~1–2). On entering a playlist surface, pause/release `SharedVideoPlayer`; the pool holds ≤2. Release the pool on `Lifecycle.ON_STOP` (backgrounding), **not** only on route-pop — navigating *forward* leaves the playlist in the back stack, and its 2 held decoders + the next screen's `SharedVideoPlayer` = 3 → exhaustion on 2-decoder devices. Re-prepare active(+next) on `ON_START`. PiP remains governed by `PipController`.

### D4 — Playback hardening (Reddit playbook)

- **Prewarm** = `prepare()` the next player before viewport entry; gate on prefetch completion (`PriorityTaskManager`) to avoid a prewarm-vs-prefetch race.
- **Lazy-prefetch the next item only** (DownloadManager sharing the shared cache) — aggressive whole-batch prefetch raised parallel-request latency in Reddit's data.
- **Decoder-exclusion retry:** a custom `MediaCodecSelector` that excludes a decoder after a 4001/4003 failure and retries once (keep ≥1 decoder).
- **Fresh MediaSource per playback** — create each `MediaSource` from the shared `MediaSource.Factory` (over the shared cache-backed `DataSource.Factory`); never cache or reuse a stateful `MediaSource` across players (the root cause of Media3 1004 `ERROR_CODE_FAILED_RUNTIME_CHECK`, and a leak risk). Reuse is byte-level, in the shared cache.
- **Software-decoder fallback** enabled.

### D5 — Analytics + seekbar

Playback events (first-frame time, rebuffer, started/stopped, error+code) via Media3 `PlaybackStatsListener`. The seekbar/progress (owned by the Slice 3 screen, noted here for the contract) is Choreographer-driven off `getCurrentPosition()` (already an estimated position), not screen-level state.

## Risks / Trade-offs

- **[Touching `SharedVideoPlayer` regresses feed/fullscreen/PiP]** → eliminated by construction: this change does not modify `SharedVideoPlayer` at all (the new infra is pool-only). Its existing tests still run as a guard.
- **[Decoder exhaustion on low-end / with PiP]** → single-active + pool-of-2 + `ON_STOP` handoff (D3) + decoder-exclusion retry (D4); verify on a low-RAM / 1-decoder emulator.
- **[Prewarm vs prefetch race → 1004]** → gate `prepare()` on prefetch completion; use a fresh MediaSource per playback from the shared factory (never reuse across players).
- **[Pool complexity]** → keep pool size fixed at 2 for MVP; adaptive sizing by device performance class deferred.
- **[Battery]** → no background playback, single active, pause off-screen, honor data-saver.

## Migration Plan

1. Extract shared infra (D2) with `SharedVideoPlayer` unchanged behaviorally; land + verify against its tests first.
2. Add `VerticalVideoPlaylistPlayer` (D1) + hardening (D4) — this is bd `nubecita-zdv8.3` (Slice 2).
3. Slice 3 wires the pool to the vertical-feed screen; the lifecycle handoff (D3) is exercised end-to-end there.
4. **Rollback:** the engine is additive; if the pool misbehaves, the vertical-feed screen can fall back to `SharedVideoPlayer` (single-player, no prewarm) without touching other surfaces.

## Open Questions

- Exact split between this capability and the Slice 3 screen for the Choreographer seekbar (engine exposes position; screen renders) — finalize in Slice 3.
- Whether prefetch uses `DownloadManager` (MP4-friendly) or `PreloadManager` (partial/adaptive) for HLS — spike during Slice 2 implementation.
- Pool pre-creation on app start (via `androidx.startup`, Reddit Milestone 2) vs lazy — measure in the Slice 5 perf pass before committing.

## Test strategy

- **Unit:** pool state machine (active/prewarm promotion on index change; ≤1 active; ≤2 players), lifecycle release/re-prepare (`onStop`/`onStart`), decoder-exclusion selector logic, `shortVideoLoadControl()` values. Drive with a fake/relaxed ExoPlayer seam (mock `ExoPlayer`), same JVM-test discipline as the rest of `:core:video`.
- **Concurrency:** the Mutex/thread-confinement of pool mutations mirrors `SharedVideoPlayer`'s existing concurrency tests.
- **Regression:** `SharedVideoPlayer` is not modified; its existing tests still pass (a guard, not a gate, since its source is untouched).
- **Device check (manual / instrumented):** a low-decoder device/emulator confirms the `ON_STOP` handoff prevents exhaustion when navigating forward (playlist → profile with a feed-preview video).
