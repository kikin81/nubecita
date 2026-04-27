## Context

Bluesky's feed lexicon `app.bsky.embed.video#view` carries an HLS playlist URL plus a JPEG/WebP poster, an aspect ratio, an optional alt-text, and a duration **in milliseconds** (per the lexicon schema). The PDS / AppView serves the .m3u8 via the `https://video.bsky.app/...` CDN with adaptive bitrate variants (typically 360p / 480p / 720p). A typical Following timeline page contains 0–5 video posts.

`FeedViewPostMapper` MUST normalize the lexicon's millisecond field to **seconds** (`Int`) when constructing `EmbedUi.Video.durationSeconds`. Conversion: `(lexiconMs / 1000).coerceAtLeast(1)` — integer division rounding toward zero, with a 1-second floor to avoid showing `0:00` for very short clips. The `EmbedUi` boundary is the single place where the unit conversion happens; downstream rendering / coordinator logic operates exclusively in seconds.

Four pre-existing constraints from the project shape every decision below:

1. **120Hz scrolling is a hard requirement.** Anything that drops frames during a scroll gesture is non-negotiable broken — including memory pressure from N decoder instances, GPU overload from N video surfaces, or main-thread stalls from player init.
2. **MVI on `ViewModel` + `StateFlow`.** State changes flow through the VM; the VM is the single source of truth for "which post is currently playing." The coordinator reacts to state, never decides state autonomously.
3. **Hilt graph hosts singletons; screens host their own scoped infrastructure.** The player coordinator is screen-scoped, not app-scoped — releasing it when the user leaves the feed is the cleanest memory contract.
4. **No web views.** Mass-renderer dependencies are off-limits for embedded media; Media3 / ExoPlayer is the canonical Compose-friendly path.

## Goals / Non-Goals

**Goals:**
- Render `app.bsky.embed.video#view` as an interactive card: poster + duration chip + tap-to-play affordance.
- Single ExoPlayer instance materialized at any time, bound to the most-visible video card.
- Tap on a card → that card plays inline; scrolling the playing card off-screen pauses the binding (player can be reused for the next visible card the user taps).
- Lifecycle-clean: screen exit releases the ExoPlayer, no leaked surfaces.
- 120Hz scrolling target unchanged with N video cards on screen.

**Non-Goals:**
- Inline autoplay (mute on Wi-Fi). Deferred to v2 — Bluesky's web autoplays-muted, but Android battery + data policies vary by region/carrier and we don't yet have engagement metrics to justify the complexity.
- PostDetail screen video playback. Separate epic; likely shares the coordinator pattern.
- Live streaming (no AT Proto lexicon today).
- Picture-in-picture, Chromecast.
- Video posting / composer.
- recordWithMedia embeds where the media component is video — that's the recordWithMedia ticket's problem (`nubecita-umn`); render the inner video card via the same composable when it lands.

## Decisions

### Decision 1: Single shared ExoPlayer coordinated by FeedScreen, not N per-card players

**Choice:** `FeedVideoPlayerCoordinator` (one instance per `FeedScreen` lifecycle) owns one `ExoPlayer` instance. The screen observes `LazyListState.layoutInfo.visibleItemsInfo` via `snapshotFlow`, computes the topmost video card whose visible-fraction exceeds 0.6, and asks the coordinator to expose the player to THAT card via the shared `Player` reference the card passes to its `PlayerSurface` composable. Tap on a card flips `FeedState.currentlyPlayingPostId`; the coordinator's player attachment is driven by which card is rendering with `player = sharedPlayer` (vs `player = null`) — the swap is a `PlayerSurface` parameter change, NOT a view recreation. Scrolling the playing card off-screen → coordinator pauses (player retained, ready for re-bind on scroll-back).

**Alternatives considered:**
- *N players (one per visible card, capped at K).* Rejected — even K=2 doubles memory, decoder, GPU surface count, and battery. ABR streams + decoder warm-up amplify the per-instance cost. Bluesky's iOS / Android first-party clients use single-coordinator; matching that gives parity and avoids re-deriving the "why doesn't my video play here" failure modes.
- *No coordinator — the cards each construct their own ExoPlayer in `remember`.* Rejected — every scroll-into-view materializes a new decoder and the LazyColumn item recycle pool blows past the device's video-decoder limit (Android typically allows 2–4 concurrent hardware decoders).

### Decision 2: Autoplay-muted on scroll-into-view (matches Bluesky's reference behavior); audio focus NEVER claimed by autoplay

**Choice:** As each video card scrolls into the most-visible-video position (visible-fraction > 0.6), the shared `ExoPlayer` automatically attaches and plays the video at `volume = 0`. The card renders the poster as a placeholder until the player produces the first frame, then cross-fades to the `PlayerSurface`. A mute/unmute icon overlay is the only inline control. **Audio focus is NOT claimed by this autoplay flow** — the user can be listening to music in another app and the autoplay does not interrupt it. The user explicitly taps the unmute icon to claim audio focus and play with sound. Tap on the card body uses the existing `PostCallbacks.onTap` to navigate to the post-detail screen (separate epic), where the detail composable claims audio focus and plays unmuted.

This matches the official Bluesky client's behavior. The reference UX argument: "opening the app to read the feed while listening to music should NOT hijack my audio." Autoplay-muted gives the user the visual signal that there's a video without imposing on their audio environment; explicit unmute is the consent gesture for audio focus claim.

**Alternatives considered:**
- *Tap-to-play (poster + big play button until user taps).* Was the prior v1 plan; rejected after explicit user feedback that opening the app should not require a tap to surface video content. Tap-to-play also creates a UX disconnect with peer clients (Bluesky, Twitter, TikTok all autoplay-muted) — feels broken on first launch.
- *Always autoplay (with audio).* Disrupts users in libraries / quiet spaces; battery cost; no user agency. Worse than tap-to-play.
- *Autoplay-muted only on Wi-Fi.* Adds connectivity-class detection + per-Android-version autoplay-policy gating. Defer to v2 if mobile-data engagement metrics warrant; v1 ships always-autoplay-muted because Media3 + adaptive bitrate already keeps the cellular cost bounded (lowest variant for first 10s, see Decision 4).

### Decision 3: Coordinator binds via `media3-ui-compose`'s `PlayerSurface` (Compose-native), with `SURFACE_TYPE_TEXTURE_VIEW` for embedded feed cards

**Choice:** Use `androidx.media3:media3-ui-compose`'s `PlayerSurface` composable as the video render surface. The card's video region is `PlayerSurface(player = if (isCurrentlyPlayingCard) coordinator.player else null, modifier = Modifier.aspectRatio(...), surfaceType = SURFACE_TYPE_TEXTURE_VIEW)`. Re-bind is a parameter swap (`player = null` ↔ `player = sharedExoPlayer`); `PlayerSurface`'s internal `LaunchedEffect` handles the underlying `setVideoSurface(...)` / clear dance, with a deferred main-thread `clearVideoView` so a sibling card can grab the player without thrashing the surface.

`SURFACE_TYPE_TEXTURE_VIEW` chosen over the default `SURFACE_TYPE_SURFACE_VIEW` for embedded feed cards because `LazyColumn` actually disposes off-screen items (vs `RecyclerView`'s viewholder-pool detach), so a `SurfaceView` gets destroyed and recreated on scroll-back — visible flicker. `TextureView` composites smoothly through the Compose draw pipeline at the cost of slightly more battery / GPU. The detail screen (separate epic, single dedicated player) can switch back to `SURFACE_TYPE_SURFACE_VIEW` for the battery win.

The `PlayerSurface` API is `@UnstableApi` as of Media3 1.10.0 — requires a file-level `@OptIn(UnstableApi::class)` opt-in. The artifact ships on the stable Media3 train so the opt-in cost is the only price for cleaner Compose interop. We accept the API-shift risk between minor Media3 versions.

**Alternatives considered:**
- *`PlayerView` via `AndroidView` interop.* Was the canonical pattern before Media3 1.6.0 introduced `media3-ui-compose`. Same underlying SurfaceView so 120Hz unaffected, but: (a) drags in `media3-ui` (~150 KB) for chrome we'd disable via `useController = false` anyway, (b) `AndroidView` factory + lifecycle dance for what `PlayerSurface` does natively, (c) `@Preview` often crashes/no-ops with `AndroidView { PlayerView }` whereas `PlayerSurface` renders empty cleanly. Net: more code, more APK, less Compose-friendly tooling, identical runtime cost — so we move to `PlayerSurface`.
- *Hand-rolled `SurfaceView` / `TextureView` + manual `setVideoSurface(...)`.* What `PlayerSurface` already wraps for us; reinventing the surface lifecycle is a pure code-debt move with no upside.
- *`media3-ui-compose-material3`'s prebuilt `Player` composable.* Bundles standard playback controls + progress bar — opinionated chrome we don't need. v1 ships only the mute/unmute icon overlay (driven directly by the coordinator's `isUnmuted: StateFlow<Boolean>`); see Decision 7.

### Decision 4: HLS adaptive bitrate, but feed starts at the LOWEST variant for the first 10 seconds

**Choice:** Use `media3-exoplayer-hls`'s default ABR selector. The initial `DefaultTrackSelector` parameters set `setForceLowestBitrate(true)`. After **10 seconds of sustained playback** on a single video, the coordinator clears the force flag and the default ABR is allowed to upgrade based on observed throughput. The 10-second threshold (vs "after the first segment loads", which would be ~2 seconds) is chosen because most users scroll past a video before the first 10 s elapse — so the upgrade only fires for videos the user is actually engaging with. Detail screens (separate epic) can start at the user's connection-class default and skip the floor entirely.

**Alternatives considered:**
- *Clear the force flag after the first segment loads (~2s).* Trips ABR upgrade for videos the user scrolled past after a glance, wasting bandwidth.
- *Always lowest, never upgrade.* Wastes the bandwidth budget — most users will scroll past a video before the upgrade matters, but the few who watch a full video deserve the quality bump.
- *Connection-class-based default.* More user-friendly but adds complexity (network state observation, change-on-network-change behavior) we don't need for v1.

### Decision 5: Audio focus claimed ONLY on explicit user unmute; never on autoplay; released on mute, scroll-away, or focus loss

**Choice:** The coordinator's `ExoPlayer` operates in two distinct modes:

- **Autoplay-muted (default)**: `volume = 0`. NO audio focus claimed. Player plays the most-visible video card silently. Scrolling between video cards rebinds the player without touching audio focus state.
- **Unmuted (explicit user choice)**: user taps the unmute icon on the bound card. Coordinator: (a) claims `AUDIOFOCUS_GAIN_TRANSIENT` via Media3's `AudioAttributes` + `setHandleAudioFocus(true)`, (b) registers the BECOMING_NOISY receiver, (c) sets `volume = 1`, (d) updates internal `isUnmuted: StateFlow<Boolean>` to `true`. The bound card collects this flow and renders the mute icon variant.

Audio focus is released on:
- **User mute**: tap the mute icon. Coordinator releases focus + unregisters BECOMING_NOISY + sets `volume = 0` + `isUnmuted = false`.
- **Scroll-away from an unmuted card**: coordinator detects the unmuted post leaving the most-visible position; pauses player + releases focus + unregisters BECOMING_NOISY + auto-mutes (`volume = 0` + `isUnmuted = false`). The unmute does NOT carry over to the next visible video — user must explicitly unmute the new one. Rationale: unmute is a per-card user gesture, not a session-wide preference.
- **Focus loss while unmuted** (incoming call, music app gaining focus, headphones unplug): coordinator pauses player + sets `playbackHint = FocusLost` + leaves `isUnmuted` unchanged (the user's intent to hear audio is preserved). Card surfaces "tap to resume" overlay. User tap → coordinator reacquires focus + resumes player.

This model is the linchpin of the "don't hijack my music" UX requirement. Opening the app to read the feed silently never disturbs the user's audio environment. The unmute gesture is the explicit consent for audio focus, and it's per-card so the user is never surprised by audio.

**Alternatives considered:**
- *Claim focus on every autoplay bind (the previous spec).* Hijacks the user's music app every time a new video card scrolls into view. Loud, unwanted, doesn't match peer clients. Rejected after explicit user feedback.
- *Persist unmute across video cards (i.e., once user unmutes, every subsequent autoplay is unmuted).* Rejected — feels like a global setting opted in by accident; the user might unmute one specific video they care about, then scroll past 10 videos they don't, all blasting unwanted audio.
- *No focus management.* Plays over the user's music; unacceptable.
- *Permanent focus.* Spotify/Music keep getting interrupted; rude.

### Decision 6: Module split — defer to implementation

**Choice:** Implementation task `nubecita-sbc.1` decides whether to create `:core:media` (mirrors `:core:auth`'s pattern) or declare the Media3 deps inline in `:feature:feed:impl`. The proposal locks the dependencies + the contract; module placement is an implementation detail. If a second consumer (PostDetail epic, future media gallery, etc.) needs the same deps within 6 months, `:core:media` is correct; otherwise inline is leaner.

**Why this isn't a proposal-level decision:** the choice has no user-visible effect and trivially refactors later if we guess wrong.

### Decision 6a: PostCard exposes a `videoEmbedSlot` lambda + `PostCallbacks` callbacks; `:designsystem` does NOT depend on `:feature:feed:impl`

**Choice:** The video render composable lives in `:feature:feed:impl` (it's screen-coordinator-aware — binds the FeedScreen-scoped `FeedVideoPlayerCoordinator`'s shared `ExoPlayer`). Module dependency direction in this project is `:feature:feed:impl → :designsystem` and never the reverse, so PostCard's `EmbedSlot` cannot import the feature-impl video composable directly. Instead, PostCard gains a `videoEmbedSlot: @Composable (EmbedUi.Video) -> Unit = {}` parameter — when `EmbedSlot` dispatches on `EmbedUi.Video`, it invokes the slot. The default lambda is no-op (renders nothing); the feed feature supplies the real composable when constructing PostCard inside `FeedScreen`.

The same boundary applies to event dispatch. PostCard cannot import `FeedEvent` (lives in `:feature:feed:impl`). Existing pattern: PostCard has a `PostCallbacks` data class (in `:designsystem`) carrying `onTap`, `onAuthorTap`, `onLike`, `onRepost`, `onReply`, `onShare` lambdas. We extend it with `onVideoPlay: (PostUi) -> Unit = {}` and `onVideoPause: (PostUi) -> Unit = {}`. The video card's tap target invokes `callbacks.onVideoPlay(post)`; the feed feature wires that to dispatch `FeedEvent.OnVideoPlay(post)` in the VM.

This is the same inversion-of-control pattern PostCard already uses for every other interaction. PostCard stays the canonical post-render composable; extensibility is via slots + callbacks, not via downstream wrappers.

**Alternatives considered:**
- *Move the embed dispatch out of `:designsystem` into a feed-feature wrapper.* Would mean PostCard handles every embed type EXCEPT video, with the feed feature wrapping PostCard to intercept video posts before delegation. Asymmetric: image embeds dispatch in PostCard, video doesn't — future contributors will forget which is which.
- *Add a `:core:media` module that hosts both the deps AND a base video composable, with `:designsystem` depending on it.* Compose-friendly but bloats the module graph and hardcodes a media dependency into the design system; `:designsystem` should not require a video player to render a button.
- *Generic `embedSlot: @Composable (EmbedUi) -> Unit` covering all embed types.* Loses the existing internal dispatch (the design system's `PostCardImageEmbed` and `PostCardUnsupportedEmbed` are `:designsystem`-internal); host would need to know about every variant. The video slot is the only one needing inversion (feature-coordinator-aware); keep the rest in `:designsystem`.

### Decision 7: Single inline control (mute / unmute icon overlay); no play/pause inline; no progress bar

**Choice:** With autoplay-muted (Decision 2) the only inline control v1 needs is the **mute / unmute icon overlay**. Tap on the card body navigates to the post-detail screen (existing `PostCallbacks.onTap`); play/pause and seek are handled by the detail screen, not the feed. The mute icon's visual state is driven by `coordinator.isUnmuted: StateFlow<Boolean>` — when `false`, render the muted-speaker icon (Material `VolumeOff`); when `true`, render the unmuted-speaker icon (Material `VolumeUp`). Tap calls `coordinator.toggleMute()` directly (PostCardVideoEmbed lives in `:feature:feed:impl`, same module as the coordinator — no callback hop needed).

We do NOT use Media3's `rememberMuteButtonState` here because it observes `Player.volume` state changes; the coordinator's `isUnmuted` is the authoritative source for our UI (it captures both volume + audio-focus state in one signal). The Media3 state holder would race against the coordinator's mute() / unmute() actions in a way that produced visible flicker.

**Alternatives considered:**
- *`media3-ui-compose-material3`'s prebuilt `Player` composable.* Bundles play/pause + progress + chrome — heavier than the single mute icon needs.
- *Multiple inline controls (play/pause + mute + fullscreen).* Cluttered; Bluesky's reference design has just the mute icon. Detail screen has the full set.
- *No inline controls at all.* User has no way to enable audio without leaving the feed; defeats the purpose of "tap to unmute" being the consent gesture.
- *Use Media3's `rememberMuteButtonState` for the icon state.* Races with the coordinator's authoritative state (which couples volume + focus + receiver registration). Use coordinator's `isUnmuted` StateFlow instead.

### Decision 8: All video playback state is coordinator-internal; FeedState / FeedEvent are unaware of audio playback

**Choice:** With autoplay-muted (Decision 2) and unmute-only-on-explicit-tap audio focus (Decision 5), the previous "currently-playing-post-id + OnVideoPlay/OnVideoPause" MVI surface collapses entirely. The most-visible video card autoplays automatically based on scroll position; there's no user "play" gesture in the feed; there's no concept of a screen-wide "currently selected video." All video state is **coordinator-internal**, exposed to the bound card via `StateFlow`s the card collects directly:

- `coordinator.boundPostId: StateFlow<String?>` — which post the coordinator currently has the player attached to (driven by scroll position, not user state).
- `coordinator.isUnmuted: StateFlow<Boolean>` — whether the player is currently unmuted (set true by user tap on unmute icon; cleared on mute, scroll-away, or screen exit).
- `coordinator.playbackHint: StateFlow<PlaybackHint>` — values: `None`, `FocusLost`. Set to `FocusLost` only when audio focus is lost while unmuted (incoming call, BECOMING_NOISY); cleared when user taps "tap to resume" overlay.

The coordinator owns these flows; the VM never touches them. The bound `PostCardVideoEmbed` (in `:feature:feed:impl`) collects them directly via `collectAsStateWithLifecycle()`. The coordinator's mute toggle, unmute, and resume actions are invoked directly by `PostCardVideoEmbed` (which lives in the same module as the coordinator — no `:designsystem` boundary to cross). No `PostCallbacks` lambdas needed for video-specific actions; the existing `PostCallbacks.onTap` covers the card-body tap → navigate-to-detail flow.

This is the cleanest MVI surface for the autoplay model: the user's only interaction with audio is the unmute toggle, and that's local to the bound card.

**Why this differs from the previous design:** the prior spec had `currentlyPlayingPostId: String?` in `FeedState` to model "user tapped to play this post." With autoplay, there's no tap-to-play, so no state to track. The user's only gestures in the feed are (a) tap card body → navigate (covered by existing `onTap`) and (b) tap mute icon → toggle audio focus (coordinator-internal). Pure simplification; the prior design's `OnVideoPlay` / `OnVideoPause` / process-death-exclusion concerns all disappear.

**Alternatives considered:**
- *Keep `currentlyPlayingPostId` in `FeedState` for symmetry with prior design.* Carries no information that scroll position doesn't already convey. Dead state. Drop.
- *Put `isUnmuted` in `FeedState`.* User-initiated, so a defensible candidate for VM state. But: (a) it's per-bound-video so it auto-clears on scroll-away, making it more transient than typical VM state; (b) it doesn't survive process death by design (no auto-resume); (c) routing it through MVI adds a recomposition per mute toggle for what's a card-local visual change. Coordinator StateFlow with direct collection is the cleaner fit.
- *Separate `VideoPlaybackViewModel` scoped to FeedScreen.* Overkill for what's now zero VM state.

## Risks / Trade-offs

- **First-tap latency.** ExoPlayer cold-start (decoder init + HLS playlist fetch + first segment download) is typically 200–800 ms on a warm app. The play button → playback start gap will be visible. Mitigation: pre-warm the decoder on the most-visible video card (initialize the player with `setMediaItem` but don't `play()`) — trade-off is that pre-warm consumes some memory/CPU even when the user never taps.
- **HLS variant selection on first segment.** ABR's first segment is a guess; if the network is slow, we may pick a variant that stutters. Lowest-first mitigates the stutter risk at the cost of "video looks low-res for the first 5–10 seconds."
- **Coordinator binding race during fast-scroll.** `snapshotFlow { visibleItemsInfo }` can fire many times per second during a fling. Mitigation: gate the bind flow on `LazyListState.isScrollInProgress` rather than a time-based `debounce`. The combined flow emits `null` (no bind) while scrolling and emits the resting most-visible post id the instant scroll settles — robust to fast flings, no latency penalty on settle, no risk of dropping the final correct emission inside a debounce window. Implementation in Task 4.5.
- **DPoP / auth.** HLS playlist + segment URLs are CDN-served and (per current bsky behavior) do NOT require auth. If that changes, the coordinator's HTTP data-source factory needs to grow an `AtOAuth`-aware interceptor. Out of scope today; flagged for the implementation task to verify.
- **Audio-focus loss while playing.** Incoming calls / Bluetooth disconnects → focus loss → pause. UX-correct but the play affordance must show that the pause was external (e.g. a small "tap to resume" hint), not a freeze. Detail covered in `nubecita-sbc.4` task spec.
- **Memory leak via coordinator outliving FeedScreen.** Mitigated by a `DisposableEffect(Unit) { onDispose { coordinator.release() } }` in the screen's outermost composable. Tests should assert: navigating away from the feed → coordinator's player is released → `dumpsys media.player` reports zero active players.

## Migration Plan

No data migration. New capability with backward-compatible API additions. Cards rendering `app.bsky.embed.video#view` switch from "Unsupported" chip to the new video card on the same merge.

## Open Questions

- **Pre-warm strategy** — kick off in `nubecita-sbc.4` after the basic coordinator is working; benchmark first-tap latency with and without pre-warm before locking the policy.
- **Looping** — short videos (< 15s) loop on Bluesky web. Worth replicating? Track separately if v1 ships without and the engagement signal warrants.
- **Captions / accessibility** — `app.bsky.embed.video` carries an alt-text; the bound card should expose it via `Modifier.semantics { contentDescription = altText }` on the `PlayerSurface`'s wrapper for TalkBack. Lexicon does not currently carry caption tracks (CC), so HLS subtitle rendering is out of scope for v1. Track in a `nubecita-zk2`-style follow-up if Bluesky adds caption support.
