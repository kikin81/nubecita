## Why

Bluesky encodes feed videos as HLS streams hosted by the user's PDS or the AppView CDN (`app.bsky.embed.video#view`). Today the `FeedViewPostMapper` drops these into the `Unsupported` bucket — users see a generic placeholder card on every video post. Video is also a higher-order UI concern than the other deferred embeds (link card, quoted post, recordWithMedia): it adds a runtime dependency (Media3 / ExoPlayer), a non-trivial performance design decision (single-player coordinator vs naive per-card players), a thumbnail-vs-autoplay UX choice, and likely audio-focus + Wi-Fi-only autoplay policies. None of these fit cleanly inside an "add embed variant" PostCard task — they're a feature in their own right.

This change captures the design decisions BEFORE any production code lands, so the implementation tasks (`nubecita-sbc.1` add-deps, `nubecita-sbc.3` thumbnail render, `nubecita-sbc.4` tap-to-play coordinator) execute against a locked plan rather than re-deriving the player-coordination strategy mid-PR.

## What Changes

- **NEW capability** `feature-feed-video` — covers the data-path mapping (`FeedViewPostMapper` dispatch for `app.bsky.embed.video#view` → `EmbedUi.Video`), the rendered card surface (poster + duration + tap-to-play affordance), the player coordinator that owns at most one materialized `ExoPlayer` instance at a time and binds it to the most-visible video card, and the lifecycle / inset / audio-focus contract those moving parts must satisfy.
- **MODIFIED capability** `data-models` — adds `EmbedUi.Video` variant to the sealed `EmbedUi` interface (nullable poster URL, HLS URL, aspect ratio, **nullable** duration, alt text). The `app.bsky.embed.video#view` lexicon does NOT currently expose a duration field — `durationSeconds` is reserved as nullable for a future phase that sources duration from a lexicon evolution or from HLS manifest parsing; v1 mapper passes `null`. `FeedViewPostMapper.toEmbedUi` adds the dispatch arm.
- **MODIFIED capability** `design-system` — `PostCard` gains a `videoEmbedSlot: @Composable (EmbedUi.Video) -> Unit = {}` parameter so the screen-coordinator-aware video composable (which lives in `:feature:feed:impl`) can be supplied by the host without `:designsystem` taking a forbidden dependency on the feature module. `PostCallbacks` (in `:designsystem`) gains `onVideoPlay: (PostUi) -> Unit = {}` and `onVideoPause: (PostUi) -> Unit = {}` callbacks that the feed feature wires to dispatch `FeedEvent`. Same inversion-of-control pattern PostCard already uses for `onTap` / `onLike` / `onRepost` — `:designsystem` never imports feature-layer event types.
- **MODIFIED capability** `feature-feed` — `FeedScreen` host gains a `FeedVideoPlayerCoordinator` scoped to the screen's composition lifetime; the coordinator observes `LazyListState.layoutInfo.visibleItemsInfo` to determine which video card is "most-visible" and binds the player there. NO `FeedState` or `FeedEvent` additions for video — autoplay-muted is the default model (see below) and the coordinator owns all transient playback state internally; the VM is unaware of audio playback details. The only feed-feature change is the FeedScreen-level coordinator hosting + slot wiring.
- **NEW Gradle module** `:core:media` (or, alternative, dependencies declared inline in `:feature:feed:impl`) — Media3 / ExoPlayer + HLS extension. Module split is a sub-decision tracked in `design.md`.
- **`:designsystem` exposes a slot API for video; no feature-module dependency added.** PostCard gains a `videoEmbedSlot: @Composable (EmbedUi.Video) -> Unit = {}` parameter — when `EmbedSlot` dispatches on `EmbedUi.Video`, it invokes the slot (default no-op renders nothing). The feed feature supplies a composable that renders `media3-ui-compose`'s `PlayerSurface` bound to the FeedScreen-scoped `FeedVideoPlayerCoordinator`. `:designsystem`'s build dependencies stay as-is (only `:core:common` and `:data:models`); the video composable lives in `:feature:feed:impl` because it's screen-coordinator-aware (binds the coordinator's shared `ExoPlayer`) and that violates `:designsystem`'s "stateless / loaded-data-only" PostCard contract. Same inversion-of-control pattern PostCard already uses for its `PostCallbacks` data class — host supplies behavior, designsystem renders.

Default playback mode for v1: **autoplay-muted on scroll-into-view** (matches the official Bluesky client's behavior). Audio focus is **NEVER** claimed by the autoplay flow — opening the app to read the feed while listening to music does NOT interrupt the user's audio. The shared `ExoPlayer` plays at `volume = 0` for the most-visible video card; the user explicitly taps the unmute icon to claim audio focus and play with sound. Tap on the card body navigates to the post-detail screen (separate epic) which handles its own audio focus claim. See Decision 2 + Decision 5 in `design.md` for the full rationale.

## Capabilities

### New Capabilities

- `feature-feed-video`: Video playback inside the feed — HLS-backed `app.bsky.embed.video#view` rendering, single-player coordinator that owns one ExoPlayer instance at a time and binds it to the most-visible video card, tap-to-play UX with poster + duration affordance, lifecycle + audio-focus + memory contracts.

### Modified Capabilities

- `data-models`: adds `EmbedUi.Video` sealed variant carrying poster URL, HLS URL, aspect ratio, duration, alt text.
- `design-system`: `PostCard` gains a `videoEmbedSlot: @Composable (EmbedUi.Video) -> Unit = {}` parameter so feature-module composables can supply the video render without `:designsystem` taking a dependency on `:feature:feed:impl`. NO new `PostCallbacks` lambdas — `PostCardVideoEmbed` lives in `:feature:feed:impl` and calls the coordinator directly without an MVI round-trip; existing `PostCallbacks.onTap` covers card-body tap → detail navigation.
- `feature-feed`: `FeedScreen` hosts a `FeedVideoPlayerCoordinator` scoped to its composition lifetime and supplies the `videoEmbedSlot` to PostCard. NO `FeedState` or `FeedEvent` additions — all video playback state (binding, mute, audio focus, playback hint) is coordinator-internal.

## Impact

- **Code** (estimated, finalized at implementation time):
  - New: `core/media/` module (deps wrapper) — see `design.md` for module-split decision.
  - New: `feature/feed/impl/.../FeedVideoPlayerCoordinator.kt` (composition-scoped to FeedScreen).
  - New: `feature/feed/impl/.../ui/PostCardVideoEmbed.kt` (thumbnail + play overlay; renders `media3-ui-compose`'s `PlayerSurface` bound to the coordinator's shared `ExoPlayer` when active, with our own Compose controls overlay driven by Media3 state holders).
  - Modified: `data/models/.../EmbedUi.kt` (add `Video` variant).
  - Modified: `designsystem/.../PostCard.kt` (add `videoEmbedSlot` parameter; `EmbedSlot`'s `EmbedUi.Video` arm invokes the slot).
  - Modified: `designsystem/.../PostCallbacks.kt` (add `onVideoPlay` + `onVideoPause` lambdas).
  - Modified: `feature/feed/impl/.../data/FeedViewPostMapper.kt` (dispatch arm).
  - Modified: `feature/feed/impl/.../FeedScreen.kt` (coordinator wiring via `LaunchedEffect(listState)`; supplies `videoEmbedSlot` to PostCard).
  - **NOT modified**: `feature/feed/impl/.../FeedContract.kt` — no FeedState / FeedEvent changes for video.
  - Modified: `gradle/libs.versions.toml` (Media3 catalog entries).

- **Dependencies** (Media3 1.10.0 stable; pin in `gradle/libs.versions.toml`):
  - `androidx.media3:media3-exoplayer` (~250 KB) — core player engine.
  - `androidx.media3:media3-exoplayer-hls` (~80 KB; depends on the above) — HLS playlist + segment parser, ABR selector.
  - `androidx.media3:media3-ui-compose` (~70 KB) — `PlayerSurface` composable + state holders (`rememberPlayPauseButtonState`, `rememberMuteButtonState`) for our custom controls. Replaces the older `androidx.media3:media3-ui` (`PlayerView` + XML chrome).
  - Total APK size impact: ~400 KB (release minified). Significant but justified — video is a first-class Bluesky surface, and using the Compose-native `PlayerSurface` + state-holder pattern saves ~50 KB vs the legacy `media3-ui` PlayerView path while shedding the AndroidView interop layer.
  - All public symbols in `media3-ui-compose` carry `@UnstableApi` as of 1.10.0 — requires file-level `@OptIn(UnstableApi::class)`. Acceptable: ships on the stable Media3 train; opt-in is the only price.

- **Per-thread concerns** (locked in `design.md`):
  - At most ONE `ExoPlayer` instance materialized at any time.
  - Player lifecycle scoped to FeedScreen (`DisposableEffect` releases on screen exit).
  - Autoplay-muted default — videos play silently as they scroll into view; user explicitly unmutes to claim audio focus.
  - Audio focus is NEVER claimed by autoplay; only on explicit user unmute.
  - HLS adaptive bitrate via `media3-exoplayer-hls`; feed start at lowest variant.

- **Out of scope** (each tracked separately):
  - Video posting / composer (separate epic).
  - Live streaming (no AT Proto lexicon).
  - Picture-in-picture, casting.
  - PostDetail screen video playback (separate epic — likely shares the coordinator pattern).

- **Tests**: standard MVI matrix tests for the new state + events; screenshot tests for the thumbnail card variants (light/dark, with/without poster, short/long duration); manual smoke for the coordinator's pause-on-scroll-away + memory-budget behavior on a 120Hz device.
