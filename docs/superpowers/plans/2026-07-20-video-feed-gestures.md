# Video Feed Gestures & Looping (Slice 3b, PR3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the vertical video feed's clips loop, let a tap pause and resume playback, and let a double tap like the post.

**Architecture:** `VerticalVideoPlaylistPlayer` gains looping (`REPEAT_MODE_ONE` on every pooled player) and a `setPaused` control mirroring its existing `setMuted`. `VideoFeedViewModel` holds a flat `isPaused` flag that resets on page change, and routes a double tap to the already-delegated `onLike`. The page composable arbitrates both gestures with a single `detectTapGestures`.

**Tech Stack:** Kotlin 2.3, Jetpack Compose, Media3 1.10.1 (ExoPlayer), Hilt, JUnit Jupiter, MockK, Turbine.

**Spec:** `docs/superpowers/specs/2026-07-19-video-feed-chrome-design.md` — decision **D7** (gesture arbitration). PR1 (#771) and PR2 (#772) merged. **D8 (progress bar) is explicitly OUT of scope** — deferred to `nubecita-zdv8.14`.

## Global Constraints

- Modules: `:core:video` (pool) and `:feature:videos:impl` (VM + UI).
- **MVI:** flat state. `isPaused` is a flat `Boolean` on `VideoFeedState` — it is independent of the load lifecycle, so it does not belong in `VideoFeedStatus`.
- **D5 delegation still applies:** the double tap calls `viewModel.onLike(post)`, which is delegation-forwarded to the handler. Do **not** add a bespoke like path.
- No Kotlin `!!`. JUnit **Jupiter**. ktlint via Spotless before every commit.
- Any new `<string>` needs `values-b+es+419` **and** `values-pt-rBR` entries, or CI `Lint / android` fails `MissingTranslation`. `:app` lint does not catch it — run `./gradlew :feature:videos:impl:lintDebug`.
- New `testTag` values are pinned in `VideoFeedTestTagsTest`.
- Conventional Commits; footer `Refs: nubecita-zdv8.9`. **`Closes:` goes in the PR body**, since PR3 completes Slice 3b.
- Branch `feat/nubecita-zdv8.9-video-feed-gestures` already exists off fresh `main`. Never commit to `main`.
- **Every behaviour here must be verified on the plugged-in Pixel Fold** (serial `37201FDHS002UN`). If a bench fixture cannot exercise something, extend the fixture — "not verifiable on bench" is not an acceptable outcome.

## Verified Facts

Confirmed against current `main`. Use these exactly.

| Thing | Fact |
|---|---|
| Pool API | `bind`, `onActiveIndexChanged`, `onStart`, `onStop`, `setMuted(Boolean)`, `setPrewarmEnabled(Boolean)`, `release()`. **No play/pause control exists** — this plan adds one. |
| `settle()` | Ends with `activeSlot.player.volume = …; activeSlot.player.play(); _activePlayer.value = activeSlot.player`. Non-active slots are paused via `slots.forEach { if (it !== activeSlot) it.player.pause() }`. |
| Looping | **No `repeatMode` is set anywhere in the pool**, so it inherits ExoPlayer's `REPEAT_MODE_OFF` — clips play once and freeze. `SharedVideoPlayer` by contrast sets `REPEAT_MODE_ONE` for `FeedPreview` and `OFF` for `Fullscreen` (`SharedVideoPlayer.kt:209-212`). |
| `PlaylistPlaybackState` | `Idle`, `Buffering`, `Playing`, `Error(cause)`. **No `Paused` variant** — pause is user intent, tracked in `VideoFeedState`, not derived from player events. |
| Pool test harness | `VerticalVideoPlaylistPlayerTest` uses `playerProvider = { newPlayer() }` returning `mockk<ExoPlayer>(relaxed = true)`, collected into a `created` list; assertions use `verify { }`. |
| Bench clips | `app/src/bench/assets/video/clip-{1,2,3}.mp4` — **14–15s, all landscape** (1280x720, 1694x720, 1728x720). `FakeVideoFeedSource` declares `durationSeconds = 8`, which is wrong and should be corrected to the real durations. |
| Existing state | `VideoFeedState(status, activeIndex, isMuted)`; `VideoFeedEvent` has `ActiveIndexChanged`, `ToggleMute`, `Retry`, `AuthorTapped`, `PostTapped`. |

## File Structure

| File | Responsibility |
|---|---|
| `core/video/.../VerticalVideoPlaylistPlayer.kt` | **Modify.** `REPEAT_MODE_ONE` on created players; `setPaused(Boolean)`; `settle()` honours it. |
| `core/video/src/test/.../VerticalVideoPlaylistPlayerTest.kt` | **Modify.** Loop + pause tests. |
| `core/video-feed/src/bench/.../FakeVideoFeedSource.kt` | **Modify.** Correct `durationSeconds` to the real clip lengths. |
| `.../impl/VideoFeedContract.kt` | **Modify.** `isPaused` on state; `TogglePlayPause`, `DoubleTapLike` events. |
| `.../impl/VideoFeedViewModel.kt` | **Modify.** Pause toggle + reset on page change; double-tap → `onLike`. |
| `.../impl/ui/VideoFeedPage.kt` | **Modify.** `detectTapGestures` + paused indicator overlay. |
| `.../impl/VideoFeedScreen.kt` | **Modify.** Wire the two gesture callbacks. |
| `.../impl/VideoFeedTestTags.kt` (+ test) | **Modify.** `PAUSE_INDICATOR` tag. |
| `res/values{,-b+es+419,-pt-rBR}/strings.xml` | **Modify.** Play/pause content descriptions. |

---

### Task 1: Pool — looping and pause control

**Files:**
- Modify: `core/video/src/main/kotlin/net/kikin/nubecita/core/video/playback/VerticalVideoPlaylistPlayer.kt`
- Test: `core/video/src/test/kotlin/net/kikin/nubecita/core/video/playback/VerticalVideoPlaylistPlayerTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `public fun setPaused(paused: Boolean)` on the pool.

- [ ] **Step 1: Write the failing tests**

Append to `VerticalVideoPlaylistPlayerTest`, matching the file's existing harness style:

```kotlin
    @Test
    fun bind_setsRepeatModeOne_soClipsLoop() =
        runTest {
            // A reels-style feed loops; without this the clip plays once and freezes
            // on its last frame, which reads as a broken page.
            val pool = pool()
            pool.bind(sources(2), startIndex = 0)

            created.forEach { verify { it.repeatMode = Player.REPEAT_MODE_ONE } }
        }

    @Test
    fun setPaused_pausesActivePlayer_andResumePlaysIt() =
        runTest {
            val pool = pool()
            pool.bind(sources(2), startIndex = 0)
            val active = created.first()

            pool.setPaused(true)
            verify { active.pause() }

            pool.setPaused(false)
            verify(atLeast = 2) { active.play() } // once on settle, once on resume
        }

    @Test
    fun swipe_clearsPause_soANewPageAutoPlays() =
        runTest {
            // Pause is per-page intent, like every comparable product: swiping to the
            // next clip starts it playing rather than inheriting the paused state.
            val pool = pool()
            pool.bind(sources(3), startIndex = 0)
            pool.setPaused(true)

            pool.onActiveIndexChanged(1)

            val promoted = created[1]
            verify { promoted.play() }
        }
```

Add `import androidx.media3.common.Player` if absent.

- [ ] **Step 2: Run them to verify they fail**

```bash
./gradlew :core:video:testDebugUnitTest --tests '*VerticalVideoPlaylistPlayerTest*'
```

Expected: FAIL — `Unresolved reference: setPaused`.

- [ ] **Step 3: Implement**

In `VerticalVideoPlaylistPlayer`, add a field beside the existing `muted`:

```kotlin
    private var paused = false
```

Where a player is created in `settle()` (`val player = playerProvider()`), set the repeat mode immediately after creation:

```kotlin
            // Reels-style loop. Without it a clip plays once and freezes on its last
            // frame — the vertical feed has no "next" to advance to on its own.
            player.repeatMode = Player.REPEAT_MODE_ONE
```

Add the control, mirroring `setMuted`:

```kotlin
    /**
     * Pause/resume the active playback. Prewarmed slots stay paused regardless.
     * Must be called on the main thread (the UI already is).
     *
     * This is per-page intent: [settle] clears it, so swiping to another clip
     * starts playing rather than inheriting a pause from the previous page.
     */
    public fun setPaused(paused: Boolean) {
        this.paused = paused
        val active = slots.firstOrNull { it.index == activeIndex }?.player ?: return
        if (paused) active.pause() else active.play()
    }
```

In `settle()`, clear the flag and honour it at the point that currently calls `play()` unconditionally:

```kotlin
        paused = false
        activeSlot.player.play()
```

- [ ] **Step 4: Run them to verify they pass**

```bash
./gradlew :core:video:testDebugUnitTest --tests '*VerticalVideoPlaylistPlayerTest*'
```

Expected: PASS.

- [ ] **Step 5: Correct the bench clip durations**

In `core/video-feed/src/bench/kotlin/net/kikin/nubecita/core/videofeed/FakeVideoFeedSource.kt`, `durationSeconds = 8` is wrong — the bundled clips are 15s, 14s and 15s. Set the real per-clip duration. Nothing reads it today, but a fixture that lies is a trap for `nubecita-zdv8.14` (the progress bar), which must read `player.duration` precisely because metadata can be wrong.

- [ ] **Step 6: Commit**

```bash
./gradlew :core:video:spotlessApply -q
git add core/video core/video-feed
git commit -m "feat(video): loop vertical-feed clips and add pause control

The pool set no repeatMode, so it inherited REPEAT_MODE_OFF and every
clip played once then froze on its last frame. Pooled players now loop.

Adds setPaused mirroring setMuted, cleared by settle so pause is per-page
intent rather than something a swipe inherits.

Refs: nubecita-zdv8.9"
```

---

### Task 2: ViewModel — pause state and double-tap like

**Files:**
- Modify: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/VideoFeedContract.kt`
- Modify: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/VideoFeedViewModel.kt`
- Test: `feature/videos/impl/src/test/kotlin/net/kikin/nubecita/feature/videos/impl/VideoFeedViewModelTest.kt`

**Interfaces:**
- Consumes: `pool.setPaused(Boolean)` (Task 1).
- Produces: `VideoFeedState.isPaused: Boolean`; `VideoFeedEvent.TogglePlayPause`, `VideoFeedEvent.DoubleTapLike(post: PostUi)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
    @Test
    fun togglePlayPause_flipsState_andDrivesPool() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(listOf(videoPost("a")), cursor = null))
            val viewModel = vm()
            advanceUntilIdle()

            viewModel.handleEvent(VideoFeedEvent.TogglePlayPause)
            assertTrue(viewModel.uiState.value.isPaused)
            verify { pool.setPaused(true) }

            viewModel.handleEvent(VideoFeedEvent.TogglePlayPause)
            assertFalse(viewModel.uiState.value.isPaused)
            verify { pool.setPaused(false) }
        }

    @Test
    fun swipingToANewPage_clearsPaused() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(List(3) { videoPost("v$it") }, cursor = null))
            val viewModel = vm()
            advanceUntilIdle()
            viewModel.handleEvent(VideoFeedEvent.TogglePlayPause)
            assertTrue(viewModel.uiState.value.isPaused)

            viewModel.handleEvent(VideoFeedEvent.ActiveIndexChanged(1))
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isPaused)
        }

    @Test
    fun doubleTapLike_likesAnUnlikedPost() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(listOf(videoPost("a")), cursor = null))
            val viewModel = vm()
            advanceUntilIdle()

            viewModel.handleEvent(VideoFeedEvent.DoubleTapLike(videoPost("a")))

            verify { handler.onLike(any()) }
        }

    @Test
    fun doubleTapLike_onAnAlreadyLikedPost_doesNotUnlike() =
        runTest(mainDispatcher.dispatcher) {
            // A double tap is an affirmative "like this", never a toggle: a mistimed
            // second tap must not silently undo a like.
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(listOf(videoPost("a")), cursor = null))
            val viewModel = vm()
            advanceUntilIdle()
            val liked = videoPost("a").let { it.copy(viewer = it.viewer.copy(isLikedByViewer = true)) }

            viewModel.handleEvent(VideoFeedEvent.DoubleTapLike(liked))

            verify(exactly = 0) { handler.onLike(any()) }
        }
```

Add `import org.junit.jupiter.api.Assertions.assertFalse` if absent.

- [ ] **Step 2: Run them to verify they fail**

```bash
./gradlew :feature:videos:impl:testDebugUnitTest --tests '*VideoFeedViewModelTest*'
```

Expected: FAIL — `Unresolved reference: TogglePlayPause` / `isPaused`.

- [ ] **Step 3: Extend the contract**

In `VideoFeedContract.kt`, add to `VideoFeedState`:

```kotlin
    val isPaused: Boolean = false,
```

and to `VideoFeedEvent`:

```kotlin
    /** The page was single-tapped — toggle playback. */
    data object TogglePlayPause : VideoFeedEvent

    /**
     * The page was double-tapped. Always an affirmative like, never a toggle:
     * a mistimed second tap must not silently undo an existing like.
     */
    data class DoubleTapLike(
        val post: PostUi,
    ) : VideoFeedEvent
```

- [ ] **Step 4: Implement in the ViewModel**

Add to `handleEvent`:

```kotlin
                VideoFeedEvent.TogglePlayPause -> togglePlayPause()
                is VideoFeedEvent.DoubleTapLike ->
                    if (!event.post.viewer.isLikedByViewer) onLike(event.post)
```

and the private helper beside `toggleMute`:

```kotlin
        private fun togglePlayPause() {
            val paused = !uiState.value.isPaused
            setState { copy(isPaused = paused) }
            pool.setPaused(paused)
        }
```

In `onActiveIndexChanged`, clear the flag alongside the index update — the pool's `settle` already resumes playback, so the UI flag must not lag behind it:

```kotlin
            setState { copy(activeIndex = index, isPaused = false) }
```

- [ ] **Step 5: Run them to verify they pass**

```bash
./gradlew :feature:videos:impl:testDebugUnitTest --tests '*VideoFeedViewModelTest*'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
./gradlew :feature:videos:impl:spotlessApply -q
git add feature/videos/impl
git commit -m "feat(videos): pause state and double-tap-to-like on the vertical feed

Tap toggles playback through the pool; a swipe clears the flag so a new
page auto-plays. Double tap only ever likes, so a mistimed second tap
cannot silently undo an existing like.

Refs: nubecita-zdv8.9"
```

---

### Task 3: Gestures and the paused indicator

**Files:**
- Modify: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoFeedPage.kt`
- Modify: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/VideoFeedScreen.kt`
- Modify: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/VideoFeedTestTags.kt` (+ its test)
- Modify: `res/values{,-b+es+419,-pt-rBR}/strings.xml`
- Modify: `feature/videos/impl/src/screenshotTest/.../VideoFeedPageScreenshotTest.kt`

**Interfaces:**
- Consumes: `VideoFeedEvent.TogglePlayPause`, `VideoFeedEvent.DoubleTapLike` (Task 2).
- Produces: `VideoFeedTestTags.PAUSE_INDICATOR = "video_feed_pause_indicator"`.

- [ ] **Step 1: Pin the new test tag**

Add to `VideoFeedTestTagsTest`'s pinned assertions:

```kotlin
        assertEquals("video_feed_pause_indicator", VideoFeedTestTags.PAUSE_INDICATOR)
```

Run `./gradlew :feature:videos:impl:testDebugUnitTest --tests '*VideoFeedTestTagsTest*'` — expect FAIL, then add the constant to `VideoFeedTestTags` and expect PASS.

- [ ] **Step 2: Add the gesture layer and indicator to `VideoFeedPage`**

Add parameters `isPaused: Boolean = false`, `onTogglePlayPause: () -> Unit = {}`, `onDoubleTapLike: () -> Unit = {}`. Inside the root `Box`, **after** the poster and **before** `chrome()`, add:

```kotlin
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onDoubleTapLike() },
                        onTap = { onTogglePlayPause() },
                    )
                },
        )
        if (isPaused) {
            NubecitaIcon(
                name = NubecitaIconName.PlayArrow,
                contentDescription = stringResource(R.string.videos_paused),
                tint = Color.White.copy(alpha = PAUSE_GLYPH_ALPHA),
                opticalSize = PAUSE_GLYPH_SIZE,
                modifier = Modifier.align(Alignment.Center).testTag(VideoFeedTestTags.PAUSE_INDICATOR),
            )
        }
```

with `private val PAUSE_GLYPH_SIZE = 72.dp` and `private const val PAUSE_GLYPH_ALPHA = 0.85f`.

Two ordering notes that matter:
- The gesture Box sits **below** `chrome()`, so the rail's own clickables win over the page tap rather than being swallowed by it.
- `detectTapGestures` does not consume drags, so the pager's vertical swipe still works — verify this on device, it is the main risk in this task.

`NubecitaIconName.PlayArrow` is verified to exist (`NubecitaIconName.kt:79`; `Pause` is at `:75` if the design ever calls for the inverse). Do not add a glyph — regenerating the icon font to add one breaks baselines repo-wide.

- [ ] **Step 3: Add the strings in all three locales**

`values`: `<string name="videos_paused">Paused</string>`
`values-b+es+419`: `<string name="videos_paused">En pausa</string>`
`values-pt-rBR`: `<string name="videos_paused">Pausado</string>`

- [ ] **Step 4: Wire the screen**

In `VideoFeedScreen`'s pager page lambda, pass to `VideoFeedPage`:

```kotlin
                            isPaused = state.isPaused && isSettled,
                            onTogglePlayPause = { viewModel.handleEvent(VideoFeedEvent.TogglePlayPause) },
                            onDoubleTapLike = { viewModel.handleEvent(VideoFeedEvent.DoubleTapLike(item.post)) },
```

`&& isSettled` matters: `isPaused` is screen-level state, so without it every composed neighbouring page would also render the paused glyph.

- [ ] **Step 5: Add a screenshot preview for the paused state**

Add one preview mirroring the existing chrome previews, with `isPaused = true`, so the glyph's placement and size are pinned. Regenerate:

```bash
./gradlew :feature:videos:impl:updateScreenshots
```

**Look at the new PNG** with the Read tool, then confirm the baselines still discriminate:

```bash
shasum -a 256 feature/videos/impl/src/screenshotTestDebug/reference/**/*.png | awk '{print $1}' | sort -u | wc -l
```

This must equal the file count.

**Do not commit locally-regenerated PNGs for baselines you did not change.** Mac and Linux renders drift; `main` currently fails ~14 baselines locally on a clean tree. Commit only the genuinely new baseline, and if CI rejects it, use the `update-baselines` label rather than regenerating locally.

- [ ] **Step 6: Verify and commit**

```bash
./gradlew :feature:videos:impl:assembleDebug :feature:videos:impl:testDebugUnitTest :feature:videos:impl:lintDebug > /tmp/gate.log 2>&1; echo "EXIT=$?"; tail -3 /tmp/gate.log
```

Expected `EXIT=0`. Check the exit code explicitly — a piped gradle command's failure is otherwise invisible.

```bash
git add feature/videos/impl
git commit -m "feat(videos): tap to pause and double tap to like

detectTapGestures sits below the chrome so rail buttons win, and does not
consume drags so the pager keeps its swipe.

Refs: nubecita-zdv8.9"
```

---

### Task 4: Device verification on the Pixel Fold

This is a required gate, not a bonus. Unit tests cannot see gesture arbitration.

**Files:** none.

- [ ] **Step 1: Check devices, then install**

```bash
adb devices -l
./gradlew :app:installBenchDebug
```

`installBenchDebug` installs to **every** attached target; note the Fold's serial (`37201FDHS002UN`) and pass `-s` to every adb call. Enable DND: `adb -s 37201FDHS002UN shell cmd notification set_dnd priority`.

- [ ] **Step 2: Determine the live display**

```bash
adb -s 37201FDHS002UN shell dumpsys display | grep -E "state ON|state OFF"
```

The Fold has two displays and `screencap` corrupts its PNG unless given `-d <displayId>`. Use the one that is ON (folded ⇒ outer, 1080x2092, id `4619827677550801153`).

- [ ] **Step 3: Verify looping**

Open the feed and leave a clip running past its length (clips are 14–15s). Capture at ~5s and again at ~20s.

```bash
adb -s <serial> exec-out screencap -p -d <displayId> > /tmp/loop-a.png   # ~5s in
# wait past the clip length
adb -s <serial> exec-out screencap -p -d <displayId> > /tmp/loop-b.png   # ~20s in
```

Pass = the two frames **differ** and the later one is not a frozen last frame. Before this change the clip stopped; if it still stops, the repeat mode is not reaching the active player.

- [ ] **Step 4: Verify tap-to-pause**

Tap the centre of the video (away from the rail, which owns the right edge). Capture twice ~1.5s apart.

Pass = the two captures are **byte-identical** (playback genuinely stopped) **and** the paused glyph is visible. Tap again and confirm two captures ~1.5s apart now **differ**.

This is the inverse of the "identical hashes" trap from earlier device passes — here identical is the *expected* result, so also confirm the glyph, or a frozen surface for any other reason would read as a pass.

- [ ] **Step 5: Verify double-tap-to-like**

Resolve the like cell's bounds first so you can read its count:

```bash
adb -s <serial> shell uiautomator dump /sdcard/ui.xml
adb -s <serial> shell cat /sdcard/ui.xml | tr '<' '\n<' | grep -oE 'resource-id="video_feed_like"[^>]*bounds="\[[0-9,]+\]\[[0-9,]+\]"'
```

Double tap the centre of the video, then crop the like cell from a capture.

Pass = the heart is filled and the count incremented by exactly 1. Then double tap **again** and confirm the count does **not** decrement — that is the never-unlike rule.

- [ ] **Step 6: Verify the pager still swipes**

Swipe up. Pass = the page changes. `detectTapGestures` must not consume the drag; this is the single most likely regression in this task.

- [ ] **Step 7: Restore and report**

```bash
adb -s <serial> shell cmd notification set_dnd off
```

Report the result of each of the five checks with the evidence used. If any fails, that is a finding to surface, not to smooth over.

---

## PR completion

Open the PR with **`Closes: nubecita-zdv8.9`** — PR3 completes Slice 3b. Note in the body that D8 (progress bar) was deferred to `nubecita-zdv8.14`. This diff adds `@Composable` lines, so run the **compose-expert skill** (invoke it; do not substitute a hand-review). Reply to and resolve every review thread — the repo blocks merge on unresolved threads. `bd close nubecita-zdv8.9` only after the PR merges.
