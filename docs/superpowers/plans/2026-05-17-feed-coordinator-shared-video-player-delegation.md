# `FeedVideoPlayerCoordinator` → `SharedVideoPlayer` delegation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor `FeedVideoPlayerCoordinator` so it delegates ExoPlayer ownership to the process-scoped `SharedVideoPlayer` singleton shipped in `nubecita-zak.1`, while preserving every user-visible behavior (autoplay-muted, in-feed mute icon, audio focus on unmute, BECOMING_NOISY pause, tap-to-resume overlay, bitrate-floor unlock). No PostCardVideoEmbed UX changes.

**Architecture:** The coordinator becomes a feed-specific facade over `SharedVideoPlayer`. The holder owns the ExoPlayer + TrackSelector + lazy reconstruction + idle-release. The coordinator continues to own its own audio-focus state (manual `AudioFocusRequest`, NOISY receiver, focus-change listener), mute toggle, and bitrate-unlock timer — these access the underlying ExoPlayer through `holder.player: StateFlow<Player?>`. The coordinator's bind path uses `holder.bind(...)` + `setMode(FeedPreview)` + `play()`; its unbind path uses `holder.detachSurface()` + `pause()` without clearing the bound URL (preserves the feed → fullscreen instance-transfer payoff). A `Player.Listener` for the bitrate-unlock trigger is re-attached every time `holder.player` emits a new non-null instance, so listener registration survives the holder's lazy reconstruction after idle-release. The coordinator's own `release()` no longer releases the player — the holder is singleton-scoped and is released by the auth-state-cleared broadcaster (out of scope for zak.2); feed's `release()` just detaches its surface and cancels its own scope.

**Tech Stack:** Existing — Kotlin + Coroutines + Media3 ExoPlayer 1.x + JUnit 5 + mockk. New consumer: `:core:video`'s `SharedVideoPlayer` from `nubecita-zak.1`.

---

## File Structure

**Files to modify:**

- `feature/feed/impl/build.gradle.kts` — add `implementation(project(":core:video"))`.
- `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/video/FeedVideoPlayerCoordinator.kt` — the substantive refactor. Constructor changes from `(context, audioManager, player, trackSelector)` to `(context, audioManager, sharedVideoPlayer)`. Internal `bindInternal` / `toggleMuteInternal` / `resumeInternal` / `handleFocusLostInternal` / `onPlaybackStarted` / `release` reroute through the holder. Player-listener re-attaches on `holder.player` emissions. Production factory `createFeedVideoPlayerCoordinator` collapses to a thin shim that takes the injected holder.
- `feature/feed/impl/src/test/kotlin/net/kikin/nubecita/feature/feed/impl/video/FeedVideoPlayerCoordinatorTest.kt` — test harness `newCoordinator()` injects a mockk `SharedVideoPlayer` (with stubbed `player: StateFlow<Player?>` emitting a mock ExoPlayer). Existing audio-focus + mute + resume + NOISY assertions migrate unchanged in shape; the ExoPlayer is now accessed through the holder mock rather than directly.
- `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/FeedScreen.kt` — call-site change: the `remember { FeedVideoPlayerCoordinator(...) }` block must now inject the Hilt-provided `SharedVideoPlayer`. Today the factory captures Application context + AudioManager from a `LocalContext.current`-derived lookup. After zak.2 the factory also needs `SharedVideoPlayer`, which is process-scoped and injected via Hilt. The cleanest seam is to pull it from a `@Composable` `hiltViewModel` or via a CompositionLocal; simpler still — FeedViewModel exposes the holder and FeedScreen reads it from the VM.

**Files NOT touched in this PR:**

- `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/ui/PostCardVideoEmbed.kt` — the mute icon overlay + resume overlay stay. `PlayerSurface(player = coordinator.player)` is the only call site; the coordinator exposes a `val player: StateFlow<Player?>` that proxies to the holder, so the call site does NOT change.
- `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/video/PlaybackHint.kt` — coordinator state hint, unchanged.
- `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/video/VideoBindingTarget.kt` — bind input shape, unchanged.

---

## Task 1: Add `:core:video` to `:feature:feed:impl`'s dependency set

**Files:**
- Modify: `feature/feed/impl/build.gradle.kts`

- [ ] **Step 1: Add the implementation dep**

Open `feature/feed/impl/build.gradle.kts` and add `implementation(project(":core:video"))` in the `dependencies { }` block, alphabetically among the other `implementation(project(":core:..."))` lines (after `:core:post-interactions` if the existing order is alphabetical; otherwise group with the other `:core:*` deps).

- [ ] **Step 2: Verify the module configures cleanly**

Run: `./gradlew :feature:feed:impl:dependencies --configuration debugCompileClasspath 2>&1 | grep -i 'core:video'`

Expected: shows `project :core:video` in the dependency tree.

Run: `./gradlew :feature:feed:impl:compileDebugKotlin`

Expected: BUILD SUCCESSFUL (no actual import of SharedVideoPlayer yet, just the dep wiring).

- [ ] **Step 3: Commit**

```bash
git add feature/feed/impl/build.gradle.kts
git commit -m "$(cat <<'EOF'
chore(feature/feed/impl): add :core:video dep

Foundation for the FeedVideoPlayerCoordinator refactor that delegates
ExoPlayer ownership to the SharedVideoPlayer singleton from
nubecita-zak.1. No code changes; this is just the build-graph wiring.

Refs: nubecita-zak.2
EOF
)"
```

---

## Task 2: Replace coordinator's constructor + introduce holder delegation seam

This task changes the constructor signature and adds the lazy-listener re-attach mechanism. The behavior of `bindInternal` / `toggleMuteInternal` / etc. stays referentially equivalent — they still mutate `player.X` directly. The difference is that `player` is now sourced from `holder.player.value` rather than a constructor field.

**Files:**
- Modify: `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/video/FeedVideoPlayerCoordinator.kt`

- [ ] **Step 1: Update constructor signature + class fields**

Replace the constructor declaration:

```kotlin
internal class FeedVideoPlayerCoordinator(
    context: Context,
    private val audioManager: AudioManager,
    val player: ExoPlayer,
    private val trackSelector: DefaultTrackSelector,
) {
```

with:

```kotlin
internal class FeedVideoPlayerCoordinator(
    context: Context,
    private val audioManager: AudioManager,
    private val sharedVideoPlayer: SharedVideoPlayer,
) {
    /**
     * Reactive accessor for the underlying ExoPlayer, proxied from
     * [sharedVideoPlayer]. PostCardVideoEmbed reads this via
     * `coordinator.player.collectAsStateWithLifecycle()` to render
     * `PlayerSurface(player = …)` against the currently-bound player.
     * Null until the first bind triggers lazy construction, and null
     * again after the holder's idle-release timer fires.
     *
     * Exposed on the coordinator (not the holder directly) so the
     * existing PostCardVideoEmbed call site doesn't need to know about
     * `:core:video`; only the feed module's wiring is aware of the
     * delegation.
     */
    val player: StateFlow<androidx.media3.common.Player?> = sharedVideoPlayer.player
```

Add the import at the top of the file:

```kotlin
import net.kikin.nubecita.core.video.PlaybackMode
import net.kikin.nubecita.core.video.SharedVideoPlayer
```

Drop these imports (no longer used directly by this file's body — the factory will reintroduce some as needed):

```kotlin
// REMOVE:
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
```

(But keep `androidx.media3.common.Player` and `androidx.media3.common.MediaItem` — still referenced in this file.)

- [ ] **Step 2: Add the listener-reattach mechanism**

Inside the class body, after the existing `bitrateUnlockJob: Job? = null` declaration and before the `noisyReceiver` field, add:

```kotlin
    /**
     * Re-attached every time [sharedVideoPlayer.player] emits a fresh
     * ExoPlayer (first bind, or post-idle-release rebind). Triggers
     * [notifyPlaybackStarted] when the player reaches STATE_READY +
     * playWhenReady. The holder's lazy reconstruction means the
     * listener registration can't live in a one-shot factory — it
     * must follow the player instance through release-and-recreate
     * cycles. The job is cancelled in [release].
     */
    private val playerListenerAttachJob: Job = scope.launch {
        sharedVideoPlayer.player.collect { current ->
            current?.addListener(playbackStartedListener)
            // Note: we don't removeListener on the prior player — the
            // holder's release() already detached all listeners by
            // releasing the ExoPlayer. The flow emits null between
            // release and the next bind, and we no-op on null.
        }
    }

    private val playbackStartedListener: Player.Listener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val p = sharedVideoPlayer.player.value ?: return
                if (playbackState == Player.STATE_READY && p.playWhenReady) {
                    notifyPlaybackStarted()
                }
            }
        }
```

- [ ] **Step 3: Update `release()` to drop player ownership + cancel the attach job**

Replace the existing `release()` body:

```kotlin
    fun release() {
        if (released) return
        released = true
        bitrateUnlockJob?.cancel()
        scope.cancel("FeedVideoPlayerCoordinator released")
        if (activeFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(activeFocusRequest!!)
            activeFocusRequest = null
        }
        if (noisyReceiverRegistered) {
            runCatching { appContext.unregisterReceiver(noisyReceiver) }
            noisyReceiverRegistered = false
        }
        player.release()
        _boundPostId.value = null
        _isUnmuted.value = false
        _playbackHint.value = PlaybackHint.None
    }
```

with:

```kotlin
    fun release() {
        if (released) return
        released = true
        bitrateUnlockJob?.cancel()
        playerListenerAttachJob.cancel()
        // Cancel coordinator's scope BEFORE the synchronous teardown
        // below so no queued bindInternal / toggleMuteInternal can race
        // with abandon / unregister.
        scope.cancel("FeedVideoPlayerCoordinator released")
        if (activeFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(activeFocusRequest!!)
            activeFocusRequest = null
        }
        if (noisyReceiverRegistered) {
            runCatching { appContext.unregisterReceiver(noisyReceiver) }
            noisyReceiverRegistered = false
        }
        // NOTE: do NOT call sharedVideoPlayer.release() here. The
        // holder is process-scoped — feed exiting MainShell is not a
        // signal to tear down playback. The holder's idle-release
        // timer + the auth-state-cleared broadcaster (out of scope for
        // zak.2) own the holder's lifecycle. Feed just stops driving.
        sharedVideoPlayer.detachSurface()
        _boundPostId.value = null
        _isUnmuted.value = false
        _playbackHint.value = PlaybackHint.None
    }
```

- [ ] **Step 4: Reroute `bindInternal` through the holder**

Replace the existing `bindInternal` body:

```kotlin
    private suspend fun bindInternal(target: VideoBindingTarget?) =
        mutex.withLock {
            if (released) return@withLock
            val current = _boundPostId.value
            if (target?.postId == current && target != null) {
                return@withLock
            }
            if (_isUnmuted.value) {
                releaseFocusAndUnregisterNoisy()
                player.volume = 0f
                _isUnmuted.value = false
            }
            _playbackHint.value = PlaybackHint.None
            bitrateUnlockJob?.cancel()
            bitrateUnlockJob = null
            trackSelector.setParameters(
                trackSelector.buildUponParameters().setForceLowestBitrate(true),
            )

            if (target == null) {
                player.pause()
                player.clearMediaItems()
                _boundPostId.value = null
                return@withLock
            }
            _boundPostId.value = target.postId
            player.setMediaItem(MediaItem.fromUri(target.playlistUrl))
            player.prepare()
            player.playWhenReady = true
        }
```

with:

```kotlin
    private suspend fun bindInternal(target: VideoBindingTarget?) =
        mutex.withLock {
            if (released) return@withLock
            val current = _boundPostId.value
            if (target?.postId == current && target != null) {
                return@withLock
            }
            // Cross-card transition (or unbind): drop the unmute state +
            // audio focus + NOISY receiver first per the "scroll-away
            // from an unmuted card auto-mutes" contract.
            if (_isUnmuted.value) {
                releaseFocusAndUnregisterNoisy()
                sharedVideoPlayer.player.value?.volume = 0f
                _isUnmuted.value = false
            }
            _playbackHint.value = PlaybackHint.None
            bitrateUnlockJob?.cancel()
            bitrateUnlockJob = null

            if (target == null) {
                // Unbind: pause + detach surface but DO NOT clear the
                // holder's bound URL. The same playlist stays prepared
                // so a future fullscreen tap (zak.4 / zak.5) can pick
                // up mid-playback via the instance-transfer payoff.
                sharedVideoPlayer.player.value?.pause()
                sharedVideoPlayer.detachSurface()
                _boundPostId.value = null
                return@withLock
            }

            // Bind: ensure the holder is in FeedPreview mode (the
            // holder also pins volume=0 + handleAudioFocus=false; the
            // coordinator may later flip volume=1 for unmute via
            // direct player.volume mutation, leaving the holder's
            // mode at FeedPreview — see toggleMuteInternal).
            sharedVideoPlayer.setMode(PlaybackMode.FeedPreview)
            sharedVideoPlayer.bind(playlistUrl = target.playlistUrl, posterUrl = null)
            sharedVideoPlayer.attachSurface()
            _boundPostId.value = target.postId
            sharedVideoPlayer.play()
        }
```

Note the trackSelector.setParameters call was removed — the holder's production factory pins the bitrate floor on each new ExoPlayer instance via the lambda inside `createSharedVideoPlayer`. The unlock timer that lifts the floor mid-playback no longer has a coordinator-local trackSelector to mutate. That logic moves to Task 3.

- [ ] **Step 5: Reroute the rest of the volume + player.X access**

Replace these blocks inside the class:

`toggleMuteInternal()`:

```kotlin
    private suspend fun toggleMuteInternal() =
        mutex.withLock {
            if (released) return@withLock
            if (_boundPostId.value == null) return@withLock
            if (_isUnmuted.value) {
                releaseFocusAndUnregisterNoisy()
                sharedVideoPlayer.player.value?.volume = 0f
                _isUnmuted.value = false
            } else {
                val granted = requestAudioFocus()
                if (granted) {
                    registerNoisyReceiver()
                    sharedVideoPlayer.player.value?.volume = 1f
                    _isUnmuted.value = true
                    _playbackHint.value = PlaybackHint.None
                }
            }
        }
```

`resumeInternal()`:

```kotlin
    private suspend fun resumeInternal() =
        mutex.withLock {
            if (released) return@withLock
            if (_playbackHint.value != PlaybackHint.FocusLost) return@withLock
            if (_boundPostId.value == null) {
                _playbackHint.value = PlaybackHint.None
                return@withLock
            }
            val granted = requestAudioFocus()
            if (granted) {
                registerNoisyReceiver()
                val p = sharedVideoPlayer.player.value
                if (p != null) {
                    p.volume = 1f
                    p.play()
                }
                _playbackHint.value = PlaybackHint.None
            }
        }
```

`handleFocusLostInternal()`:

```kotlin
    private suspend fun handleFocusLostInternal() =
        mutex.withLock {
            if (released) return@withLock
            if (!_isUnmuted.value) return@withLock
            val p = sharedVideoPlayer.player.value
            if (p != null) {
                p.pause()
                p.volume = 0f
            }
            releaseFocusAndUnregisterNoisy()
            _playbackHint.value = PlaybackHint.FocusLost
        }
```

- [ ] **Step 6: Drop the now-unused trackSelector + media3 imports from the coordinator file**

`DefaultTrackSelector` is no longer referenced inside the coordinator (the holder owns it). Confirm the import is gone (Task 2 Step 1 should have removed it). The `Media3AudioAttributes` alias and `androidx.media3.common.C` import are also unused now since AudioAttributes construction lives in the holder; remove them too.

After this step, the only `androidx.media3.*` imports the coordinator file needs are:
- `androidx.media3.common.Player` (for the listener type)
- `androidx.media3.common.util.UnstableApi` (file-level `@OptIn`)
- `androidx.media3.common.MediaItem` — **REMOVE** if no longer referenced (the bind path uses the holder's `bind`, not direct setMediaItem). Verify by grep.

- [ ] **Step 7: Verify the module compiles**

Run: `./gradlew :feature:feed:impl:compileDebugKotlin`

Expected: BUILD SUCCESSFUL. If unresolved-import errors fire, the prior import-cleanup steps missed something; resolve and re-run.

NOTE: tests will FAIL at this point because the existing tests inject `ExoPlayer + DefaultTrackSelector`. They get fixed in Task 5. Tests should NOT pass yet — that's a Task 5 concern.

- [ ] **Step 8: Commit**

```bash
git add feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/video/FeedVideoPlayerCoordinator.kt
git commit -m "$(cat <<'EOF'
chore(feature/feed/impl): delegate coordinator player ownership to sharedvideoplayer

Constructor now takes a SharedVideoPlayer instead of (ExoPlayer,
DefaultTrackSelector). All player-state mutations route through the
holder: bind/setMode/attachSurface/play/detachSurface for lifecycle,
holder.player.value for volume + focus-loss-driven pause. The
coordinator's manual AudioFocusRequest plumbing + NOISY receiver +
mute toggle + tap-to-resume overlay stay intact for the user-facing
in-feed unmute affordance (deletion deferred to nubecita-zak.5 when
the tap-to-fullscreen wire makes mute-in-feed redundant).

The Player.Listener for the bitrate-unlock trigger now re-attaches on
every emission of holder.player so listener registration survives
the holder's lazy reconstruction after idle-release.

release() no longer calls player.release() — the holder is
process-scoped and released by the auth-state-cleared broadcaster.
Feed's release just detaches its surface and tears down its own
state.

Tests are intentionally broken until task 5's harness migration.

Refs: nubecita-zak.2
EOF
)"
```

---

## Task 3: Move the bitrate-floor unlock timer to use the holder's trackSelector access

The coordinator's existing `onPlaybackStarted` schedules a 10-second timer that flips the holder's `DefaultTrackSelector` from `setForceLowestBitrate(true)` to `setForceLowestBitrate(false)`. After zak.2 the coordinator no longer holds the track selector. Options:

- (a) Promote the unlock logic into `SharedVideoPlayer` itself (zak.1 contract addition).
- (b) Keep the unlock logic in the coordinator but expose a track-selector accessor on the holder.
- (c) Defer the unlock to zak.4 entirely; ship zak.2 with the bitrate floor permanently pinned in feed.

Option (c) regresses feed video quality after sustained playback. Reject.

Option (a) is the cleanest long-term but means re-touching zak.1 / `SharedVideoPlayer`. Spec defers this to zak.4. Reject for zak.2 scope.

Option (b) is the pragmatic middle: add a single internal accessor on the holder for the track selector. Doesn't change behavior; lets feed retain bitrate-unlock behavior.

**Files:**
- Modify: `core/video/src/main/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayer.kt`
- Modify: `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/video/FeedVideoPlayerCoordinator.kt`

- [ ] **Step 1: Add the track-selector accessor to `SharedVideoPlayer`**

In `core/video/src/main/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayer.kt`, change the constructor to also take a `trackSelectorFactory: () -> DefaultTrackSelector` alongside the existing `playerFactory`. The current factory lambda inside `createSharedVideoPlayer` already builds the track selector inside the player-factory closure; split that out so it's accessible:

In `SharedVideoPlayer.kt`, change the constructor:

```kotlin
class SharedVideoPlayer
    internal constructor(
        private val playerFactory: () -> ExoPlayer,
        private val trackSelectorFactory: () -> DefaultTrackSelector,
        private val scope: CoroutineScope,
        private val idleReleaseMs: Long,
    ) {
```

Add a private cache + an accessor:

```kotlin
        private var cachedTrackSelector: DefaultTrackSelector? = null

        /**
         * The currently-bound `DefaultTrackSelector`, lazy-constructed
         * alongside the ExoPlayer. Exposed for in-feed bitrate-floor
         * unlock logic (see `:feature:feed:impl`'s coordinator). zak.4's
         * VM will likely subsume this surface when the sustained-playback
         * unlock policy moves into the holder; for now the accessor is
         * internal and only `:feature:feed:impl` consumes it.
         */
        internal val trackSelector: DefaultTrackSelector?
            get() = cachedTrackSelector
```

Update `requirePlayer()` to also instantiate the track selector via its factory and cache it:

```kotlin
        private fun requirePlayer(): ExoPlayer {
            val existing = cachedExoPlayer
            if (existing != null) return existing
            val ts = trackSelectorFactory()
            cachedTrackSelector = ts
            val built = playerFactory(ts)
            cachedExoPlayer = built
            _playerFlow.value = built
            return built
        }
```

Wait — the existing `playerFactory: () -> ExoPlayer` doesn't take a track selector. To wire the selector to the player we need either:
- `playerFactory: (DefaultTrackSelector) -> ExoPlayer`, OR
- Inline the track selector construction inside the player factory and expose it via a different mechanism.

The cleanest fix: change `playerFactory` to take a `DefaultTrackSelector` parameter, so the caller wires the two together. Update both the constructor and `createSharedVideoPlayer` accordingly.

In `SharedVideoPlayer.kt`, change `playerFactory`:

```kotlin
private val playerFactory: (DefaultTrackSelector) -> ExoPlayer,
```

And inside `requirePlayer()`:

```kotlin
        private fun requirePlayer(): ExoPlayer {
            val existing = cachedExoPlayer
            if (existing != null) return existing
            val ts = cachedTrackSelector ?: trackSelectorFactory().also { cachedTrackSelector = it }
            val built = playerFactory(ts)
            cachedExoPlayer = built
            _playerFlow.value = built
            return built
        }
```

And update `release()` (and the idle-release block inside `detachSurface`) to clear both caches:

```kotlin
        fun release() {
            idleReleaseJob?.cancel()
            idleReleaseJob = null
            cachedExoPlayer?.release()
            cachedExoPlayer = null
            cachedTrackSelector = null
            _playerFlow.value = null
            _mode.value = PlaybackMode.FeedPreview
            _boundPlaylistUrl.value = null
            _isPlaying.value = false
        }
```

Same null-out of `cachedTrackSelector` inside the idle-release timer's launch body.

Update the production factory `createSharedVideoPlayer`:

```kotlin
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun createSharedVideoPlayer(
    context: android.content.Context,
    scope: CoroutineScope,
    idleReleaseMs: Long = DEFAULT_IDLE_RELEASE_MS,
): SharedVideoPlayer {
    val appContext = context.applicationContext
    return SharedVideoPlayer(
        playerFactory = { trackSelector ->
            val attrs =
                androidx.media3.common.AudioAttributes
                    .Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build()
            ExoPlayer
                .Builder(appContext)
                .setTrackSelector(trackSelector)
                .build()
                .apply {
                    volume = 0f
                    setAudioAttributes(attrs, false)
                }
        },
        trackSelectorFactory = {
            DefaultTrackSelector(appContext).apply {
                setParameters(buildUponParameters().setForceLowestBitrate(true))
            }
        },
        scope = scope,
        idleReleaseMs = idleReleaseMs,
    )
}
```

- [ ] **Step 2: Update `SharedVideoPlayerTest.kt`'s `newHolder` helper**

The harness needs to provide the second factory. In `core/video/src/test/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayerTest.kt`, change `newHolder`:

```kotlin
    private fun newHolder(
        testScope: kotlinx.coroutines.test.TestScope,
    ): Pair<SharedVideoPlayer, ExoPlayer> {
        val player = mockk<ExoPlayer>(relaxed = true)
        val trackSelector = mockk<DefaultTrackSelector>(relaxed = true)
        val holder =
            SharedVideoPlayer(
                playerFactory = { _ -> player },
                trackSelectorFactory = { trackSelector },
                scope = testScope,
                idleReleaseMs = 30_000L,
            )
        return holder to player
    }
```

Re-add the `import androidx.media3.exoplayer.trackselection.DefaultTrackSelector` to the test file (was removed in zak.1's commit `3fad9af5`).

Update the `bind_afterRelease_recreatesPlayer_andSetsMediaItem` test similarly — the counting factory there is `playerFactory: () -> ExoPlayer`; it now becomes `(DefaultTrackSelector) -> ExoPlayer`:

```kotlin
    @Test
    fun bind_afterRelease_recreatesPlayer_andSetsMediaItem() =
        runTest {
            val player = mockk<ExoPlayer>(relaxed = true)
            val trackSelector = mockk<DefaultTrackSelector>(relaxed = true)
            var invocations = 0
            val holder =
                SharedVideoPlayer(
                    playerFactory = { _ ->
                        invocations += 1
                        player
                    },
                    trackSelectorFactory = { trackSelector },
                    scope = this,
                    idleReleaseMs = 30_000L,
                )
            // ... rest of the test body unchanged
        }
```

- [ ] **Step 3: Run :core:video tests to confirm the holder still works**

Run: `./gradlew :core:video:testDebugUnitTest`

Expected: all 18 tests pass.

- [ ] **Step 4: Update the coordinator's `onPlaybackStarted` to use the holder's track-selector accessor**

In `FeedVideoPlayerCoordinator.kt`, replace the existing `onPlaybackStarted` body's track-selector access:

```kotlin
    private suspend fun onPlaybackStarted() =
        mutex.withLock {
            if (released) return@withLock
            val bindAtScheduling = _boundPostId.value ?: return@withLock
            if (bitrateUnlockJob?.isActive == true) return@withLock
            bitrateUnlockJob =
                scope.launch {
                    delay(SUSTAINED_PLAYBACK_BITRATE_UNLOCK_MS)
                    mutex.withLock {
                        if (released) return@withLock
                        if (_boundPostId.value != bindAtScheduling) return@withLock
                        val ts = sharedVideoPlayer.trackSelector ?: return@withLock
                        ts.setParameters(
                            ts.buildUponParameters().setForceLowestBitrate(false),
                        )
                        bitrateUnlockJob = null
                    }
                }
        }
```

The `sharedVideoPlayer.trackSelector ?: return@withLock` guard handles the case where the holder's idle-release fired between the bind and the timer expiry — without the track selector, there's nothing to unlock; the next bind reconstructs at the locked floor.

- [ ] **Step 5: Verify the module compiles**

Run: `./gradlew :feature:feed:impl:compileDebugKotlin :core:video:compileDebugKotlin`

Expected: BUILD SUCCESSFUL for both modules.

- [ ] **Step 6: Commit**

```bash
git add core/video/src/main/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayer.kt core/video/src/test/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayerTest.kt feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/video/FeedVideoPlayerCoordinator.kt
git commit -m "$(cat <<'EOF'
feat(core/video): expose tracksselector accessor for feed bitrate unlock

Splits the SharedVideoPlayer constructor's monolithic playerFactory
into separate playerFactory(trackSelector) + trackSelectorFactory
lambdas so both pieces can be lazy-constructed in lockstep. Adds
internal val trackSelector: DefaultTrackSelector? accessor — feed's
coordinator uses it to lift the HLS bitrate floor after sustained
playback (the unlock policy stays in :feature:feed:impl for zak.2;
zak.4 will likely subsume this into the holder itself).

Coordinator's onPlaybackStarted now reaches into the holder's track
selector instead of holding its own; idle-release between bind and
unlock-timer-expiry is handled by an early return.

Refs: nubecita-zak.2
EOF
)"
```

---

## Task 4: Update the production factory + `FeedScreen` wiring

The existing `createFeedVideoPlayerCoordinator(context, audioManager)` builds an ExoPlayer + TrackSelector itself. After zak.2, the coordinator is constructed with an injected `SharedVideoPlayer`. The factory shrinks; the caller (`FeedScreen`) needs to obtain the holder somehow.

**Files:**
- Modify: `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/video/FeedVideoPlayerCoordinator.kt` (factory at the bottom of the file)
- Modify: `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/FeedScreen.kt`
- Modify: `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/FeedViewModel.kt` (likely — to expose the holder for FeedScreen)

- [ ] **Step 1: Collapse the production factory**

Replace the entire `createFeedVideoPlayerCoordinator` body at the bottom of `FeedVideoPlayerCoordinator.kt`:

```kotlin
@androidx.annotation.OptIn(UnstableApi::class)
internal fun createFeedVideoPlayerCoordinator(
    context: Context,
    audioManager: AudioManager,
): FeedVideoPlayerCoordinator { ... existing body ... }
```

with:

```kotlin
internal fun createFeedVideoPlayerCoordinator(
    context: Context,
    audioManager: AudioManager,
    sharedVideoPlayer: SharedVideoPlayer,
): FeedVideoPlayerCoordinator =
    FeedVideoPlayerCoordinator(
        context = context,
        audioManager = audioManager,
        sharedVideoPlayer = sharedVideoPlayer,
    )
```

The factory no longer needs `@OptIn(UnstableApi::class)` since the unstable-api ExoPlayer construction has lifted to `:core:video`'s factory. Drop the file-level `@file:OptIn(UnstableApi::class)` if no other declarations in the file still need it — verify by grep for `UnstableApi` after the cleanup. If only the existing class body referenced UnstableApi for `androidx.media3.common.util.UnstableApi`-annotated types like `DefaultTrackSelector`, and those references are now gone (per Task 2 cleanup), the file-level opt-in can be removed entirely.

- [ ] **Step 2: Expose `sharedVideoPlayer` from `FeedViewModel`**

The cleanest seam between Hilt-injected scope and a composable-remembered coordinator is to inject the holder into `FeedViewModel` and expose it as a public field.

Read `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/FeedViewModel.kt`. Find the constructor (Hilt-injected). Add `private val sharedVideoPlayer: SharedVideoPlayer` to the parameter list and expose it:

```kotlin
@HiltViewModel
internal class FeedViewModel
    @Inject
    constructor(
        // ... existing params ...
        val sharedVideoPlayer: SharedVideoPlayer,
    ) : MviViewModel<FeedState, FeedEvent, FeedEffect>(FeedState()) {
```

Mark the parameter `val` (not `private val`) so the screen can read it.

Add the import:

```kotlin
import net.kikin.nubecita.core.video.SharedVideoPlayer
```

- [ ] **Step 3: Update `FeedScreen.kt`'s coordinator construction**

In `FeedScreen.kt`, find where the coordinator is constructed today (`remember { createFeedVideoPlayerCoordinator(context, audioManager) }`). Pass `viewModel.sharedVideoPlayer` through:

```kotlin
val coordinator =
    remember(viewModel) {
        createFeedVideoPlayerCoordinator(
            context = appContext,
            audioManager = audioManager,
            sharedVideoPlayer = viewModel.sharedVideoPlayer,
        )
    }
```

If the existing `remember(...)` key was `Unit` or just `context`, change it to `viewModel` so a VM re-creation (e.g. after a process death + restore) reconstructs the coordinator with the right holder reference.

- [ ] **Step 4: Verify the module compiles**

Run: `./gradlew :feature:feed:impl:compileDebugKotlin :feature:feed:impl:kspDebugKotlin`

Expected: BUILD SUCCESSFUL. The Hilt code generation needs to see `SharedVideoPlayer` as an injectable dep — since `:core:video`'s `VideoPlayerModule` provides `@Singleton SharedVideoPlayer`, the Hilt graph resolves it automatically.

- [ ] **Step 5: Commit**

```bash
git add feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/video/FeedVideoPlayerCoordinator.kt feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/FeedScreen.kt feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/FeedViewModel.kt
git commit -m "$(cat <<'EOF'
chore(feature/feed/impl): wire sharedvideoplayer through feedviewmodel

createFeedVideoPlayerCoordinator collapses to a thin facade; the
ExoPlayer + TrackSelector construction lifts entirely to :core:video.
FeedViewModel exposes the Hilt-injected SharedVideoPlayer so
FeedScreen can pass it into the remember-block coordinator.

Refs: nubecita-zak.2
EOF
)"
```

---

## Task 5: Migrate the existing tests to the new constructor

Existing `FeedVideoPlayerCoordinatorTest` constructs the coordinator with `(context, audioManager, exoPlayerMock, trackSelectorMock)`. After zak.2 the constructor takes `(context, audioManager, sharedVideoPlayerMock)` and accesses the ExoPlayer through `sharedVideoPlayerMock.player.value`. The audio-focus assertions stay structurally the same; only the test harness changes.

**Files:**
- Modify: `feature/feed/impl/src/test/kotlin/net/kikin/nubecita/feature/feed/impl/video/FeedVideoPlayerCoordinatorTest.kt`

- [ ] **Step 1: Rewrite the `newCoordinator()` helper**

Replace the existing helper at the bottom of the test class:

```kotlin
    private fun newCoordinator(): FeedVideoPlayerCoordinator = ...
```

with:

```kotlin
    private fun newCoordinator(): FeedVideoPlayerCoordinator {
        val holder = mockk<SharedVideoPlayer>(relaxed = true)
        // Stub holder.player as a real MutableStateFlow seeded with the
        // mock ExoPlayer so the coordinator's listener-attach coroutine
        // sees a non-null player on its first collect emission, mirroring
        // the production "first bind triggers lazy construction" flow.
        every { holder.player } returns MutableStateFlow<Player?>(mockPlayer).asStateFlow()
        return FeedVideoPlayerCoordinator(
            context = appContext,
            audioManager = audioManager,
            sharedVideoPlayer = holder,
        )
    }
```

Add (or keep, depending on existing imports) at the top of the file:

```kotlin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.media3.common.Player
import net.kikin.nubecita.core.video.SharedVideoPlayer
```

Drop the imports that were specific to direct ExoPlayer/TrackSelector construction:

```kotlin
// REMOVE if not used elsewhere in test:
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
```

(But keep `import androidx.media3.exoplayer.ExoPlayer` — `mockPlayer` is still an `ExoPlayer` mock.)

`mockPlayer` was already a class-level `mockk<ExoPlayer>(relaxed = true)` field in the existing test — keep it. The change is that it now lives behind `holder.player` rather than being passed directly to the coordinator.

- [ ] **Step 2: Update the test class's holder reference for tests that assert against `coordinator.player`**

The test bodies likely call `verify { mockPlayer.X }` (e.g. `verify(exactly = 0) { mockPlayer.setMediaItem(any()) }`). Those keep working because the coordinator's bind path now goes through `sharedVideoPlayer.bind(...)`, NOT through `mockPlayer.setMediaItem` directly. The volume/play/pause mutations DO still go through `mockPlayer` (via `holder.player.value?.X`).

Where existing tests check `setMediaItem` / `prepare` / `playWhenReady` directly on `mockPlayer`, those assertions need to migrate to checking the holder:

```kotlin
// OLD:
verify { mockPlayer.setMediaItem(any()) }
verify { mockPlayer.prepare() }

// NEW:
verify { holder.bind(any(), any()) }
verify { holder.play() }
```

Where existing tests check `mockPlayer.volume = X` or `mockPlayer.pause()`, the assertions stay (volume + pause go through `holder.player.value?.X` which is `mockPlayer`).

Add a class-level field for the holder mock:

```kotlin
    private lateinit var holder: SharedVideoPlayer
```

And in `setUp()`:

```kotlin
    @BeforeEach
    fun setUp() {
        // ... existing field setup ...
        holder = mockk<SharedVideoPlayer>(relaxed = true)
        every { holder.player } returns MutableStateFlow<Player?>(mockPlayer).asStateFlow()
        // No longer pass mockPlayer + trackSelectorMock to coordinator;
        // the holder owns those.
    }
```

Update `newCoordinator()` to use the class-level `holder` field instead of creating one locally.

- [ ] **Step 3: Audit the existing 8 tests and update assertions**

Open the test file. For each `@Test` function, identify which assertions need to migrate. The audit:

1. **`bindMostVisibleVideo never requests audio focus`**: Asserts `verify(exactly = 0) { audioManager.requestAudioFocus(any()) }`. Audio focus is still managed by the coordinator. Assertion stays unchanged.

2. **`toggleMute from muted to unmuted requests focus exactly once`**: Asserts `verify(exactly = 1) { audioManager.requestAudioFocus(any()) }` + volume. Audio focus stays in coordinator; assertion stays. Volume assertion (`verify { mockPlayer.volume = 1f }`) stays — volume is set via `holder.player.value?.volume = 1f` which resolves to mockPlayer.

3. **`toggleMute denies do not transition isUnmuted`**: stub returns DENIED; asserts no volume change. Stays.

4. **`releasing focus on rebind`** (line 149-171): asserts that rebinding releases focus via `abandonAudioFocusRequest`. Stays.

5. **`focus loss while unmuted preserves isUnmuted and sets FocusLost hint`**: asserts pause + volume=0 + abandon. Pause and volume stay (through mockPlayer); abandon stays.

6. **`BECOMING_NOISY broadcast while unmuted surfaces FocusLost hint`**: same shape.

7. **`resumeAfterFocusLost reacquires focus`** (line 223-243): asserts `requestAudioFocus` after resume. Stays.

8. **`release without ever unmuting does not call abandonAudioFocus`** (line 244-256): asserts no `abandonAudioFocusRequest` call. Stays. BUT — verify that release no longer asserts `verify { mockPlayer.release() }` since feed's release no longer releases the player. If the test checks `mockPlayer.release()`, update to `verify(exactly = 0) { mockPlayer.release() }` + add `verify { holder.detachSurface() }`.

9. **`release` test at line 257-281**: probably the most-affected. The old release called `player.release()` + `audioManager.abandonAudioFocusRequest()` (if held) + `unregisterReceiver`. The new release calls `holder.detachSurface()` + abandon (if held) + unregister. Update assertions to:

```kotlin
verify(exactly = 1) { holder.detachSurface() }
verify(exactly = 0) { mockPlayer.release() } // crucial: feed must NOT release the singleton holder
```

For each test, perform Step 3 in-place by editing the test file. List each test by name in commit notes so reviewers can confirm coverage didn't shrink.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :feature:feed:impl:testDebugUnitTest --tests "*FeedVideoPlayerCoordinatorTest*"`

Expected: all existing tests pass after migration. If a test fails because the assertion shape is wrong for the new path, fix the assertion (per Step 3 audit) — but do NOT delete tests. Every audio-focus assertion must continue to pass.

If a test fails because of a real behavior regression (the coordinator's bind path doesn't actually call `holder.bind` etc.), STOP and investigate. That's a Task 2 bug, not a Task 5 bug.

- [ ] **Step 5: Commit**

```bash
git add feature/feed/impl/src/test/kotlin/net/kikin/nubecita/feature/feed/impl/video/FeedVideoPlayerCoordinatorTest.kt
git commit -m "$(cat <<'EOF'
test(feature/feed/impl): migrate coordinator test harness to sharedvideoplayer

newCoordinator() now constructs the coordinator with a relaxed-mockk
SharedVideoPlayer whose .player flow is seeded with the existing
mockPlayer. Bind-path assertions move from verify { mockPlayer.X } to
verify { holder.X } for setMediaItem/prepare/playWhenReady (now
delegated). Volume + pause assertions stay on mockPlayer (still
accessed via holder.player.value). Release test verifies
holder.detachSurface() is called and mockPlayer.release() is NOT —
the singleton holder is owned by :core:video, not by feed scope.

All 8 existing tests preserved; audio-focus contract assertions
unchanged.

Refs: nubecita-zak.2
EOF
)"
```

---

## Task 6: Final verification + spotless

**Files:** none (verification only)

- [ ] **Step 1: Run the full local gauntlet**

Run, in order:

1. `./gradlew :core:video:testDebugUnitTest` — Expected: PASS. zak.1's tests must still be green after Task 3's API addition.
2. `./gradlew :feature:feed:impl:testDebugUnitTest` — Expected: PASS. Feed coordinator tests + any other feed impl tests.
3. `./gradlew :feature:feed:impl:spotlessCheck` — Expected: PASS. Apply `:feature:feed:impl:spotlessApply` if not.
4. `./gradlew :feature:feed:impl:lint` — Expected: PASS.
5. `./gradlew assembleDebug` — Expected: PASS. Full app graph compiles + dexes + packages.

- [ ] **Step 2: Install on a device + smoke-test feed video**

The "no user-visible change" promise requires manual verification of the in-feed video behaviors:

- Open the app, scroll into a feed video card.
- Verify autoplay starts (muted) when the card becomes most-visible.
- Tap the mute icon: audio plays.
- Tap again: audio mutes.
- Scroll to another video card: prior card mutes; new card autoplays muted.
- Trigger audio focus loss (start playing music in another app, then return to nubecita): the "tap to resume" overlay appears.
- Tap the overlay: playback resumes with audio.

If any of these regress, STOP and triage. Per the user's chosen Path A, none of these behaviors should change.

Run: `adb devices -l` then `ANDROID_SERIAL=<serial> ./gradlew :app:installDebug`. If the install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, `adb -s <serial> uninstall net.kikin.nubecita` then retry. Pixel Fold (`37201FDHS002UN`) or the running emulator both work.

- [ ] **Step 3: If spotless needed to apply, commit the auto-format**

If `spotlessApply` made edits:

```bash
git add core/video/ feature/feed/impl/
git commit -m "$(cat <<'EOF'
chore(feature/feed/impl): spotless apply

Refs: nubecita-zak.2
EOF
)"
```

If no auto-format was needed, skip.

---

## Self-Review

**Spec coverage:** The plan covers every line item from `nubecita-zak.2`'s bd description: coordinator delegates bind to `SharedVideoPlayer.bind/setMode/play` (Task 2), unbind calls `detachSurface()` without clearing bound URL (Task 2 Step 4), the existing test contract is preserved (Task 5), and the no-user-visible-change constraint maps to the kept mute/focus/resume/playbackHint surface (per user's chosen Path A).

**Placeholder scan:** No "TBD" / "TODO" / "fill in" markers. Every step has either runnable code or a runnable command with expected output.

**Type consistency:** Every reference to `SharedVideoPlayer` matches the contract in `core/video/src/main/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayer.kt` after Task 3's constructor evolution. The `playerFactory: (DefaultTrackSelector) -> ExoPlayer` change in Task 3 propagates correctly into the production factory and the test harness in the same task. `holder.player: StateFlow<Player?>` (zak.1) is consumed via `holder.player.value?.X` for snapshot reads and via `.collect { … }` for the listener-reattach. `holder.detachSurface()` matches zak.1's no-arg `attachSurface()` / `detachSurface()` shape.

**Cross-cutting risk: zak.1 constructor change.** Task 3 modifies `SharedVideoPlayer`'s constructor. zak.1's existing 18 tests in `:core:video` are migrated in Task 3 Step 2; verified by Task 6 Step 1. If a downstream consumer outside this PR exists (none should — zak.1's holder has no production callers yet), it would break. Confirmed by `git grep "SharedVideoPlayer(" --` showing only test files + production factory inside `:core:video` itself before zak.2.

**Cross-cutting risk: bitrate-floor unlock semantics after idle-release.** The holder's lazy reconstruction starts a fresh ExoPlayer with the floor pinned (`setForceLowestBitrate(true)`). The coordinator's unlock timer fires once per bind via `notifyPlaybackStarted` → `onPlaybackStarted` → 10s `delay`. After an idle-release-and-rebind, the coordinator's listener (re-attached via Task 2's `playerListenerAttachJob`) fires `notifyPlaybackStarted` on the new player's STATE_READY, and the unlock timer starts fresh. So the timing semantics are preserved across the idle-release boundary.

**Cross-cutting risk: process-death recovery.** The coordinator is `remember { … }` in FeedScreen; FeedViewModel survives process death via `SavedStateHandle`. After process restore, the new coordinator's `playerListenerAttachJob` collects `holder.player` which is `null` until the next bind, then non-null after. Listener attaches lazily on first emission. No regression.

**Out-of-scope deliberately omitted from zak.2:**

- Dropping the in-feed mute icon + the manual audio-focus management. Deferred to **zak.5** (tap-to-fullscreen wire) per the user's Path A decision — by then fullscreen owns audio properly and the in-feed surface can become silent-only.
- Moving the bitrate-floor sustained-playback unlock into `SharedVideoPlayer`. Deferred to **zak.4** per the spec. zak.2 keeps the unlock logic in the coordinator via the new `holder.trackSelector` accessor (Task 3).
- Position polling / `isPlaying`-from-Listener / `playbackError`-from-Listener wiring on `SharedVideoPlayer`. Deferred to **zak.4** — zak.2 doesn't need them; feed coordinator manages its own `isUnmuted` + `playbackHint` flows for the existing UX.
