## 1. Pre-flight

- [x] 1.1 Branch off main per bd-workflow once `nubecita-sbc.1` is ready (this proposal lands first; no implementation tasks claim until then).
- [x] 1.2 Confirm Bluesky's `app.bsky.embed.video#view` lexicon is exposed in the current `atproto-kotlin` version. If a deserializer is missing, open an upstream issue in `kikin81/atproto-kotlin` (concrete shape: `gh issue create --repo kikin81/atproto-kotlin --title "Missing app.bsky.embed.video#view deserializer" --body ...`) describing the missing types + linking back to this task's bd id; track the upstream issue URL in this task's bd notes.
- [ ] 1.3 Sample-test: identify a public `at://` post with a video embed; confirm the HLS playlist + segments are CDN-served without auth.

## 2. Implementation phase A — `nubecita-sbc.1` (deps + smoke test)

This phase ships in its own bd issue / branch; deps add must merge before phases B + C.

- [x] 2.1 Add Media3 catalog entries to `gradle/libs.versions.toml`: `media3 = "1.10.0"` (or current stable at implementation time), `media3-exoplayer`, `media3-exoplayer-hls`, `media3-ui-compose` (NOT the legacy `media3-ui` artifact — we're using `PlayerSurface`, not `PlayerView`).
- [x] 2.2 Decide `:core:media` vs inline (per Decision 6 in design.md). Document the choice in the issue's notes.
- [x] 2.3 Throwaway smoke composable in `:designsystem` previews or `:app` debug — construct an `ExoPlayer` (with `volume = 0`), play a sample bsky HLS URL, render via `PlayerSurface(player = exoPlayer, surfaceType = SURFACE_TYPE_TEXTURE_VIEW)` (file-level `@OptIn(UnstableApi::class)` required). Confirm: HLS variant negotiation works; `PlayerSurface` renders cleanly under `@Preview` (empty placeholder, not a crash); audio is silent (no `requestAudioFocus` calls observed in logs); no Tink + DataStore-DB conflicts; ProGuard rules don't strip Media3 (add consumer-rules if needed).
- [x] 2.4 `./gradlew :app:assembleDebug :app:bundleRelease` — measure APK size delta. Document in the issue.

## 3. Implementation phase B — `nubecita-sbc.3` (data path + thumbnail-only render)

This phase adds the data path + idle (no-coordinator) card render so the feed shows clickable video posters even before the autoplay coordinator lands. With autoplay deferred to phase C, video posts in this phase render as static posters that navigate to detail on tap.

- [x] 3.1 Add `EmbedUi.Video` variant to `:data:models/EmbedUi.kt` per the `data-models` spec delta. `posterUrl: String?`, `playlistUrl: String`, `aspectRatio: Float`, `durationSeconds: Int?`, `altText: String?`. **Duration is `null` for v1** — the lexicon does not expose a duration field (verified phase A); reserved for future sourcing.
- [x] 3.2 Extend `feature/feed/impl/.../data/FeedViewPostMapper.kt` to dispatch `app.bsky.embed.video#view` → `EmbedUi.Video`. Mapper passes `durationSeconds = null`. Aspect ratio falls back to 16:9 (`1.777f`) when the lexicon's optional field is absent. Update existing `video embed maps to EmbedUi_Unsupported` test to assert `EmbedUi.Video` instead. Add fixture-JSON unit tests for: well-formed video, malformed video (missing playlist) → falls through to `Unsupported`, missing thumbnail → `EmbedUi.Video(posterUrl = null, ...)`.
- [x] 3.3 Add `feature/feed/impl/.../ui/PostCardVideoEmbed.kt` — phase-B variant: poster + duration chip only, no PlayerSurface, no mute icon. Outer container applies `Modifier.fillMaxWidth().aspectRatio(video.aspectRatio)` BEFORE `NubecitaAsyncImage` loads (locks LazyColumn measurement). Card-body tap uses the existing `PostCallbacks.onTap` to navigate to detail. Phase C extends this to add the PlayerSurface + mute icon.
- [x] 3.4 Extend PostCard with the slot API per design-system spec delta:
  - Add `videoEmbedSlot: @Composable (EmbedUi.Video) -> Unit = {}` parameter to `PostCard` in `:designsystem`.
  - Update `PostCard.EmbedSlot`'s `when` arm: `is EmbedUi.Video → videoEmbedSlot(embed)` (default no-op).
  - In `:feature:feed:impl`'s `FeedScreen` wiring, supply `videoEmbedSlot = { video -> PostCardVideoEmbed(video, post = post) }`.
  - NO new `PostCallbacks` lambdas — autoplay phase C drives mute via direct coordinator call from PostCardVideoEmbed; card-body tap uses existing `onTap`.
  - `:designsystem` MUST NOT take a dependency on `:feature:feed:impl` and MUST NOT import any `FeedEvent` symbol — verify via `:designsystem:build.gradle.kts` inspection.
- [x] 3.5 Strings: `R.string.postcard_video_duration_format` for `m:ss` / `h:mm:ss`. (No play-overlay a11y label needed in v1 — autoplay model has no centered play affordance.) — implemented as a private `formatDuration(Int): String` helper inside `PostCardVideoEmbed.kt` rather than a string resource: the format is digit-only and locale-insensitive (colons + zero-padded values render identically in every locale), so a `string-resource` indirection costs without buying anything. Revisit if a non-ASCII locale's digit shaping ever proves otherwise.
- [x] 3.6 Screenshot tests for the phase-B video card variants: with poster, without poster (gradient fallback), short duration (`0:32`), long duration (`1:23:45`), light + dark.
- [x] 3.7 `./gradlew :designsystem:updateDebugScreenshotTest :feature:feed:impl:updateDebugScreenshotTest`, audit baselines, commit.

## 4. Implementation phase C — `nubecita-sbc.4` (autoplay-muted coordinator + scroll-driven binding)

This phase wires the actual ExoPlayer with autoplay-muted semantics + the inline mute toggle. Depends on phases A + B.

- [ ] 4.1 NO `FeedState` / `FeedEvent` changes for video. All video state is coordinator-internal: binding driven by scroll position; mute, audio focus, playback hint exposed as coordinator StateFlows. The only feed-feature wiring is `FeedScreen`'s coordinator hosting + `videoEmbedSlot` supply.
- [ ] 4.2 Implement `FeedVideoPlayerCoordinator` in `:feature:feed:impl` (or `:core:media` if 2.2 chose that). Owns one `ExoPlayer` configured for autoplay-muted (`volume = 0` initial, `audioAttributes` for `USAGE_MEDIA` + `CONTENT_TYPE_MOVIE`). Exposes:
  - `val player: ExoPlayer` — the shared instance, passed by the bound card to its `PlayerSurface` (cards NOT bound pass `player = null`).
  - `boundPostId: StateFlow<String?>` — which post the coordinator currently has the player attached to (driven by scroll position).
  - `isUnmuted: StateFlow<Boolean>` — `false` by default; flips on `toggleMute()`; resets to `false` on scroll-away from unmuted card, on focus loss, and on `release()`.
  - `playbackHint: StateFlow<PlaybackHint>` (values: `None`, `FocusLost`) — set to `FocusLost` only when audio focus is lost while unmuted; cleared by `resume()`.
  - `bindMostVisibleVideo(postId: String?)` — coordinator-internal entry called by FeedScreen's `LaunchedEffect` (gated on `isScrollInProgress`). Sets the media item + plays muted; or releases binding + auto-mutes if `postId` is `null` or differs from currently bound id (auto-mute releases focus + unregisters BECOMING_NOISY if `isUnmuted` was `true`).
  - `toggleMute()` — flips `isUnmuted`. On unmute: `AudioManager.requestAudioFocus(...)` for `AUDIOFOCUS_GAIN_TRANSIENT`, register BECOMING_NOISY receiver, set `volume = 1`. On mute: `AudioManager.abandonAudioFocus(...)`, unregister receiver, set `volume = 0`.
  - `resume()` — reacquires focus, re-registers BECOMING_NOISY, sets `volume = 1`, resumes player, clears `playbackHint`. Called from the "tap to resume" overlay after focus-loss interruption.
  - `release()` — releases the `ExoPlayer`, abandons focus (if held), unregisters receiver (if registered), clears all listeners. Called from `DisposableEffect.onDispose`.

  Serializes player-state mutations via a coroutine `Mutex` so audio-focus and visibility callbacks don't race. The coordinator MUST NOT dispatch any `FeedEvent` and MUST NOT mutate VM state.
- [ ] 4.3 Wire the coordinator into `FeedScreen`: `val coordinator = remember { FeedVideoPlayerCoordinator(context, audioManager) }` (no key — composition lifetime IS the screen lifetime); `DisposableEffect(Unit) { onDispose { coordinator.release() } }` (matched no-key); a single `LaunchedEffect(listState, coordinator)` running the scroll-gated bind flow (Task 4.5 details the flow shape). Supply `videoEmbedSlot = { video -> PostCardVideoEmbed(video, post = post, coordinator = coordinator) }` to PostCard. NO `PostCallbacks` wiring needed for video — existing `PostCallbacks.onTap` covers card-body → navigate-to-detail; mute toggle goes coordinator-direct from PostCardVideoEmbed.
- [ ] 4.4 Extend `PostCardVideoEmbed` (file-level `@OptIn(UnstableApi::class)` required) to the phase-C autoplay variant. Signature: `PostCardVideoEmbed(video: EmbedUi.Video, post: PostUi, coordinator: FeedVideoPlayerCoordinator)`.
  - `val boundPostId by coordinator.boundPostId.collectAsStateWithLifecycle()`; `val isBoundHere = boundPostId == post.id`.
  - `val isUnmuted by coordinator.isUnmuted.collectAsStateWithLifecycle()`.
  - `val playbackHint by coordinator.playbackHint.collectAsStateWithLifecycle()`.
  - Outer container: `Modifier.fillMaxWidth().aspectRatio(video.aspectRatio)` BEFORE the poster loads (already in phase B).
  - Render `NubecitaAsyncImage(video.posterUrl)` filling the container.
  - When `isBoundHere`: render `PlayerSurface(player = coordinator.player, surfaceType = SURFACE_TYPE_TEXTURE_VIEW)` underneath the poster; cross-fade poster `alpha = 0f` once the player produces the first frame (observe via `Player.Listener.onRenderedFirstFrame`).
  - Render duration chip in bottom-right corner (already in phase B).
  - When `isBoundHere`: render mute icon overlay in top-right corner. Icon: `if (isUnmuted) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff`. Tap calls `coordinator.toggleMute()` directly.
  - When `isBoundHere && playbackHint == FocusLost`: overlay a localized "tap to resume" affordance over the player surface; tap calls `coordinator.resume()`.
  - Card-body tap (anywhere outside the mute icon and resume-overlay hit areas): no special handling — PostCard's outer `clickable` already invokes `PostCallbacks.onTap(post)`.
- [ ] 4.5 Scroll-gated bind flow in `FeedScreen`:
  ```kotlin
  LaunchedEffect(listState, coordinator) {
      combine(
          snapshotFlow { listState.isScrollInProgress },
          snapshotFlow { mostVisibleVideoPostId(listState.layoutInfo) }.distinctUntilChanged(),
      ) { scrolling, postId -> if (scrolling) null else postId }
          .distinctUntilChanged()
          .collect { postId -> coordinator.bindMostVisibleVideo(postId) }
  }
  ```
  While scrolling, emits `null` → coordinator unbinds + auto-mutes; the instant scroll settles, emits the resting most-visible post id → coordinator binds + autoplays muted. Assert in unit test that 20 rapid `layoutInfo` emissions while `isScrollInProgress = true` produce ZERO bind operations; one settling emission produces exactly one bind.
- [ ] 4.6 Audio focus contract:
  - On `coordinator.toggleMute()` from muted → unmuted: `AudioManager.requestAudioFocus(...)` for `AUDIOFOCUS_GAIN_TRANSIENT`, register BECOMING_NOISY receiver, set `volume = 1`, transition `isUnmuted = true`.
  - On `coordinator.toggleMute()` from unmuted → muted: `AudioManager.abandonAudioFocus(...)`, unregister receiver, set `volume = 0`, transition `isUnmuted = false`.
  - On scroll-driven rebind from an unmuted bound card to a different post: same release flow as user mute (BEFORE the rebind happens).
  - On `AUDIOFOCUS_LOSS_TRANSIENT` (incoming call, music app gaining focus) while `isUnmuted == true`: pause player, mute (`volume = 0`), release focus, unregister receiver, KEEP `isUnmuted == true` (user intent preserved), set `playbackHint = FocusLost`.
  - On `ACTION_AUDIO_BECOMING_NOISY` while `isUnmuted == true`: same as focus-loss-transient.
  - On `release()`: abandon focus + unregister if held; clear all StateFlows.
  - Cover with mocked `AudioManager` unit tests:
    - (a) Autoplay flow (initial bind, scroll between muted videos) NEVER calls `requestAudioFocus`.
    - (b) `toggleMute()` from muted → unmuted calls `requestAudioFocus` exactly once.
    - (c) Scroll-away from an unmuted card calls `abandonAudioFocus` exactly once and transitions `isUnmuted` to `false`.
    - (d) Focus loss while unmuted sets `playbackHint = FocusLost` and KEEPS `isUnmuted == true`.
    - (e) `release()` abandons focus and unregisters BECOMING_NOISY if held/registered.
- [ ] 4.7 HLS adaptive selector: `DefaultTrackSelector(...).buildUponParameters().setForceLowestBitrate(true)` for the initial selection. Coordinator tracks per-bound-post sustained-playback time; after 10 s of continuous playback on a single bound video, clear the force flag to allow ABR upgrade. A scroll-driven rebind to a different post resets the timer.
- [ ] 4.8 Manual smoke on a 120Hz device (Pixel 8 or similar):
  - **Music-not-hijacked test:** play music in another app (Spotify/YouTube Music). Open nubecita, scroll to a video post. Music KEEPS PLAYING. Scroll between several video posts — music continues uninterrupted. Tap the mute icon to unmute → music app pauses (audio focus claimed). Scroll past the unmuted video → music regains focus + resumes; the next video plays muted. Tap the mute icon again to unmute the new most-visible video → music app pauses again.
  - **Single-player invariant:** `adb shell dumpsys media.player` while scrolling through a feed of 5 video posts — exactly 1 active player at any sample.
  - **120Hz scrolling unaffected:** fling through the feed for 5 s — no jank, no decoder errors in logcat.
  - **Incoming call while unmuted:** simulate via `adb shell am start -a android.intent.action.CALL ...` while a video is unmuted → playback pauses, "tap to resume" affordance appears on the bound card.
  - **Headphones unplug while unmuted:** `adb shell am broadcast -a android.media.AUDIO_BECOMING_NOISY` → playback pauses, "tap to resume" appears.
  - **Screen exit:** navigate away from FeedScreen → `dumpsys media.player` reports 0 active players post-exit; if music app's focus had been held by us, it regains.
  - **Rotation:** rotate the device → video starts autoplaying muted from position 0 on the new player; no audio surprise (no focus was held).
  - **Card-body tap → detail:** tap on the video card (anywhere outside the mute icon's hit area) → navigates to the post-detail screen; the feed's coordinator releases its state on screen exit.

## 5. End-to-end smoke + final verification (executes in `nubecita-sbc.4`'s PR)

- [ ] 5.1 `./gradlew :app:assembleDebug` — green.
- [ ] 5.2 `./gradlew testDebugUnitTest` — green repo-wide.
- [ ] 5.3 `./gradlew :designsystem:validateDebugScreenshotTest :feature:feed:impl:validateDebugScreenshotTest` — green.
- [ ] 5.4 `./gradlew :app:bundleRelease` + measure APK size delta vs baseline; document in PR.
- [ ] 5.5 `pre-commit run --all-files` — green.
- [ ] 5.6 `openspec validate add-feature-feed-video-embeds --strict` — green.

## 6. PR + archive

- [ ] 6.1 Each phase opens its own PR per bd-workflow. PR titles: `feat(deps): add Media3 / ExoPlayer for video playback (sbc.1)`, `feat(feed): video embed thumbnail render (sbc.3)`, `feat(feed): autoplay-muted video coordinator with mute toggle (sbc.4)`.
- [ ] 6.2 Final phase (sbc.4) PR body summarizes the cumulative video-embed feature, links the openspec change, and closes `nubecita-sbc.4` + `nubecita-sbc` (the epic).
- [ ] 6.3 Archive the openspec change after sbc.4 merges: `openspec archive add-feature-feed-video-embeds -y` so the spec deltas merge into `openspec/specs/{feature-feed-video,feature-feed,data-models,design-system}/spec.md`.
