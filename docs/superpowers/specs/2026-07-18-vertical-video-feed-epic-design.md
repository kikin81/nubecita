# Vertical video feed ("Videos") — epic design

**Date:** 2026-07-18
**Status:** Approved (brainstorm) — ready for beads epic + task decomposition
**Type:** Epic (multi-slice)

## Why

Growth. Feedback from other Bluesky-client devs and a verified competitive scan both point to a small set of cheap, screenshot-demoable differentiators; a **vertical, TikTok/reels-style short-video feed** ranked as the highest demo-to-effort win *for Nubecita specifically*, because Nubecita has already built the hard parts (HLS decode, PiP, inline player). "TikTok for Bluesky" is an active, mainstream lane, and a full-bleed vertical video is the single best hero screenshot for the Play listing.

Ships **free** (D1) — it is an install-driver, not a Pro perk.

## Current state (what we build on)

- **`:core:video/SharedVideoPlayer`** — a *single*, process-scoped `ExoPlayer`, rebound as the user scrolls (one active playback at a time). `PlaybackMode` (FeedPreview = no audio focus / Fullscreen = audio focus), bitrate-floor logic, a Mutex enforcing the single-player invariant. Also powers feed-preview autoplay, the fullscreen route, and PiP.
- **`:core:video/PipController`** — the single PiP gate.
- **`:feature:videoplayer`** — a full-screen *single-video* route (Screen/VM/Content/Chrome).
- **`:core:feed-cache/FeedNetworkSource`** — already fetches feeds via `getFeed(feedUri, cursor)` for Discover/custom feeds.
- **`:core:feed-mapping`** — already maps `app.bsky.embed.video` → UI models; Nubecita plays HLS today.

The single-player model matches the Reddit playbook's "one active playback by priority," but it **cannot prewarm the next video** — which is the entire feel of a vertical feed.

## Data source feasibility (S0 spike — RESOLVED)

- **Trending/vertical-video source (MVP):** `getFeed` on Bluesky's official **"Video"** feed
  `at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.generator/thevids` (rkey `thevids`,
  same DID as Discover's `whats-hot`). Standard pinnable custom feed → cursor pagination,
  reusing the existing `FeedNetworkSource` path. **New feed URI, not new networking.**
- **Profile videos source (post-MVP):** `getAuthorFeed(actor, filter = posts_with_video, cursor)`.
  `posts_with_video` is a **first-class filter value** in the SDK's `getAuthorFeed` lexicon —
  the "playlist" is protocol-paginated for us, no custom construction.
- **Video model:** `app.bsky.embed.video` (main/view/caption) is in the SDK; `VideoView` carries
  the HLS playlist + aspectRatio. Already mapped + playable.
- **Caveat:** the SDK vendors **no `unspecced` endpoints**, so Bluesky's exact "Trending Videos"
  *carousel injection* in Discover (likely an unspecced trending endpoint) is not callable today.
  We don't need it — our carousel is sourced from `getFeed(thevids)`. If Bluesky's precise trending
  signals are wanted later, add those lexicons to the `atproto-kotlin` fork.
- `thevids` returns **mixed aspect ratios** (not all portrait) — the vertical player must fit/letterbox
  landscape clips gracefully (UX detail, not a blocker).

## Decisions

- **D1 — Free.** Ships to everyone; it is a growth/install lever. (Save/hide-seen may become Pro perks far later.)
- **D2 — Trending-first MVP.** Ship the core vertical player + a Trending entry first; profile-videos entry lands later in the same epic.
- **D3 — One reusable player over a paginated `VideoFeedSource`.** Every entry point is a different *source* feeding the same vertical player: Trending (`getFeed(thevids)`), Profile (`getAuthorFeed(posts_with_video)`), and future sources. Build the player + source abstraction once; each entry point is a small adapter.
- **D4 — A separate, pooled player for this surface; do NOT retrofit `SharedVideoPlayer`.** Add a dedicated `VerticalVideoPlaylistPlayer` — a **pool of 2** (active + next-prewarmed) scoped to the vertical feed only. Prewarm the next page's player via `prepare()` before it enters the viewport → swipe = instant first frame (Reddit measured +19% starts <250ms from prewarming next). Retrofitting a pool into the single-player would destabilize feed previews / fullscreen / PiP (all working). Pool-of-2 captures ~all the smoothness and extends to 3 later if metrics want it.
- **D5 — Decoder budget via handoff.** Entering the vertical feed pauses/releases `SharedVideoPlayer` (frees its decoder); the pool uses ≤2. On exit, the pool releases and the shared player resumes. Respects device decoder limits (low-end phones cap at 1–2 hardware decoders).
- **D6 — Share plumbing, not the instance.** Extract `SimpleCache`, track-selector/codec-selector, and `LoadControl` config into `:core:video` building blocks consumed by *both* `SharedVideoPlayer` and the pool, so the Reddit optimizations (MP4-under-45s, drip-feed LoadControl, decoder-exclusion, lazy-prefetch-next) live in one place.
- **D7 — Entry surface = a "Trending Videos" carousel in Discover.** Mirror the official app: a dismissible horizontal strip of portrait thumbnails injected into the Discover feed → tap a thumbnail → full-screen vertical player opened at that index over the trending source. Familiar, and the best hero screenshot.
- **D8 — Adopt the Reddit ExoPlayer performance playbook** (see references): player pool + prewarm-next; lazy-prefetch only the next video (`DownloadManager`, later `PreloadManager`); short-video `LoadControl` tuning (bufferForPlayback≈1000ms, min/max≈20000ms); MP4 vs HLS handling; decoder-exclusion retry; a Choreographer-driven seekbar; single active playback; analytics events (first-frame, rebuffer, exit-before-start).

## Architecture

```
:core:video
  ├─ (extract) VideoPlaybackInfra   — SimpleCache, track/codec selectors, LoadControl, player factory  [D6]
  ├─ SharedVideoPlayer              — unchanged consumer of the extracted infra
  └─ VerticalVideoPlaylistPlayer    — pool of 2 (active + prewarm-next), prepare-before-viewport,
                                      single-active, loop, mute; enter/exit handoff with SharedVideoPlayer  [D4,D5]

:core:video-feed (new, thin)         — VideoFeedSource abstraction  [D3]
  ├─ TrendingVideoSource            — getFeed(thevids) → paginated video posts
  └─ AuthorVideoSource              — getAuthorFeed(actor, posts_with_video) → paginated  [post-MVP]
     (both reuse :core:feed-mapping for embed→UI)

:feature:videos:{api,impl} (new)     — NavKey + full-screen vertical feed screen + VM
  └─ VerticalVideoFeedScreen        — snap VerticalPager, full-bleed video, overlay chrome
                                      (author/caption + like/repost/reply/share via PostInteractionHandler),
                                      Choreographer-driven progress, mute toggle

:feature:feed:impl                   — inject the "Trending Videos" carousel entry into Discover  [D7]
```

Module names are proposals to finalize in S1 / Slice 1.

## Epic slices

**Spike (design-only):**
- **S1 — Playback architecture.** Design `VerticalVideoPlaylistPlayer` (pool of 2, prewarm-next, single-active), the `:core:video` infra extraction (D6), and the enter/exit decoder handoff with `SharedVideoPlayer` (D5). Output: a short design note + interfaces. No user-facing UI.

**MVP build (Trending):**
1. **`VideoFeedSource` + `TrendingVideoSource`** — paginated video posts from `getFeed(thevids)`, reusing feed-mapping. Unit-tested with a fake network.
2. **`VerticalVideoPlaylistPlayer`** — the pool (active + prewarm-next), loop, mute, prepare-before-viewport; infra extraction from S1. Unit + integration tested; bench smoke.
3. **`:feature:videos` vertical feed screen** — snap `VerticalPager`, full-bleed video (fit/letterbox mixed aspect), overlay chrome reusing `PostInteractionHandler`, Choreographer progress, mute. Screenshot tests + VM unit tests.
4. **Trending Videos carousel in Discover** — dismissible portrait-thumbnail strip injected into the Discover feed; tap → open the vertical feed at index. Screenshot tests.
5. **Perf + analytics pass** — lazy-prefetch next, short-video LoadControl tuning, MP4/HLS handling, decoder-exclusion retry, analytics events (first-frame / rebuffer / exit-before-start), macrobench a scroll-through.

**Post-MVP (same epic):**
6. **Profile videos entry** — `AuthorVideoSource` (`getAuthorFeed(posts_with_video)`); tap an author's video → vertical player over their videos, opened at index. The TikTok-on-a-profile case.
7. **(future)** other sources (a feed's videos), save / hide-seen (candidate Pro perks), adaptive pool size by device performance class.

## Risks / trade-offs

- **[Decoder exhaustion on low-end / with PiP]** → single-active playback + pool-of-2 + enter/exit handoff (D5); decoder-exclusion retry (D8). Verify on a low-RAM emulator.
- **[Retrofitting the single-player would destabilize working surfaces]** → mitigated by the *separate* pool (D4); shared infra keeps optimizations unified (D6).
- **[`thevids` mixed aspect ratios]** → fit/letterbox in the player; don't assume portrait.
- **[Unspecced trending carousel not callable]** → source our own carousel from `getFeed(thevids)` (D6 data note); fork-add lexicons only if precise trending signals are needed later.
- **[Prewarm vs prefetch races]** → gate `prepare()` on prefetch completion (Reddit `PriorityTaskManager`) to avoid error 1004 / stalls.
- **[Battery]** — vertical video autoplay is power-hungry; respect the "battery is top priority" rule: pause off-screen immediately, single active playback, no background playback, honor data-saver.

## Open questions

- Exact module boundary for `VideoFeedSource` (`:core:video-feed` vs. reuse `:core:posts`) — finalize in Slice 1.
- Carousel placement/anchor within the Discover feed (inject position, dismissal persistence) — finalize in Slice 4.
- Whether to also expose the vertical feed as a pinnable feed / nav entry (beyond the carousel) — defer.

## References

- Reddit ExoPlayer engineering series (perf playbook): "Improving video playback with ExoPlayer", "Taking ExoPlayer Further", "How we rewrote Reddit's video player on Android" — player pooling, prewarming, lazy prefetch, LoadControl tuning, decoder-exclusion, Choreographer seekbar, single-active playback, analytics.
- Competitive research: `docs/superpowers/specs/` deep-research findings (vertical video ranked top differentiator; feasibility verified).
