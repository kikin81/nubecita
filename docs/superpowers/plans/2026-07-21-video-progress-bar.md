# Video Feed Playback Progress Bar (D8) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a thin, read-only, draw-phase-only playback progress bar pinned inset+rounded at the foot of the vertical video feed, reflecting the active clip's live position.

**Architecture:** Three units mirroring the existing LikeBurst / poster-crossfade split — a pure `progressFraction` helper (unit-tested), a stateless `VideoProgressBarContent` whose `drawBehind` reads a deferred `() -> Float` (screenshot-tested, draw-phase only), and a `VideoProgressBar` driver that owns a `MutableFloatState` fed by a `withFrameNanos` loop and is wired into `VideoFeedScreen` reading the existing `activePlayer` + `isPaused` (device-verified).

**Tech Stack:** Kotlin, Jetpack Compose, Media3 `Player`, JUnit Jupiter, the AGP `com.android.compose.screenshot` plugin.

## Global Constraints

- Position/duration come from the **player** (`player.currentPosition` / `player.duration`), NEVER `EmbedUi.Video.durationSeconds` — bench metadata declares 8s while clips run 14–15s, and real posts carry wrong metadata too.
- The advancing bar must re-run the **draw phase only** — never composition or layout. The progress value is read exclusively inside a `drawBehind` lambda as a deferred `() -> Float`.
- The frame loop runs **only for the active page while playing** (`player != null && isPlaying`).
- **Read-only** — no scrubbing, no seek, no buffered-range, no time labels in this slice.
- Loop reset is automatic: recompute the fraction from live position each frame; never accumulate. `REPEAT_MODE_ONE` wraps position to ~0 and the next frame reflects it.
- No new ViewModel state, events, or effects — the bar is a pure projection of existing screen state.
- No Kotlin `!!`. Reorder null checks so the compiler smart-casts.
- Device verification on the plugged-in Pixel Fold (serial `37201FDHS002UN`) is required; a moving bar needs two captures a known interval apart with a measured fill x-extent, not an eyeball.

---

### Task 1: `progressFraction` pure helper

**Files:**
- Modify: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoFeedPresentation.kt` (append)
- Test: `feature/videos/impl/src/test/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoFeedPresentationTest.kt` (append)

**Interfaces:**
- Produces: `internal fun progressFraction(positionMs: Long, durationMs: Long): Float` — returns `0f` when `durationMs <= 0`, else `(positionMs.toFloat() / durationMs).coerceIn(0f, 1f)`.

- [ ] **Step 1: Write the failing tests**

Append to `VideoFeedPresentationTest.kt`, inside the `VideoFeedPresentationTest` class (before the closing brace):

```kotlin
    @Test
    fun `progress is zero before the player is prepared`() {
        // Player.duration is TIME_UNSET (-1) until prepared; a naive divide would
        // yield a negative fraction. It must read as an empty bar, not a full one.
        assertEquals(0f, progressFraction(positionMs = 0L, durationMs = -1L), 0.0001f)
        assertEquals(0f, progressFraction(positionMs = 0L, durationMs = 0L), 0.0001f)
    }

    @Test
    fun `progress is the position over duration mid clip`() {
        assertEquals(0.5f, progressFraction(positionMs = 5_000L, durationMs = 10_000L), 0.0001f)
        assertEquals(0.25f, progressFraction(positionMs = 3_750L, durationMs = 15_000L), 0.0001f)
    }

    @Test
    fun `progress is full at the end and never overruns`() {
        assertEquals(1f, progressFraction(positionMs = 10_000L, durationMs = 10_000L), 0.0001f)
        // A position transiently past duration at a loop boundary must clamp to 1.
        assertEquals(1f, progressFraction(positionMs = 11_000L, durationMs = 10_000L), 0.0001f)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :feature:videos:impl:testDebugUnitTest --tests "*VideoFeedPresentationTest"`
Expected: FAIL — `progressFraction` is unresolved (compilation error).

- [ ] **Step 3: Implement `progressFraction`**

Append to `VideoFeedPresentation.kt` (end of file):

```kotlin

/**
 * Playback progress in `0f..1f` for the video progress bar.
 *
 * Both values come from the Media3 [androidx.media3.common.Player] at render
 * time — NOT from `EmbedUi.Video.durationSeconds`, which the bench fixture
 * declares as 8s while the bundled clips run 14–15s (and real posts carry wrong
 * metadata too), so a metadata-driven bar fills to 100% at ~55% of the clip and
 * then sits pinned.
 *
 * Returns `0f` when [durationMs] is non-positive — the player reports
 * `TIME_UNSET` (`-1`) until it is prepared, and a raw divide would produce a
 * negative or `NaN`/`Infinity` fraction that corrupts the draw. The `coerceIn`
 * clamps a [positionMs] that briefly exceeds [durationMs] at a loop boundary.
 */
internal fun progressFraction(
    positionMs: Long,
    durationMs: Long,
): Float = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :feature:videos:impl:testDebugUnitTest --tests "*VideoFeedPresentationTest"`
Expected: PASS (all existing + 3 new tests).

- [ ] **Step 5: Spotless + commit**

```bash
./gradlew :feature:videos:impl:spotlessApply -q
git add feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoFeedPresentation.kt \
        feature/videos/impl/src/test/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoFeedPresentationTest.kt
git commit -m "feat(videos): progressFraction helper for the playback progress bar

Refs: nubecita-zdv8.14"
```

---

### Task 2: `VideoProgressBarContent` stateless visual + screenshot tests

**Files:**
- Create: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoProgressBar.kt`
- Modify: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/VideoFeedTestTags.kt`
- Test: `feature/videos/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoProgressBarScreenshotTest.kt`

**Interfaces:**
- Consumes: nothing from Task 1 (visual only).
- Produces:
  - `internal fun VideoProgressBarContent(progress: () -> Float, modifier: Modifier = Modifier)` — a `fillMaxWidth`, `PROGRESS_HEIGHT`-tall, `PROGRESS_INSET`-inset pill; a `drawBehind` block draws a translucent-white track and a solid-white fill to `progress()` of the width. Carries `VideoFeedTestTags.PROGRESS_BAR`.
  - `VideoFeedTestTags.PROGRESS_BAR: String = "video_feed_progress"`.

- [ ] **Step 1: Add the test tag**

In `VideoFeedTestTags.kt`, add after the `PAUSE_INDICATOR` constant (mirror the existing `const val` + blank-line spacing):

```kotlin

    const val PROGRESS_BAR: String = "video_feed_progress"
```

- [ ] **Step 2: Create the stateless content composable**

Create `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoProgressBar.kt`:

```kotlin
package net.kikin.nubecita.feature.videos.impl.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.feature.videos.impl.VideoFeedTestTags

/**
 * Stateless visual for the playback progress bar: a pill-shaped translucent-white
 * track with a solid-white fill grown left→right to [progress].
 *
 * [progress] is a DEFERRED read — invoked inside the `drawBehind` lambda, never
 * unwrapped at composition scope — so an advancing bar re-runs only the draw
 * phase, never composition or layout. This is the same discipline as
 * `VideoFeedPage`'s poster alpha and `LikeBurst`. Splitting the visual from the
 * frame driver ([VideoProgressBar]) also lets layoutlib screenshot it without a
 * running `withFrameNanos` loop.
 *
 * White-on-black is deliberate: the feed canvas is always `Color.Black`, so the
 * bar does not read theme colours (a themed token would pin a colour that never
 * ships on this surface).
 */
@Composable
internal fun VideoProgressBarContent(
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = PROGRESS_INSET)
            .height(PROGRESS_HEIGHT)
            .testTag(VideoFeedTestTags.PROGRESS_BAR)
            .drawBehind {
                val radius = CornerRadius(size.height / 2f, size.height / 2f)
                drawRoundRect(color = PROGRESS_TRACK_COLOR, cornerRadius = radius)
                val fillWidth = size.width * progress().coerceIn(0f, 1f)
                if (fillWidth > 0f) {
                    drawRoundRect(
                        color = PROGRESS_FILL_COLOR,
                        size = Size(fillWidth, size.height),
                        cornerRadius = radius,
                    )
                }
            },
    )
}

private val PROGRESS_HEIGHT = 3.dp
private val PROGRESS_INSET = 16.dp
private val PROGRESS_TRACK_COLOR = Color.White.copy(alpha = 0.28f)
private val PROGRESS_FILL_COLOR = Color.White
```

Add the missing `Box` import — insert `import androidx.compose.foundation.layout.Box` in the import block (alphabetical order: it sorts before `fillMaxWidth`).

- [ ] **Step 3: Create the screenshot tests**

Create `feature/videos/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoProgressBarScreenshotTest.kt`:

```kotlin
package net.kikin.nubecita.feature.videos.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

private const val CANVAS_HEIGHT_DP = 120

/**
 * The feed canvas is always black (VideoFeedScreen sets a black Scaffold), so
 * the fixture wraps in an explicit black Box rather than a themed surface — a
 * themed canvas would pin white behind the white bar. No dark variants: the bar
 * does not respond to theme, so a dark baseline is byte-identical to its light
 * twin. The three fractions produce three visibly distinct fill widths, so the
 * baselines discriminate.
 */
@Composable
private fun ProgressCanvas(progress: Float) {
    NubecitaTheme(dynamicColor = false) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.BottomCenter) {
            VideoProgressBarContent(progress = { progress })
        }
    }
}

@PreviewTest
@Preview(name = "progress-empty", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Composable
private fun ProgressEmptyPreview() = ProgressCanvas(progress = 0f)

@PreviewTest
@Preview(name = "progress-partial", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Composable
private fun ProgressPartialPreview() = ProgressCanvas(progress = 0.4f)

@PreviewTest
@Preview(name = "progress-full", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Composable
private fun ProgressFullPreview() = ProgressCanvas(progress = 1f)
```

- [ ] **Step 4: Compile main + screenshotTest source sets**

Run: `./gradlew :feature:videos:impl:compileDebugKotlin :feature:videos:impl:compileDebugScreenshotTestKotlin`
Expected: BUILD SUCCESSFUL (the `screenshotTest` source set is a separate compile that `assemble`/`testDebugUnitTest` do not cover).

- [ ] **Step 5: Generate the screenshot baselines**

Run: `./gradlew :feature:videos:impl:updateDebugScreenshotTest`
Then confirm three new PNGs exist and are distinct:

Run: `ls feature/videos/impl/src/screenshotTest/**/reference/ 2>/dev/null | grep -i progress`
Expected: three files (`progress-empty`, `progress-partial`, `progress-full`). Hash them to confirm they differ:

Run: `md5 feature/videos/impl/src/screenshotTest/*/reference/*progress* 2>/dev/null || find feature/videos/impl/src/screenshotTest -iname '*progress*' -exec md5 {} +`
Expected: three DIFFERENT hashes (a partial-vs-full-vs-empty regression must be able to fail; identical hashes mean the fixture pins nothing).

- [ ] **Step 6: Validate the baselines**

Run: `./gradlew :feature:videos:impl:validateDebugScreenshotTest`
Expected: PASS.

- [ ] **Step 7: Spotless + commit**

```bash
./gradlew :feature:videos:impl:spotlessApply -q
git add feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoProgressBar.kt \
        feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/VideoFeedTestTags.kt \
        feature/videos/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoProgressBarScreenshotTest.kt \
        feature/videos/impl/src/screenshotTest
git commit -m "feat(videos): stateless progress-bar visual + screenshot baselines

Refs: nubecita-zdv8.14"
```

---

### Task 3: `VideoProgressBar` driver + wire into `VideoFeedScreen`

**Files:**
- Modify: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoProgressBar.kt` (add driver above the content composable)
- Modify: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/VideoFeedScreen.kt`

**Interfaces:**
- Consumes:
  - `progressFraction(positionMs: Long, durationMs: Long): Float` (Task 1).
  - `VideoProgressBarContent(progress: () -> Float, modifier: Modifier)` (Task 2).
- Produces: `internal fun VideoProgressBar(player: Player?, isPlaying: Boolean, modifier: Modifier = Modifier)` — owns a `MutableFloatState` driven by a `withFrameNanos` loop that runs only while `player != null && isPlaying`, and renders `VideoProgressBarContent`.

- [ ] **Step 1: Add the driver composable**

In `VideoProgressBar.kt`, add these imports to the import block (keep alphabetical):

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.media3.common.Player
import net.kikin.nubecita.feature.videos.impl.ui.progressFraction
```

(The `progressFraction` import is same-package, so it may be unnecessary — if the compiler flags it as redundant, drop that one line. All others are required.)

Insert the driver ABOVE `VideoProgressBarContent`:

```kotlin
/**
 * Drives [VideoProgressBarContent] from the active Media3 [player].
 *
 * A `withFrameNanos` loop writes the live fraction into a [androidx.compose.runtime.MutableFloatState]
 * that ONLY the content's `drawBehind` reads — so each frame re-runs the draw
 * phase and never composition. The loop runs only while [player] is non-null and
 * [isPlaying]; when either flips the `LaunchedEffect` cancels and the last value
 * stays drawn (a static bar under pause). `withFrameNanos` also stops producing
 * frames when the app is backgrounded, so the loop is free off-screen.
 *
 * Loop reset is automatic: the fraction is recomputed from the player's live
 * `currentPosition`/`duration` every frame, so when `REPEAT_MODE_ONE` wraps the
 * position back to ~0 the next frame reflects it — nothing is accumulated.
 */
@Composable
internal fun VideoProgressBar(
    player: Player?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(player, isPlaying) {
        if (player != null && isPlaying) {
            while (true) {
                withFrameNanos { }
                progress = progressFraction(player.currentPosition, player.duration)
            }
        }
    }
    VideoProgressBarContent(progress = { progress }, modifier = modifier)
}
```

- [ ] **Step 2: Wire it into `VideoFeedScreen`**

In `VideoFeedScreen.kt`, add the import (keep alphabetical, beside the other `...impl.ui.*` imports):

```kotlin
import net.kikin.nubecita.feature.videos.impl.ui.VideoProgressBar
```

Then, inside the `is VideoFeedStatus.Content ->` branch, add the bar as the LAST child of the feed content `Box` — the `Box(contentModifier.onSizeChanged { pageHeightPx = it.height }) { … }` that holds the surface and the `VerticalPager`. Add it immediately after the `VerticalPager(...) { … }` block's closing brace, still inside that `Box`:

```kotlin
                    VideoProgressBar(
                        player = activePlayer,
                        isPlaying = !state.isPaused,
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 8.dp),
                    )
```

Add the imports these modifiers need if not already present (check the existing import block first; `Alignment`, `Modifier`, and `padding` are already imported — add only the genuinely missing ones):

```kotlin
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.unit.dp
```

- [ ] **Step 3: Compile the module**

Run: `./gradlew :feature:videos:impl:compileDebugKotlin :feature:videos:impl:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — all Task 1 unit tests still pass, driver + wiring compile.

- [ ] **Step 4: Compile the bench flavor (device build source set)**

Run: `./gradlew :app:compileBenchDebugKotlin`
Expected: BUILD SUCCESSFUL — the bench flavor is what installs to the device; compiling it before the device pass catches flavor-only breakage that `compileDebugKotlin` misses.

- [ ] **Step 5: Install and device-verify on the Pixel Fold**

The immersive feed is entered via the **Discover** tab's *Trending Videos carousel* (tapping a thumbnail → `VideoFeed`), NOT by tapping an inline video embed (that opens the lightbox). The folded device's active display is the **outer** panel; capture it with the explicit physical display id.

```bash
D=37201FDHS002UN
./gradlew :app:installBenchDebug
adb -s $D shell am start-activity -n net.kikin.nubecita/.MainActivity
# In the app: tap Discover (top), tap a Trending Videos thumbnail to enter the immersive feed.
# Confirm the active display id (outer panel, state ON):
adb -s $D shell dumpsys display | grep -A2 'Outer Display' | grep -i 'state ON'
DISP=$(adb -s $D shell dumpsys SurfaceFlinger --display-id | grep 'display 1' | grep -oE '[0-9]{16,}' | head -1)
# Two captures a known interval apart while the clip plays:
adb -s $D exec-out screencap -p -d "$DISP" > /tmp/prog-a.png
sleep 3
adb -s $D exec-out screencap -p -d "$DISP" > /tmp/prog-b.png
```

Read `/tmp/prog-a.png` and `/tmp/prog-b.png`. Verify:
- A thin inset+rounded white bar sits at the bottom of the screen.
- The fill's right edge (x-extent) is **further right** in `prog-b` than in `prog-a` (the bar advances).
- Let a short clip loop (watch past its end) and confirm the fill **resets** toward the left rather than sticking at full.
- Tap to pause and confirm the bar **stops** (a third capture pair shows no advance).

Record the measured x-extents in the task report as the pass evidence (an eyeball "looks like it moved" is not acceptable per the standing device rule).

- [ ] **Step 6: Spotless + commit**

```bash
./gradlew :feature:videos:impl:spotlessApply -q
git add feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoProgressBar.kt \
        feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/VideoFeedScreen.kt
git commit -m "feat(videos): drive the playback progress bar from the active player

Refs: nubecita-zdv8.14"
```

---

## Self-Review

**Spec coverage:**
- Placement (inset+rounded, bottom, navbar padding, screen-level) → Task 2 (visual) + Task 3 (align/navbar wiring). ✓
- Appearance (translucent-white track, solid-white fill) → Task 2 constants. ✓
- Pure `progressFraction` with `duration <= 0` guard + coerce → Task 1. ✓
- Stateless `VideoProgressBarContent` deferred-read drawBehind → Task 2. ✓
- `VideoProgressBar` driver, `withFrameNanos`, active-while-playing only → Task 3. ✓
- Reads player position/duration, not metadata → Global Constraints + Task 1 doc + Task 3 call site. ✓
- Loop reset automatic → Task 3 doc + Step 5 device check. ✓
- No VM state/events → Task 3 wires existing `activePlayer`/`isPaused` only. ✓
- Testing: unit (Task 1), screenshot 3 fractions (Task 2), device two-capture (Task 3). ✓
- Non-goals (no scrubbing/buffered/labels) → not implemented; Global Constraints states read-only. ✓

**Placeholder scan:** No TBD/TODO; every code step shows complete code. ✓

**Type consistency:** `progressFraction(positionMs: Long, durationMs: Long): Float`, `VideoProgressBarContent(progress: () -> Float, modifier)`, `VideoProgressBar(player: Player?, isPlaying: Boolean, modifier)`, `VideoFeedTestTags.PROGRESS_BAR` — names/signatures identical across all tasks. ✓
