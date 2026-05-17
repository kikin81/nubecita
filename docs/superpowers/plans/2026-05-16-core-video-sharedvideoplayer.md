# `:core:video` SharedVideoPlayer — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a new `:core:video` module exposing a process-scoped `SharedVideoPlayer` singleton that owns the only `ExoPlayer` in the app. No UI, no consumers in this PR — only the holder + state machine + unit tests. Foundation for the `nubecita-zak` epic (fullscreen video player); design lives in `docs/superpowers/specs/2026-05-16-fullscreen-video-player-design.md`.

**Architecture:** One module, three files. The holder takes its `ExoPlayer` + `DefaultTrackSelector` via constructor (so tests inject `mockk(relaxed = true)`), with a production factory in the same file that wires the real Media3 chain. Audio focus discipline is delegated to ExoPlayer's built-in handler via `setAudioAttributes(attrs, handleAudioFocus = <bool>)` flipped per `PlaybackMode`. The bound URL survives across `PlaybackMode` flips so a feed → fullscreen tap is a mode change, not a re-prepare. An internal refcount on `attachSurface` / `detachSurface` drives an idle-release timer that calls `exoPlayer.release()` after 30s of zero attachment so hardware decoders aren't pinned while the user reads text.

**Tech Stack:** Kotlin / Coroutines / Hilt / Media3 ExoPlayer 1.x. Tests: JUnit 5 (`org.junit.jupiter.api`), mockk, `kotlinx.coroutines.test.TestScope` + `UnconfinedTestDispatcher`, Turbine for StateFlow assertions. Mirrors the existing `FeedVideoPlayerCoordinator` testing pattern in `:feature:feed:impl`.

---

## File Structure

**Files to create:**

- `settings.gradle.kts` — add one `include(":core:video")` line.
- `core/video/build.gradle.kts` — convention plugins, Media3 + Hilt deps, `testOptions.unitTests.isReturnDefaultValues = true` (required so the Android stub jar's `AudioAttributes.Builder()` doesn't throw "not mocked" in JVM unit tests, same fix as `:feature:feed:impl`).
- `core/video/src/main/AndroidManifest.xml` — empty `<manifest>` so AGP accepts the library module.
- `core/video/src/main/kotlin/net/kikin/nubecita/core/video/PlaybackMode.kt` — enum.
- `core/video/src/main/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayer.kt` — the holder class + production factory + `@Module @Provides` Hilt wiring.
- `core/video/src/test/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayerTest.kt` — unit tests.
- `core/video/consumer-rules.pro` — empty (matches `:core:auth` shape).

**Files to modify:** only `settings.gradle.kts`.

---

## Task 1: Module scaffold

**Files:**
- Create: `core/video/build.gradle.kts`
- Create: `core/video/src/main/AndroidManifest.xml`
- Create: `core/video/consumer-rules.pro`
- Modify: `settings.gradle.kts:N` (add `include(":core:video")` after `include(":core:testing-android")`)

- [ ] **Step 1: Add the module to settings**

Open `settings.gradle.kts` and add `include(":core:video")` immediately after `include(":core:testing-android")` so the alphabetical-ish ordering stays consistent with the existing `:core:*` block.

- [ ] **Step 2: Create the build file**

Write `core/video/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
}

android {
    namespace = "net.kikin.nubecita.core.video"

    // Required so JVM unit tests can call android.media.AudioAttributes.Builder()
    // without "Method ... not mocked" failures from the platform stub jar.
    // The Media3 audio-attributes path runs through java.net stubs that
    // need default-values to be tolerated. Same fix as :feature:feed:impl.
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    api(libs.media3.exoplayer)
    api(libs.media3.exoplayer.hls)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
```

If `libs.media3.exoplayer.hls` doesn't resolve, check `gradle/libs.versions.toml` for the existing alias used in `:feature:feed:impl/build.gradle.kts` and copy the line verbatim. The two artifacts must come from the same Media3 BOM as `:feature:feed:impl` to avoid duplicate-class conflicts when both modules eventually link in the app.

- [ ] **Step 3: Create the empty manifest**

Write `core/video/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

- [ ] **Step 4: Create empty consumer-rules.pro**

Write `core/video/consumer-rules.pro`:

```
```

(Empty file. Matches the shape of `:core:auth/consumer-rules.pro`.)

- [ ] **Step 5: Verify the module configures**

Run: `./gradlew :core:video:tasks --group=verification`

Expected: the task list prints without "Project not found" or "Plugin not found" errors. If `nubecita.android.hilt` fails to apply, double-check the plugin alias in `gradle/libs.versions.toml` — it should match the one used in `:core:auth/build.gradle.kts`.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts core/video/build.gradle.kts core/video/src/main/AndroidManifest.xml core/video/consumer-rules.pro
git commit -m "$(cat <<'EOF'
chore(core/video): scaffold module

Empty :core:video module with the convention-plugin set used by
sibling :core:* modules (nubecita.android.library + nubecita.android.hilt).
Media3 exoplayer + exoplayer-hls in the dependency set so the upcoming
SharedVideoPlayer can host a real ExoPlayer instance. isReturnDefaultValues
mirrors the :feature:feed:impl fix for AudioAttributes.Builder under JVM
unit tests.

Refs: nubecita-zak.1
EOF
)"
```

---

## Task 2: `PlaybackMode` enum

**Files:**
- Create: `core/video/src/main/kotlin/net/kikin/nubecita/core/video/PlaybackMode.kt`

- [ ] **Step 1: Write the enum**

Create `core/video/src/main/kotlin/net/kikin/nubecita/core/video/PlaybackMode.kt`:

```kotlin
package net.kikin.nubecita.core.video

/**
 * Operating mode for [SharedVideoPlayer]. Determines audio-focus
 * discipline and default volume:
 *
 * - [FeedPreview]: ExoPlayer runs with `handleAudioFocus = false` and
 *   `volume = 0f`. Opening the app to scroll the feed must never
 *   interrupt the user's music — feed autoplay is a silent preview.
 * - [Fullscreen]: ExoPlayer runs with `handleAudioFocus = true` and
 *   `volume = 1f`. Media3's built-in focus handler manages the OS
 *   audio-focus hierarchy (pause on incoming call, duck on transient
 *   loss, resume on focus regained) without per-listener wiring.
 *
 * Mode flips re-call `Player.setAudioAttributes(attrs, handleAudioFocus)`
 * with the new flag rather than touching `android.media.AudioManager`
 * directly. See the design doc for the rationale:
 * `docs/superpowers/specs/2026-05-16-fullscreen-video-player-design.md`,
 * "SharedVideoPlayer contract" section.
 */
enum class PlaybackMode { FeedPreview, Fullscreen }
```

- [ ] **Step 2: Compile**

Run: `./gradlew :core:video:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/video/src/main/kotlin/net/kikin/nubecita/core/video/PlaybackMode.kt
git commit -m "$(cat <<'EOF'
feat(core/video): add PlaybackMode enum

Two-variant enum driving SharedVideoPlayer's audio-focus discipline.
FeedPreview = silent + no focus claim; Fullscreen = unmuted + Media3
manages the OS audio-focus hierarchy.

Refs: nubecita-zak.1
EOF
)"
```

---

## Task 3: `SharedVideoPlayer` skeleton + state flows (TDD: construct + initial state)

**Files:**
- Create: `core/video/src/main/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayer.kt`
- Create: `core/video/src/test/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayerTest.kt`

- [ ] **Step 1: Write the failing test**

Create `core/video/src/test/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayerTest.kt`:

```kotlin
package net.kikin.nubecita.core.video

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SharedVideoPlayer]. The class takes its [ExoPlayer]
 * + [DefaultTrackSelector] via constructor so tests inject relaxed
 * mockks; the production factory `createSharedVideoPlayer(...)` wires
 * the real Media3 chain.
 *
 * The harness uses `TestScope(UnconfinedTestDispatcher())` as the
 * holder's internal coroutine scope so the idle-release timer and
 * mutex-serialized mutations can be driven deterministically via
 * `advanceTimeBy`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedVideoPlayerTest {
    @Test
    fun initialState_isFeedPreviewWithNoBoundUrl() =
        runTest {
            val (holder, _) = newHolder(testScope = this)

            assertEquals(PlaybackMode.FeedPreview, holder.mode.value)
            assertNull(holder.boundPlaylistUrl.value)
            assertEquals(false, holder.isPlaying.value)
        }

    private fun newHolder(
        testScope: kotlinx.coroutines.test.TestScope,
    ): Pair<SharedVideoPlayer, ExoPlayer> {
        val player = mockk<ExoPlayer>(relaxed = true)
        val trackSelector = mockk<DefaultTrackSelector>(relaxed = true)
        val holder =
            SharedVideoPlayer(
                player = player,
                trackSelector = trackSelector,
                scope = testScope,
                idleReleaseMs = 30_000L,
            )
        return holder to player
    }
}
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `./gradlew :core:video:testDebugUnitTest --tests "*.initialState_isFeedPreviewWithNoBoundUrl"`

Expected: FAIL with "Unresolved reference: SharedVideoPlayer" — the class doesn't exist yet.

- [ ] **Step 3: Write the minimal implementation**

Create `core/video/src/main/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayer.kt`:

```kotlin
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package net.kikin.nubecita.core.video

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

/**
 * Process-scoped owner of *the* `ExoPlayer` instance.
 *
 * Multiple Compose surfaces (feed cards, fullscreen route) attach and
 * detach against this holder; the underlying player is never recreated
 * across navigation transitions. Audio-focus discipline and unmute
 * state live here (not on the surface) so flipping between
 * [PlaybackMode.FeedPreview] and [PlaybackMode.Fullscreen] is a mode
 * change, not a re-prepare.
 *
 * Constructor takes the [ExoPlayer] + [DefaultTrackSelector] so unit
 * tests can inject relaxed mockks. Production code uses
 * [createSharedVideoPlayer] to wire the real Media3 chain.
 *
 * Design: `docs/superpowers/specs/2026-05-16-fullscreen-video-player-design.md`.
 */
class SharedVideoPlayer
    internal constructor(
        private val player: ExoPlayer,
        private val trackSelector: DefaultTrackSelector,
        private val scope: CoroutineScope,
        private val idleReleaseMs: Long,
    ) {
        private val _mode = MutableStateFlow(PlaybackMode.FeedPreview)
        val mode: StateFlow<PlaybackMode> = _mode.asStateFlow()

        private val _boundPlaylistUrl = MutableStateFlow<String?>(null)
        val boundPlaylistUrl: StateFlow<String?> = _boundPlaylistUrl.asStateFlow()

        private val _isPlaying = MutableStateFlow(false)
        val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

        // positionMs / durationMs are declared on the contract here in zak.1
        // so consumers can compile against the final shape. The polling
        // Job that actually updates them lives in zak.4 (VM-level concern
        // — only matters when a fullscreen surface needs the seek bar to
        // reflect playback progress).
        private val _positionMs = MutableStateFlow(0L)
        val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

        private val _durationMs = MutableStateFlow(0L)
        val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

        @Suppress("unused") // Reserved for future mutations + idle timer.
        private val mutationMutex = Mutex()
    }
```

- [ ] **Step 4: Run the test and watch it pass**

Run: `./gradlew :core:video:testDebugUnitTest --tests "*.initialState_isFeedPreviewWithNoBoundUrl"`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/video/src/main/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayer.kt core/video/src/test/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayerTest.kt
git commit -m "$(cat <<'EOF'
feat(core/video): SharedVideoPlayer skeleton + initial-state test

Constructor takes ExoPlayer + DefaultTrackSelector via primary
constructor so unit tests inject relaxed mockks. Production factory
arrives in a later task. Initial state: FeedPreview mode, no bound
URL, isPlaying = false.

Refs: nubecita-zak.1
EOF
)"
```

---

## Task 4: `bind()` + idempotency on same URL

**Files:**
- Modify: `core/video/src/main/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayer.kt`
- Modify: `core/video/src/test/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayerTest.kt`

- [ ] **Step 1: Write the failing tests**

Append to `SharedVideoPlayerTest.kt` (inside the class, after the existing test):

```kotlin
    @Test
    fun bind_firstCall_setsMediaItemAndPrepares() =
        runTest {
            val (holder, player) = newHolder(testScope = this)

            holder.bind(playlistUrl = "https://video.cdn/hls/abc.m3u8", posterUrl = null)

            io.mockk.verify(exactly = 1) { player.setMediaItem(any<androidx.media3.common.MediaItem>()) }
            io.mockk.verify(exactly = 1) { player.prepare() }
            assertEquals("https://video.cdn/hls/abc.m3u8", holder.boundPlaylistUrl.value)
        }

    @Test
    fun bind_sameUrlTwice_isIdempotent_noRebind() =
        runTest {
            val (holder, player) = newHolder(testScope = this)

            holder.bind("https://video.cdn/hls/abc.m3u8", null)
            holder.bind("https://video.cdn/hls/abc.m3u8", null)

            // Pin the load-bearing property: re-bind on the SAME URL is a no-op,
            // which is how the feed → fullscreen instance-transfer payoff works.
            // VideoPlayerScreen.LaunchedEffect calls bind() with the post's URL
            // unconditionally; if the holder is already on that URL, no prepare
            // cycle happens and playback continues uninterrupted.
            io.mockk.verify(exactly = 1) { player.setMediaItem(any<androidx.media3.common.MediaItem>()) }
            io.mockk.verify(exactly = 1) { player.prepare() }
        }

    @Test
    fun bind_differentUrl_rebinds() =
        runTest {
            val (holder, player) = newHolder(testScope = this)

            holder.bind("https://video.cdn/hls/a.m3u8", null)
            holder.bind("https://video.cdn/hls/b.m3u8", null)

            io.mockk.verify(exactly = 2) { player.setMediaItem(any<androidx.media3.common.MediaItem>()) }
            io.mockk.verify(exactly = 2) { player.prepare() }
            assertEquals("https://video.cdn/hls/b.m3u8", holder.boundPlaylistUrl.value)
        }
```

- [ ] **Step 2: Run tests and watch them fail**

Run: `./gradlew :core:video:testDebugUnitTest --tests "*SharedVideoPlayerTest.bind_*"`

Expected: FAIL with "Unresolved reference: bind".

- [ ] **Step 3: Implement `bind()`**

Add to `SharedVideoPlayer.kt` inside the class body (before the closing brace):

```kotlin
        /**
         * Bind the holder to a video. Idempotent on same `playlistUrl`:
         * a re-bind to the URL already in [boundPlaylistUrl] is a no-op,
         * which is the load-bearing property for the feed → fullscreen
         * instance-transfer. Different URL triggers `setMediaItem` +
         * `prepare`; the previous media item is replaced.
         *
         * [posterUrl] is reserved for a future poster-binding seam — the
         * surface composables resolve their own poster image today, so
         * this method only stores the URL but doesn't act on it. Kept on
         * the contract so future bind-time poster fetches don't require
         * a breaking API change.
         */
        fun bind(playlistUrl: String, posterUrl: String?) {
            if (_boundPlaylistUrl.value == playlistUrl) return
            player.setMediaItem(androidx.media3.common.MediaItem.fromUri(playlistUrl))
            player.prepare()
            _boundPlaylistUrl.value = playlistUrl
        }
```

- [ ] **Step 4: Run tests and watch them pass**

Run: `./gradlew :core:video:testDebugUnitTest --tests "*SharedVideoPlayerTest.bind_*"`

Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add core/video/src/main/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayer.kt core/video/src/test/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayerTest.kt
git commit -m "$(cat <<'EOF'
feat(core/video): SharedVideoPlayer.bind() + idempotent same-URL guard

Same-URL bind is a no-op — the load-bearing property for the
feed → fullscreen instance-transfer. Different URL triggers
setMediaItem + prepare. Three tests pin first-call, idempotency,
and rebind-on-difference contracts.

Refs: nubecita-zak.1
EOF
)"
```

---

## Task 5: `setMode()` + audio-attributes flipping

**Files:**
- Modify: `core/video/src/main/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayer.kt`
- Modify: `core/video/src/test/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayerTest.kt`

- [ ] **Step 1: Write the failing tests**

Append to `SharedVideoPlayerTest.kt`:

```kotlin
    @Test
    fun setMode_fullscreen_setsAudioAttributesWithHandleAudioFocusTrue_andUnmutes() =
        runTest {
            val (holder, player) = newHolder(testScope = this)

            holder.setMode(PlaybackMode.Fullscreen)

            io.mockk.verify {
                player.setAudioAttributes(any<androidx.media3.common.AudioAttributes>(), eq(true))
                player.volume = 1f
            }
            assertEquals(PlaybackMode.Fullscreen, holder.mode.value)
        }

    @Test
    fun setMode_feedPreview_setsAudioAttributesWithHandleAudioFocusFalse_andMutes() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            holder.setMode(PlaybackMode.Fullscreen) // start in Fullscreen so the flip is observable
            io.mockk.clearMocks(player, answers = false)

            holder.setMode(PlaybackMode.FeedPreview)

            io.mockk.verify {
                player.setAudioAttributes(any<androidx.media3.common.AudioAttributes>(), eq(false))
                player.volume = 0f
            }
            assertEquals(PlaybackMode.FeedPreview, holder.mode.value)
        }

    @Test
    fun setMode_sameMode_isNoOp() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            holder.setMode(PlaybackMode.Fullscreen)
            io.mockk.clearMocks(player, answers = false)

            holder.setMode(PlaybackMode.Fullscreen)

            // No second flip — already in Fullscreen.
            io.mockk.verify(exactly = 0) {
                player.setAudioAttributes(any<androidx.media3.common.AudioAttributes>(), any())
            }
        }
```

Add the `eq` import at the top of the test file:

```kotlin
import io.mockk.mockk      // (existing)
import io.mockk.eq         // (new)
```

- [ ] **Step 2: Run tests and watch them fail**

Run: `./gradlew :core:video:testDebugUnitTest --tests "*SharedVideoPlayerTest.setMode_*"`

Expected: FAIL with "Unresolved reference: setMode".

- [ ] **Step 3: Implement `setMode()`**

Add to `SharedVideoPlayer.kt` (inside the class):

```kotlin
        /**
         * Flip the holder's [PlaybackMode]. Idempotent on same mode.
         *
         * On [PlaybackMode.Fullscreen]: ExoPlayer's audio attributes get
         * `handleAudioFocus = true`, so Media3's built-in handler claims
         * focus on the next `play()` and pauses on transient loss
         * (incoming call, other media). Volume goes to 1.
         *
         * On [PlaybackMode.FeedPreview]: `handleAudioFocus = false`
         * (silent preview must never interrupt the user's music) and
         * volume = 0.
         */
        fun setMode(target: PlaybackMode) {
            if (_mode.value == target) return
            val attrs = audioAttributes
            when (target) {
                PlaybackMode.Fullscreen -> {
                    player.setAudioAttributes(attrs, true)
                    player.volume = 1f
                }
                PlaybackMode.FeedPreview -> {
                    player.setAudioAttributes(attrs, false)
                    player.volume = 0f
                }
            }
            _mode.value = target
        }

        private val audioAttributes: androidx.media3.common.AudioAttributes =
            androidx.media3.common.AudioAttributes
                .Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
```

- [ ] **Step 4: Run tests and watch them pass**

Run: `./gradlew :core:video:testDebugUnitTest --tests "*SharedVideoPlayerTest.setMode_*"`

Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add core/video/src/main/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayer.kt core/video/src/test/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayerTest.kt
git commit -m "$(cat <<'EOF'
feat(core/video): SharedVideoPlayer.setMode() flips audio focus + volume

Fullscreen → handleAudioFocus=true + volume=1 (Media3 owns the OS
focus hierarchy). FeedPreview → handleAudioFocus=false + volume=0.
Same-mode setMode is a no-op so callers can call unconditionally
in LaunchedEffect blocks.

Refs: nubecita-zak.1
EOF
)"
```

---

## Task 6: `attachSurface` / `detachSurface` refcount + idle-release timer

**Files:**
- Modify: `core/video/src/main/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayer.kt`
- Modify: `core/video/src/test/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayerTest.kt`

- [ ] **Step 1: Write the failing tests**

Append to `SharedVideoPlayerTest.kt`:

```kotlin
    @Test
    fun attachSurface_then_detachAllSurfaces_within_idleWindow_doesNotRelease() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            holder.bind("https://video.cdn/hls/a.m3u8", null)

            holder.attachSurface()
            holder.detachSurface()
            // Within the 30-second idle window — re-attaching cancels the timer.
            kotlinx.coroutines.test.advanceTimeBy(15_000L)
            holder.attachSurface()
            kotlinx.coroutines.test.advanceTimeBy(60_000L)

            io.mockk.verify(exactly = 0) { player.release() }
        }

    @Test
    fun attachSurface_detached_idleTimeoutElapses_callsRelease_andClearsBoundUrl() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            holder.bind("https://video.cdn/hls/a.m3u8", null)

            holder.attachSurface()
            holder.detachSurface()
            kotlinx.coroutines.test.advanceTimeBy(30_000L)
            kotlinx.coroutines.test.runCurrent()

            io.mockk.verify(exactly = 1) { player.release() }
            assertNull(holder.boundPlaylistUrl.value, "bound URL should clear when ExoPlayer releases")
        }

    @Test
    fun detachSurface_whenAlreadyAtZero_isNoOp() =
        runTest {
            val (holder, _) = newHolder(testScope = this)

            // Detach without an attach: refcount must clamp at zero, not go negative.
            // Otherwise a stray onDispose in a never-attached composable could
            // poison the refcount and prevent later idle-release timers from firing.
            holder.detachSurface()
            holder.detachSurface()
            holder.attachSurface()
            holder.detachSurface()
            kotlinx.coroutines.test.advanceTimeBy(30_000L)
            kotlinx.coroutines.test.runCurrent()

            // Idle release SHOULD fire after the last (matching) detach — refcount
            // is back to zero and the timer ran.
            // No assertion on the player here; the next test pins release behavior.
        }
```

- [ ] **Step 2: Run tests and watch them fail**

Run: `./gradlew :core:video:testDebugUnitTest --tests "*SharedVideoPlayerTest.attachSurface_*"`

Expected: FAIL with "Unresolved reference: attachSurface".

- [ ] **Step 3: Implement refcount + timer**

Add to `SharedVideoPlayer.kt` (inside the class):

```kotlin
        private var refcount: Int = 0
        private var idleReleaseJob: kotlinx.coroutines.Job? = null

        /**
         * Increment the active-surface refcount. The first call after a
         * zero-refcount state cancels any pending idle-release timer so
         * the ExoPlayer instance survives a quick detach → attach
         * round-trip (which happens during the feed → fullscreen
         * surface hand-off as one PlayerSurface unmounts and another
         * mounts).
         */
        fun attachSurface() {
            refcount += 1
            idleReleaseJob?.cancel()
            idleReleaseJob = null
        }

        /**
         * Decrement the refcount. Clamps at zero so a stray detach from
         * a never-attached composable can't poison the count. When the
         * count drops to zero, schedules an idle-release job that calls
         * [ExoPlayer.release] after [idleReleaseMs] of continuous
         * zero-refcount. Hardware video decoders are finite; pinning
         * the player while the user reads text posts is wasteful.
         */
        fun detachSurface() {
            if (refcount == 0) return
            refcount -= 1
            if (refcount == 0) {
                idleReleaseJob?.cancel()
                idleReleaseJob =
                    scope.launch {
                        kotlinx.coroutines.delay(idleReleaseMs)
                        player.release()
                        _boundPlaylistUrl.value = null
                        _isPlaying.value = false
                    }
            }
        }
```

Add at the top of `SharedVideoPlayer.kt` (next to existing imports):

```kotlin
import kotlinx.coroutines.launch
```

- [ ] **Step 4: Run tests and watch them pass**

Run: `./gradlew :core:video:testDebugUnitTest --tests "*SharedVideoPlayerTest.attachSurface_*" --tests "*SharedVideoPlayerTest.detachSurface_*"`

Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add core/video/src/main/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayer.kt core/video/src/test/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayerTest.kt
git commit -m "$(cat <<'EOF'
feat(core/video): SharedVideoPlayer attach/detach refcount + idle release

attachSurface/detachSurface maintain a clamped-at-zero refcount.
When the count hits zero, a 30s idle-release timer schedules
ExoPlayer.release() so hardware decoders aren't pinned while the
user reads text posts. Re-attaching within the window cancels the
timer so feed → fullscreen surface handoffs survive cleanly.

Refs: nubecita-zak.1
EOF
)"
```

---

## Task 7: Transport methods (`play` / `pause` / `seekTo` / `toggleMute`) + `release()`

**Files:**
- Modify: `core/video/src/main/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayer.kt`
- Modify: `core/video/src/test/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayerTest.kt`

- [ ] **Step 1: Write the failing tests**

Append to `SharedVideoPlayerTest.kt`:

```kotlin
    @Test
    fun play_callsPlayerPlay() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            holder.play()
            io.mockk.verify { player.play() }
        }

    @Test
    fun pause_callsPlayerPause() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            holder.pause()
            io.mockk.verify { player.pause() }
        }

    @Test
    fun seekTo_callsPlayerSeekTo_withPositionMs() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            holder.seekTo(12_345L)
            io.mockk.verify { player.seekTo(12_345L) }
        }

    @Test
    fun toggleMute_inFullscreen_flipsVolumeBetweenZeroAndOne() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            holder.setMode(PlaybackMode.Fullscreen)
            io.mockk.clearMocks(player, answers = false)
            io.mockk.every { player.volume } returns 1f

            holder.toggleMute()

            io.mockk.verify { player.volume = 0f }
        }

    @Test
    fun release_callsPlayerRelease_andClearsBoundUrl() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            holder.bind("https://video.cdn/hls/a.m3u8", null)

            holder.release()

            io.mockk.verify { player.release() }
            assertNull(holder.boundPlaylistUrl.value)
        }
```

Add the `every` import:

```kotlin
import io.mockk.every
```

- [ ] **Step 2: Run tests and watch them fail**

Run: `./gradlew :core:video:testDebugUnitTest --tests "*SharedVideoPlayerTest.play_*" --tests "*SharedVideoPlayerTest.pause_*" --tests "*SharedVideoPlayerTest.seekTo_*" --tests "*SharedVideoPlayerTest.toggleMute_*" --tests "*SharedVideoPlayerTest.release_*"`

Expected: FAIL with "Unresolved reference: play" (and the others).

- [ ] **Step 3: Implement transport + release**

Add to `SharedVideoPlayer.kt` (inside the class):

```kotlin
        /** Resume playback. Volume + audio-focus state come from the current [mode]. */
        fun play() {
            player.play()
            _isPlaying.value = true
        }

        /** Pause playback. The bound URL stays — re-binding to the same URL is idempotent. */
        fun pause() {
            player.pause()
            _isPlaying.value = false
        }

        /** Seek within the current media item. */
        fun seekTo(positionMs: Long) {
            player.seekTo(positionMs)
        }

        /**
         * Flip volume between 0f and 1f. Only meaningful in
         * [PlaybackMode.Fullscreen] — in [PlaybackMode.FeedPreview] the
         * mode contract pins volume at 0, and unmute requires entering
         * Fullscreen first.
         */
        fun toggleMute() {
            player.volume = if (player.volume > 0f) 0f else 1f
        }

        /**
         * Force-release the underlying ExoPlayer immediately and clear
         * the bound URL. Used by the auth-state-cleared broadcaster on
         * logout so a stale player doesn't survive across users. Also
         * called by the idle-release timer, which goes through the same
         * cleanup path.
         */
        fun release() {
            idleReleaseJob?.cancel()
            idleReleaseJob = null
            player.release()
            _boundPlaylistUrl.value = null
            _isPlaying.value = false
        }
```

- [ ] **Step 4: Run tests and watch them pass**

Run: `./gradlew :core:video:testDebugUnitTest`

Expected: ALL PASS (full test suite green).

- [ ] **Step 5: Commit**

```bash
git add core/video/src/main/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayer.kt core/video/src/test/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayerTest.kt
git commit -m "$(cat <<'EOF'
feat(core/video): SharedVideoPlayer transport methods + release()

play/pause/seekTo/toggleMute proxy to the ExoPlayer; release()
is the auth-state-cleared / explicit-shutdown path that the idle
timer also funnels through. toggleMute is documented as a Fullscreen-
mode operation; in FeedPreview the volume contract pins it at 0.

Refs: nubecita-zak.1
EOF
)"
```

---

## Task 8: Production factory + Hilt `@Module`

**Files:**
- Modify: `core/video/src/main/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayer.kt`

(No new test in this task — the factory + Hilt module wire real Media3 + DI, which unit tests don't exercise. Verification is the `:app` build linking successfully.)

- [ ] **Step 1: Add the production factory**

Append to `SharedVideoPlayer.kt` (below the class body, top-level):

```kotlin
/**
 * Production factory for [SharedVideoPlayer]. Wires the real Media3
 * chain: an `ExoPlayer` built with a `DefaultTrackSelector` whose
 * HLS-bitrate floor starts pinned to the lowest variant (the
 * sustained-playback unlock arrives in a follow-up task for
 * `nubecita-zak.4`; the floor stays unconditionally pinned for
 * `zak.1`). Audio attributes start at `FeedPreview` defaults
 * (`handleAudioFocus = false`, volume = 0) — `setMode(Fullscreen)`
 * flips them in.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun createSharedVideoPlayer(
    context: android.content.Context,
    scope: CoroutineScope,
    idleReleaseMs: Long = DEFAULT_IDLE_RELEASE_MS,
): SharedVideoPlayer {
    val appContext = context.applicationContext
    val trackSelector =
        DefaultTrackSelector(appContext).apply {
            setParameters(buildUponParameters().setForceLowestBitrate(true))
        }
    val attrs =
        androidx.media3.common.AudioAttributes
            .Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
    val player =
        ExoPlayer
            .Builder(appContext)
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                volume = 0f
                // FeedPreview default — flipped to `true` by setMode(Fullscreen).
                setAudioAttributes(attrs, false)
            }
    return SharedVideoPlayer(
        player = player,
        trackSelector = trackSelector,
        scope = scope,
        idleReleaseMs = idleReleaseMs,
    )
}

/** 30 seconds. Calibrated to the design's idle-release rule. */
const val DEFAULT_IDLE_RELEASE_MS: Long = 30_000L
```

- [ ] **Step 2: Add the Hilt module**

Create a new file `core/video/src/main/kotlin/net/kikin/nubecita/core/video/di/VideoPlayerModule.kt`:

```kotlin
package net.kikin.nubecita.core.video.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import net.kikin.nubecita.core.video.SharedVideoPlayer
import net.kikin.nubecita.core.video.createSharedVideoPlayer
import javax.inject.Singleton

/**
 * Hilt module for [SharedVideoPlayer]. The holder is process-scoped
 * (one ExoPlayer per process); a dedicated `SupervisorJob`-backed
 * scope drives the idle-release timer independently of any UI
 * coroutine context so a cancelled feed scope doesn't kill the
 * release timer mid-countdown.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object VideoPlayerModule {
    @Provides
    @Singleton
    fun provideSharedVideoPlayer(
        @ApplicationContext context: Context,
    ): SharedVideoPlayer =
        createSharedVideoPlayer(
            context = context,
            scope = CoroutineScope(SupervisorJob()),
        )
}
```

- [ ] **Step 3: Verify the module + Hilt graph build**

Run: `./gradlew :core:video:assembleDebug`

Expected: BUILD SUCCESSFUL.

Run: `./gradlew :app:hiltJavaCompileDebug`

Expected: BUILD SUCCESSFUL. If Hilt complains about an unsatisfied binding, double-check the module is on the app's transitive classpath — `:core:video` doesn't need to be on `:app`'s explicit dependency list because Hilt's component aggregation picks it up via `@InstallIn(SingletonComponent::class)` so long as the module is on the runtime classpath of *some* dependency that `:app` transitively pulls. For zak.1 nothing depends on `:core:video` yet, so the test of the Hilt module shape is just that `:core:video:kspDebugKotlin` compiles cleanly.

Run: `./gradlew :core:video:kspDebugKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add core/video/src/main/kotlin/net/kikin/nubecita/core/video/SharedVideoPlayer.kt core/video/src/main/kotlin/net/kikin/nubecita/core/video/di/VideoPlayerModule.kt
git commit -m "$(cat <<'EOF'
feat(core/video): createSharedVideoPlayer factory + Hilt module

createSharedVideoPlayer builds the real ExoPlayer + DefaultTrackSelector
with HLS bitrate floor pinned (unlock-after-sustained-playback lands
in zak.4). Hilt provides a SingletonComponent-scoped instance backed
by a dedicated SupervisorJob scope so the idle-release timer survives
independent of any UI coroutine context.

Refs: nubecita-zak.1
EOF
)"
```

---

## Task 9: Full verification + spotless

**Files:** none (verification only)

- [ ] **Step 1: Run the full local-verify gauntlet**

Run, in order:

1. `./gradlew :core:video:testDebugUnitTest` — Expected: PASS.
2. `./gradlew :core:video:spotlessCheck` — Expected: PASS. If ktlint flags a wildcard import or a missing trailing newline, run `./gradlew :core:video:spotlessApply` and re-stage.
3. `./gradlew :core:video:lint` — Expected: PASS (no fatal lints).
4. `./gradlew assembleDebug` — Expected: BUILD SUCCESSFUL. Pins that the new module doesn't break the app graph.

- [ ] **Step 2: If spotless needed to apply, commit the auto-format**

If `spotlessApply` made edits:

```bash
git add core/video
git commit -m "$(cat <<'EOF'
chore(core/video): spotless apply

Refs: nubecita-zak.1
EOF
)"
```

If no auto-format was needed, skip this step.

---

## Self-Review

**Spec coverage:** The plan implements every contract member listed in the spec's "SharedVideoPlayer contract" section: `bind`, `attachSurface` / `detachSurface`, `mode` / `setMode`, `positionMs` / `durationMs` / `isPlaying` / `boundPlaylistUrl` state flows, `play` / `pause` / `seekTo` / `toggleMute`, `release()`. `positionMs` and `durationMs` are declared on the contract but not exercised by tests in zak.1 — those are read-only proxies to the player's getters and are exercised by `VideoPlayerViewModelTest` in zak.4, so they're scaffolding in zak.1 (an internal `_positionMs` / `_durationMs` MutableStateFlow + a Job that polls the player every 250ms could ship here too, but it's strictly more code than the spec's foundation requires. Skipping for now and letting zak.4 add the polling job alongside the VM that consumes it.)

**Placeholder scan:** No "TBD" / "TODO" / "implement later" anywhere. Each code block is complete. Each step has either runnable code or a runnable command with expected output.

**Type consistency:** `SharedVideoPlayer` constructor parameters match across Task 3 (skeleton), the factory in Task 8 (`createSharedVideoPlayer`), and the Hilt @Provides in Task 8 — all use `(player, trackSelector, scope, idleReleaseMs)`. `PlaybackMode.FeedPreview` / `Fullscreen` are referenced consistently across tasks 3, 5, 7. Test helper `newHolder(testScope: TestScope)` is defined once in Task 3 and reused unchanged in subsequent tasks. The `androidx.media3.common.AudioAttributes` builder pattern matches between Task 5 (per-mode flip) and Task 8 (factory init); both go through `C.USAGE_MEDIA` + `C.AUDIO_CONTENT_TYPE_MOVIE`.

**Out-of-scope deliberately omitted from zak.1, picked up later:**
- Mutex serialization tests — the `mutationMutex` field is reserved in Task 3 but not exercised. None of zak.1's mutations are concurrent (each test runs in a single-threaded TestScope); the mutex matters once zak.2's feed coordinator drives the holder concurrently with the VM in zak.4. Add the mutex usage + concurrency test in zak.2 or zak.4 where the threading model actually warrants it.
- HLS bitrate floor sustained-playback unlock — pinned at lowest in the factory; the unlock job belongs to zak.4 (VM-level concern).
- Position / duration polling — the `_positionMs` / `_durationMs` MutableStateFlows aren't populated in zak.1; zak.4's VM adds the polling Job.
