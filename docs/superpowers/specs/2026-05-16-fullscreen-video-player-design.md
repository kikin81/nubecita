# Fullscreen video player

**bd epic:** `nubecita-zak` (rescoped + bumped to P1 alongside this design)
**Status:** Designed 2026-05-16. Children land per the decomposition below.

## Problem

Bluesky posts can carry video embeds (`app.bsky.embed.video`). Today, the feed renders the most-visible card with an autoplay-muted preview via `FeedVideoPlayerCoordinator`, and tapping the card routes to PostDetail — there is no dedicated fullscreen video destination. Profile and PostDetail show only the poster image. The user has no way to enter an immersive viewing surface with audio, scrubbing, or larger framing.

## Goals (V1)

- A reusable fullscreen video player route reachable from feed, profile, and post-detail surfaces.
- Smooth transition from the feed's autoplay-muted preview into fullscreen: the *same* ExoPlayer instance keeps playing, audio focus and unmute happen on entry, no perceptible interruption.
- Standard controls: play/pause, mute toggle, seek bar with elapsed/total time, tap-to-toggle chrome with idle auto-hide, back to dismiss.
- Static-poster + tap-to-fullscreen support in profile and post-detail (a new `:designsystem:VideoPosterEmbed` composable).

## Non-goals (deferred)

- Picture-in-Picture (separate platform integration; out of scope).
- Auto-play in feed/profile *beyond* what the existing most-visible-coordinator already does.
- Landscape orientation inside the player (player route inherits the app's portrait lock for V1; landscape is a follow-up that requires orientation-aware Activity wiring + surface-resize without playback restart).
- Closed captions — Bluesky's lexicon does not expose caption tracks today; only `EmbedUi.Video.altText` (accessibility description) is available. Wire `altText` as the surface's `contentDescription` and leave captions for a future upstream lexicon issue.
- Position persistence across process death (V1 restarts at 0:00 on restore; promote to a saved-state field if it bites in practice).
- Video posting / recording / share-out / save-to-device.

## Architecture

The keystone decision is a **process-scoped singleton** owning *the* ExoPlayer instance. Multiple Compose surfaces (feed cards, fullscreen route) attach/detach their `PlayerSurface` to it; the player itself never gets recreated across navigation. Audio focus and unmute state belong to the holder, not the surface — so flipping to fullscreen mutates the player's mode without interrupting playback.

The existing `FeedVideoPlayerCoordinator` invariants — autoplay-muted-most-visible, audio focus discipline, HLS bitrate floor, single-player invariant — lift to the holder unchanged.

### Module layout

```
:feature:videoplayer:api    NavKey only (VideoPlayerRoute(postUri))
:feature:videoplayer:impl   Screen + VM + chrome composables
:core:video                 SharedVideoPlayer singleton + PlaybackMode
:designsystem               new VideoPosterEmbed composable
```

Refactor footprint:

- `:feature:feed:impl/video/FeedVideoPlayerCoordinator` collapses to a thin wrapper that drives `SharedVideoPlayer` based on feed visibility. Its existing ExoPlayer ownership lifts to the holder. The wrapper retains the most-visible-card selection logic and the visibility → bind / unbind decisions.

### `SharedVideoPlayer` contract

```kotlin
@Singleton
class SharedVideoPlayer {
    fun bind(playlistUrl: String, posterUrl: String?)
    fun attachSurface(target: Any)              // Media3 ui-compose surface seam
    fun detachSurface()

    val mode: StateFlow<PlaybackMode>
    fun setMode(m: PlaybackMode)                // drives audio focus + volume

    val positionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val isPlaying: StateFlow<Boolean>
    val boundPlaylistUrl: StateFlow<String?>    // for VM's URI-match check

    fun play()
    fun pause()
    fun seekTo(ms: Long)
    fun toggleMute()

    fun release()                               // on logout / app shutdown
}

enum class PlaybackMode { FeedPreview, Fullscreen }
```

`FeedPreview`: volume=0, no audio focus claim, autoplay-when-visible.
`Fullscreen`: claims audio focus, unmute, play unconditionally.

`bind(playlistUrl, posterUrl)` is **idempotent** when the URL matches the currently bound one — that's the load-bearing property for the instance-transfer optimization. Mutations on the holder are serialized via a `Mutex` (same shape as the existing coordinator) so audio-focus callbacks and visibility-driven binds don't interleave.

## Components

| Component | Module | Role |
|---|---|---|
| `VideoPlayerRoute(postUri: String)` | `:feature:videoplayer:api` | `@Serializable data class … : NavKey`. Single primitive, mirrors `PostDetailRoute`. |
| `VideoPlayerScreen` | `:feature:videoplayer:impl` | Hoists `VideoPlayerViewModel`. On enter: `setMode(Fullscreen)`. On dispose: `setMode(FeedPreview)`. Renders `PlayerSurface` + chrome overlay. |
| `VideoPlayerViewModel` | impl | Translates `SharedVideoPlayer` state flows into a flat `VideoPlayerState(isPlaying, isMuted, positionMs, durationMs, chromeVisible, loadStatus)`. Owns the auto-hide-chrome timer + the "rebind if bound URI differs" logic. |
| `VideoPosterEmbed` | `:designsystem:component` | Stateless: `posterUrl` image + play overlay + tap callback. Used by profile + postdetail. |
| `PostCardVideoEmbed` (existing) | `:feature:feed:impl` | Gains an `onTap(postUri)` callback. Autoplay behavior unchanged — keeps driving `SharedVideoPlayer` through the refactored coordinator. |

`VideoPlayerState` shape follows the project's MVI conventions (flat fields for independent flags, sealed `VideoPlayerLoadStatus` for mutually-exclusive lifecycle — `Idle`, `Resolving`, `Ready`, `Error`).

## Data flow

**Feed scrolling brings a card into view.** `FeedVideoPlayerCoordinator` calls `SharedVideoPlayer.bind(playlistUrl, posterUrl)`, `setMode(FeedPreview)`, `play()`. The card's `PostCardVideoEmbed` attaches its `PlayerSurface` to the holder.

**User taps a playing feed video.**
`PostCardVideoEmbed.onTap` → `FeedEvent.VideoTapped(postUri)` →
`FeedViewModel` emits `FeedEffect.NavigateToVideoPlayer(postUri)` →
`FeedScreen.LaunchedEffect` → `LocalMainShellNavState.current.add(VideoPlayerRoute(postUri))`.
MainShell's inner `NavDisplay` mounts `VideoPlayerScreen`. **The ExoPlayer instance and its bound URL never move.** Feed's `PlayerSurface` detaches mid-transition; fullscreen's attaches as it composes.

**Fullscreen entry resolution.** `VideoPlayerScreen.LaunchedEffect(Unit)` asks the holder: is `boundPlaylistUrl` the one for `postUri`?
- **Yes (came from feed)**: `setMode(Fullscreen)` — instance-transfer payoff. Audio focus claimed, unmute, play continues.
- **No (came from profile / postdetail)**: VM resolves the playlist URL — via the post's cached `EmbedUi.Video` if available in the current state, else a `getPosts(uris)` lookup — calls `bind(...)` then `setMode(Fullscreen)`.

**Back-nav dismisses fullscreen.** `DisposableEffect`'s `onDispose` flips `setMode(FeedPreview)`. The feed coordinator's visibility logic resumes driving the holder — if the card is still on screen, the same player keeps playing muted right where it left off.

**Profile / post-detail tap.** Identical entry path: `VideoPosterEmbed.onTap(postUri)` → screen-level `NavigateToVideoPlayer` effect → `add(VideoPlayerRoute(postUri))`. No instance transfer (nothing was playing), but the same VM code path resolves + binds.

## Error and edge cases

- **Playlist resolution fails** (profile/postdetail entry, post not cached, `getPosts` errors): VM emits `ShowError(error)` effect; screen renders a centered error layout with a Retry button. Holder stays unbound.
- **Audio focus loss** (incoming call, other media app): holder pauses + drops focus (Media3 default). Player stays bound. User taps play to resume; the focus claim re-fires.
- **Process death while fullscreen**: `VideoPlayerRoute(postUri)` survives via Nav3 serialization. On restore, the holder is fresh — VM resolves + binds, plays from 0:00. Position persistence is a follow-up if it bites.
- **App background mid-fullscreen**: `Lifecycle.onStop` pauses + drops focus, *without* releasing the bound URL. Returning to foreground keeps the player bound, ready to play on user input.
- **Logout**: `SharedVideoPlayer.release()` wired to the auth-state-cleared broadcaster so a stale ExoPlayer doesn't survive across users.
- **HLS playlist 404 / unsupported codec**: Media3 emits `ExoPlaybackException`. The holder surfaces it through a `playbackError: StateFlow<Throwable?>`; the VM maps to `VideoPlayerLoadStatus.Error(VideoPlayerError)` (same shape as Search errors).

## Testing

**Unit tests:**

- `SharedVideoPlayerTest` (in `:core:video`): state-transition matrix across `PlaybackMode` flips; audio-focus claim/release asserted via a fake `AudioManager`; mutex serialization tested with concurrent `bind` + `setMode`; idempotent rebind when URL matches.
- `VideoPlayerViewModelTest`: state-flow → flat `VideoPlayerState` mapping; auto-hide-chrome timer (driven via `TestDispatcher.scheduler`); URI-mismatch rebind path; error mapping.
- `FeedVideoPlayerCoordinatorTest` (existing): port to the new `SharedVideoPlayer`-delegating shape. Existing audio-focus + bitrate-floor + most-visible-selection tests must stay green.

**Compose previews + screenshot tests:**

- Every `VideoPlayerScreen` state (resolving, playing, paused, chrome-hidden, error) × light/dark.
- `VideoPosterEmbed` with/without poster URL, light/dark.

**Instrumentation:**

- Feed → fullscreen → back returns to the feed with playback resumed in muted preview mode. The `PlayerSurface` attach/detach across the nav transition is the brittle bit — one targeted instrumented test pins it.
- Profile poster tap → fullscreen route mounts and reaches `Ready` state.

## Child decomposition

Each child is a separate bd issue; ordering + blockers below. Priorities P1 for the foundation, P2 for wire-up and tests.

| # | Title | Priority | Blocks |
|---|---|---|---|
| 1 | `:core:video` skeleton + `SharedVideoPlayer` holder + state-machine unit tests | P1 | — |
| 2 | Refactor `FeedVideoPlayerCoordinator` to delegate to `SharedVideoPlayer` (no user-visible change) | P1 | #1 |
| 3 | `:feature:videoplayer:api` module + `VideoPlayerRoute` NavKey | P1 | #1 |
| 4 | `:feature:videoplayer:impl` — fullscreen route, screen, VM, chrome (play/pause/seek/mute/auto-hide) | P1 | #2, #3 |
| 5 | Wire feed's `PostCardVideoEmbed` tap → `VideoPlayerRoute` (completes the feed → fullscreen → feed loop) | P2 | #4 |
| 6 | `:designsystem:VideoPosterEmbed` + wire profile + postdetail taps | P2 | #4 |
| 7 | Screenshot tests for `VideoPlayerScreen` states + `VideoPosterEmbed` | P2 | #4, #6 |
| 8 | androidTest: feed → fullscreen → back transition (PlayerSurface attach/detach) | P2 | #5 |

## Open follow-ups (separate epics or bds)

- **Position persistence across process death.** Persist `(boundPlaylistUrl, positionMs)` to `SavedStateHandle` so restoring fullscreen continues where it left off.
- **Landscape orientation inside the player.** Wire `requestedOrientation` config-change handling on `MainActivity`, ensure surface resize doesn't restart playback, decide back-stack semantics on rotation.
- **Picture-in-Picture.** Platform integration — uses the same `SharedVideoPlayer` holder, adds an Activity-level PIP mode bridge.
- **Closed captions.** Currently blocked on Bluesky's lexicon — file an upstream issue against `kikin81/atproto-kotlin` and the public `bluesky-social/atproto` repos.
- **Mixed-media gallery for posts that grow images + video.** Today's Bluesky lexicon makes a post either-images-OR-video, but if that changes, the singleton holder + mediaviewer module would share a parent surface.

## References

- `:feature:feed:impl/video/FeedVideoPlayerCoordinator.kt` — existing autoplay coordinator; its invariants lift to `SharedVideoPlayer`.
- `:feature:feed:impl/ui/PostCardVideoEmbed.kt` — gains `onTap(postUri)`.
- `:feature:mediaviewer` — image fullscreen viewer; **not extended** for this epic per the module-choice decision. Lives alongside `:feature:videoplayer` as a sibling.
- `EmbedUi.Video` (`:data:models/EmbedUi.kt`) — `posterUrl`, `playlistUrl`, `aspectRatio`, `durationSeconds`, `altText`.
- `LocalMainShellNavState` (`:core:common:navigation`) — the tab-internal nav seam; same hand-off pattern used by `nubecita-vrba.9` for post / actor tap-through.
