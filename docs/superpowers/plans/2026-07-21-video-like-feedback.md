# Video Feed Like Feedback (haptics + heart burst) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add haptic feedback to the vertical video feed's like/repost/bookmark toggles and double-tap, and a TikTok-style heart burst under the finger on every double-tap.

**Architecture:** Pure view-layer. `PostHaptics` gains `bookmarkOn/Off`. Haptics fire at the `VideoFeedScreen` wiring sites (where each callback is actually invoked). The heart burst is a videos-local, page-scoped overlay in `VideoFeedPage`, driven by a pure transform function.

**Tech Stack:** Kotlin 2.3, Jetpack Compose, JUnit Jupiter, AGP Compose screenshot testing.

**Spec:** `docs/superpowers/specs/2026-07-21-video-like-feedback-design.md`. Rebased onto `main` after PR #775 (bookmark) merged.

## Global Constraints

- Modules `:core:common` (haptics) and `:feature:videos:impl` (screen + burst).
- No ViewModel change; no playback change.
- **`Math.random` is banned** in this environment and non-deterministic for tests — per-heart tilt is derived from the heart's id.
- No Kotlin `!!`. JUnit **Jupiter**. ktlint via Spotless before every commit.
- New `<string>`? None expected (the heart has a `contentDescription`; add one string in 3 locales if used).
- Conventional Commits; footer `Refs: nubecita-zdv8`. `Closes:` not used (no dedicated bd id beyond the epic child; PR body references the epic).
- **Verify on the plugged-in Pixel Fold** (`37201FDHS002UN`). The burst is device-visible; haptics are verified by faithful mirror of the Feed path + manual feel.
- Branch `feat/nubecita-video-like-feedback` off fresh `main`. Never commit to `main`.

## Verified Facts

| Thing | Fact |
|---|---|
| Haptics helper | `PostHaptics(view)` in `:core:common:haptic`: `likeOn()` = `CONFIRM`/fallback `LONG_PRESS`; `likeOff()` = `performToggleOff()` (`TOGGLE_OFF` on API 34+, else `KEYBOARD_TAP`); `repostOn/Off` identical shape; `lightTap()`; `rejected()`. `rememberPostHaptics()` composable exists (`FeedScreen.kt:184`). **No `bookmarkOn/Off` yet.** No `PostHapticsTest` exists. |
| Feed wrapping | `FeedInteractions` wraps `onLike = { post -> if (post.viewer.isLikedByViewer) haptics.likeOff() else haptics.likeOn(); viewModel.onLike(post) }`, same for repost. The pattern to mirror. |
| Video wiring | `VideoFeedScreen` wires `onLike = { viewModel.onLike(item.post) }`, `onRepost = { viewModel.onRepost(item.post) }` (delegation-forwarded), `onBookmark = { callbacks.onBookmark(item.post) }`, `onDoubleTapLike = { viewModel.handleEvent(VideoFeedEvent.DoubleTapLike(item.post)) }`. Haptics attach at these sites. |
| Double-tap | `VideoFeedPage` runs `detectTapGestures(onDoubleTap = { currentOnDoubleTapLike() }, onTap = { currentOnTogglePlayPause() })`; both callbacks are `rememberUpdatedState`-wrapped (PR3). `onDoubleTap`'s lambda already receives the `Offset` — currently ignored. |
| Icon | `NubecitaIconName.Favorite` (filled) exists. `NubecitaIcon(name, contentDescription, modifier, filled, …, opticalSize, tint)`. |
| viewer state | `post.viewer.isLikedByViewer`, `isRepostedByViewer`, `isBookmarked` — all `Boolean`. |

## File Structure

| File | Responsibility |
|---|---|
| `core/common/.../haptic/PostHaptics.kt` | **Modify.** `bookmarkOn()`/`bookmarkOff()`. |
| `feature/videos/impl/.../ui/LikeBurst.kt` | **Create.** `HeartBurst`, `BurstTransform`, pure `heartBurstTransform`, `LikeBurstHeartContent` (stateless visual), `LikeBurstHeart` (animated wrapper). |
| `feature/videos/impl/.../ui/VideoFeedPage.kt` | **Modify.** Capture the double-tap offset; host the burst list + overlay. |
| `feature/videos/impl/.../VideoFeedScreen.kt` | **Modify.** `rememberPostHaptics()`; fire haptics at the like/repost/bookmark/double-tap sites. |
| `feature/videos/impl/src/test/.../ui/LikeBurstTest.kt` | **Create.** Unit-test `heartBurstTransform`. |
| `.../screenshotTest/.../VideoFeedPageScreenshotTest.kt` | **Modify.** One static `LikeBurstHeartContent` preview. |

---

### Task 1: `PostHaptics.bookmarkOn/Off`

**Files:**
- Modify: `core/common/src/main/kotlin/net/kikin/nubecita/core/common/haptic/PostHaptics.kt`

**Interfaces:**
- Produces: `PostHaptics.bookmarkOn()`, `PostHaptics.bookmarkOff()`.

- [ ] **Step 1: Add the methods** (no unit test — `PostHaptics` needs a real `View`; bookmark is a verbatim mirror of like, and the existing like/repost methods are untested for the same reason). Insert after `repostOff()`:

```kotlin
    /** Tap enabled the bookmark — confirmation feel, same envelope as [likeOn]. */
    public fun bookmarkOn() {
        perform(modern = HapticFeedbackConstants.CONFIRM, fallback = HapticFeedbackConstants.LONG_PRESS)
    }

    /** Tap removed the bookmark — same distinct lighter cue as [likeOff]. */
    public fun bookmarkOff() {
        performToggleOff()
    }
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :core:common:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
./gradlew :core:common:spotlessApply -q
git add core/common
git commit -m "feat(haptics): bookmark on/off cues on PostHaptics

Mirrors likeOn/likeOff (CONFIRM / TOGGLE_OFF) — bookmark is a save-type
confirmation. No surface fired a bookmark haptic before; additive.

Refs: nubecita-zdv8"
```

---

### Task 2: The burst — pure transform + composables

**Files:**
- Create: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/ui/LikeBurst.kt`
- Test: `feature/videos/impl/src/test/kotlin/net/kikin/nubecita/feature/videos/impl/ui/LikeBurstTest.kt`

**Interfaces:**
- Produces:
  - `internal data class HeartBurst(val id: Int, val position: androidx.compose.ui.geometry.Offset)`
  - `internal data class BurstTransform(val scale: Float, val alpha: Float, val translationYDp: Float, val rotationDegrees: Float)`
  - `internal fun heartBurstTransform(progress: Float, id: Int): BurstTransform`
  - `internal fun LikeBurstHeartContent(transform: BurstTransform, modifier: Modifier = Modifier)`
  - `internal fun LikeBurstHeart(heart: HeartBurst, onFinished: () -> Unit, modifier: Modifier = Modifier)`

- [ ] **Step 1: Write the failing transform tests**

Create `LikeBurstTest.kt`:

```kotlin
package net.kikin.nubecita.feature.videos.impl.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class LikeBurstTest {
    @Test
    fun `at start the heart is invisible and unscaled`() {
        val t = heartBurstTransform(progress = 0f, id = 2)
        assertEquals(0f, t.scale)
        assertEquals(0f, t.alpha)
        assertEquals(0f, t.translationYDp)
    }

    @Test
    fun `scale overshoots to 1_2 at the pop peak then settles to 1_0`() {
        assertEquals(1.2f, heartBurstTransform(0.2f, 2).scale, 0.001f)
        assertEquals(1.0f, heartBurstTransform(0.35f, 2).scale, 0.001f)
        assertEquals(1.0f, heartBurstTransform(1f, 2).scale, 0.001f)
    }

    @Test
    fun `alpha reaches full early, holds, then fades to zero`() {
        assertEquals(1f, heartBurstTransform(0.15f, 2).alpha, 0.001f)
        assertEquals(1f, heartBurstTransform(0.5f, 2).alpha, 0.001f)
        assertEquals(0f, heartBurstTransform(1f, 2).alpha, 0.001f)
    }

    @Test
    fun `the heart drifts up only in the back half`() {
        assertEquals(0f, heartBurstTransform(0.4f, 2).translationYDp, 0.001f)
        assertEquals(-48f, heartBurstTransform(1f, 2).translationYDp, 0.001f)
    }

    @Test
    fun `tilt is deterministic per id, spread around zero`() {
        assertEquals(-12f, heartBurstTransform(0.5f, 0).rotationDegrees)
        assertEquals(0f, heartBurstTransform(0.5f, 2).rotationDegrees)
        assertEquals(12f, heartBurstTransform(0.5f, 4).rotationDegrees)
        // wraps every 5 ids
        assertEquals(-12f, heartBurstTransform(0.5f, 5).rotationDegrees)
    }
}
```

- [ ] **Step 2: Run to verify fail**

```bash
./gradlew :feature:videos:impl:testDebugUnitTest --tests '*LikeBurstTest*'
```

Expected: FAIL — `Unresolved reference: heartBurstTransform`.

- [ ] **Step 3: Write `LikeBurst.kt`**

```kotlin
package net.kikin.nubecita.feature.videos.impl.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/** One heart spawned by a double-tap, positioned at the touch point. */
internal data class HeartBurst(
    val id: Int,
    val position: Offset,
)

/** Per-frame transform of a burst heart, derived purely from animation progress. */
internal data class BurstTransform(
    val scale: Float,
    val alpha: Float,
    val translationYDp: Float,
    val rotationDegrees: Float,
)

/**
 * Pure transform for a heart at [progress] (0..1). Pop in (scale overshoots to
 * 1.2 then settles), hold, then fade out while drifting up. Extracted so the
 * motion shape is unit-tested without a running animation.
 *
 * Tilt is deterministic from [id] — organic variety without `Math.random`,
 * which is banned here and non-deterministic for tests.
 */
internal fun heartBurstTransform(
    progress: Float,
    id: Int,
): BurstTransform {
    val p = progress.coerceIn(0f, 1f)
    val scale =
        when {
            p <= 0.2f -> lerp(0f, 1.2f, p / 0.2f)
            p <= 0.35f -> lerp(1.2f, 1.0f, (p - 0.2f) / 0.15f)
            else -> 1.0f
        }
    val alpha =
        when {
            p <= 0.15f -> p / 0.15f
            p <= 0.5f -> 1f
            else -> lerp(1f, 0f, (p - 0.5f) / 0.5f)
        }
    val translationYDp = if (p <= 0.4f) 0f else lerp(0f, -48f, (p - 0.4f) / 0.6f)
    val rotationDegrees = ((id % 5) - 2) * 6f
    return BurstTransform(scale, alpha, translationYDp, rotationDegrees)
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

/** Stateless heart visual for a fixed [transform] — the screenshot seam. */
@Composable
internal fun LikeBurstHeartContent(
    transform: BurstTransform,
    modifier: Modifier = Modifier,
) {
    NubecitaIcon(
        name = NubecitaIconName.Favorite,
        contentDescription = null,
        filled = true,
        tint = Color.White,
        opticalSize = HEART_SIZE,
        modifier =
            modifier.graphicsLayer {
                scaleX = transform.scale
                scaleY = transform.scale
                alpha = transform.alpha
                rotationZ = transform.rotationDegrees
                translationY = transform.translationYDp.dp.toPx()
            },
    )
}

/** Drives a [heart]'s progress 0->1 over [BURST_DURATION_MS], then [onFinished]. */
@Composable
internal fun LikeBurstHeart(
    heart: HeartBurst,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember(heart.id) { Animatable(0f) }
    LaunchedEffect(heart.id) {
        progress.animateTo(1f, tween(durationMillis = BURST_DURATION_MS, easing = LinearEasing))
        onFinished()
    }
    LikeBurstHeartContent(heartBurstTransform(progress.value, heart.id), modifier)
}

private val HEART_SIZE = 100.dp
private const val BURST_DURATION_MS = 700
```

Add the import `androidx.compose.runtime.remember` (used by `LikeBurstHeart`).

- [ ] **Step 4: Run to verify pass**

```bash
./gradlew :feature:videos:impl:testDebugUnitTest --tests '*LikeBurstTest*'
```

Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
./gradlew :feature:videos:impl:spotlessApply -q
git add feature/videos/impl
git commit -m "feat(videos): heart-burst animation for double-tap-like

Pure heartBurstTransform (pop-in overshoot, hold, drift-up fade) unit-tested
at the motion boundaries; deterministic per-id tilt. LikeBurstHeartContent is
the stateless visual, LikeBurstHeart the self-removing animated wrapper.

Refs: nubecita-zdv8"
```

---

### Task 3: Wire the burst into the page and haptics into the screen

**Files:**
- Modify: `feature/videos/impl/.../ui/VideoFeedPage.kt`
- Modify: `feature/videos/impl/.../VideoFeedScreen.kt`
- Modify: `.../screenshotTest/.../VideoFeedPageScreenshotTest.kt`

**Interfaces:**
- Consumes: `HeartBurst`, `LikeBurstHeart`, `LikeBurstHeartContent`, `heartBurstTransform` (Task 2); `bookmarkOn/Off` (Task 1).

- [ ] **Step 1: Host the burst in `VideoFeedPage`**

Add imports: `androidx.compose.foundation.layout.matchParentSize`, `androidx.compose.runtime.mutableIntStateOf`, `androidx.compose.runtime.remember`, `androidx.compose.runtime.mutableStateListOf`, `androidx.compose.runtime.setValue`, `androidx.compose.ui.unit.IntOffset`, `androidx.compose.ui.unit.dp`, `androidx.compose.foundation.layout.offset`.

In the gesture block, capture the offset and spawn a heart. Replace the `detectTapGestures(...)` with:

```kotlin
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            hearts.add(HeartBurst(id = nextHeartId, position = offset))
                            nextHeartId++
                            currentOnDoubleTapLike()
                        },
                        onTap = { currentOnTogglePlayPause() },
                    )
```

Declare the burst state just inside the root `Box` (top of the composable body, before the gesture Box):

```kotlin
        val hearts = remember { mutableStateListOf<HeartBurst>() }
        var nextHeartId by remember { mutableIntStateOf(0) }
```

After `chrome()` (so hearts draw on top), render the overlay. It has no pointer input, so it does not intercept taps:

```kotlin
        Box(Modifier.matchParentSize()) {
            hearts.forEach { heart ->
                key(heart.id) {
                    LikeBurstHeart(
                        heart = heart,
                        onFinished = { hearts.remove(heart) },
                        // Center the 100dp heart on the touch point.
                        modifier =
                            Modifier.offset {
                                IntOffset(
                                    x = heart.position.x.toInt() - heartCenterPx,
                                    y = heart.position.y.toInt() - heartCenterPx,
                                )
                            },
                    )
                }
            }
        }
```

`heartCenterPx` is a composition-scope density read (the 100dp heart's half-extent in px), declared alongside the burst state at the top of the composable body:

```kotlin
        val heartCenterPx = with(LocalDensity.current) { (100.dp / 2).roundToPx() }
```

Add imports `androidx.compose.runtime.key`, `androidx.compose.ui.platform.LocalDensity`. The read is at composition scope, not per-frame, so it costs nothing on the hot path.

- [ ] **Step 2: Fire haptics at the screen wiring sites**

In `VideoFeedScreen.kt`, add `import net.kikin.nubecita.core.common.haptic.rememberPostHaptics` and, near the other `remember`s:

```kotlin
    val haptics = rememberPostHaptics()
```

In the `VideoPageChrome(...)` call, wrap the like/repost/bookmark lambdas and the double-tap:

```kotlin
                                onLike = {
                                    if (item.post.viewer.isLikedByViewer) haptics.likeOff() else haptics.likeOn()
                                    viewModel.onLike(item.post)
                                },
                                onRepost = {
                                    if (item.post.viewer.isRepostedByViewer) haptics.repostOff() else haptics.repostOn()
                                    viewModel.onRepost(item.post)
                                },
                                onBookmark = {
                                    if (item.post.viewer.isBookmarked) haptics.bookmarkOff() else haptics.bookmarkOn()
                                    callbacks.onBookmark(item.post)
                                },
```

and the double-tap (fire `likeOn` only on the unliked→liked transition; the burst plays regardless, inside the page):

```kotlin
                            onDoubleTapLike = {
                                if (!item.post.viewer.isLikedByViewer) haptics.likeOn()
                                viewModel.handleEvent(VideoFeedEvent.DoubleTapLike(item.post))
                            },
```

- [ ] **Step 3: Add a screenshot preview for the heart visual**

In `VideoFeedPageScreenshotTest.kt`, add (the burst is at mid-life, alpha 1, so it's visible):

```kotlin
@PreviewTest
@Preview(name = "like-burst-heart", showBackground = true, heightDp = 240)
@Composable
private fun LikeBurstHeartPreview() {
    VideoFeedCanvas {
        LikeBurstHeartContent(transform = heartBurstTransform(progress = 0.4f, id = 0))
    }
}
```

- [ ] **Step 4: Regenerate and inspect the baseline**

```bash
./gradlew :feature:videos:impl:updateScreenshots
```

**Look at** `like-burst-heart`: a white filled heart, slightly tilted (id 0 → −12°), full size, opaque. If it's missing or clipped, that's a finding. Confirm baseline uniqueness (count == unique hashes). Restore any pre-existing baseline that only drifted (`git checkout origin/main -- <path>`) — this task changes no existing previewed composable, so only `like-burst-heart` should be genuinely new.

- [ ] **Step 5: Verify**

```bash
./gradlew :feature:videos:impl:assembleDebug :feature:videos:impl:testDebugUnitTest :feature:videos:impl:validateScreenshots :feature:videos:impl:lintDebug > /tmp/gate.log 2>&1; echo "EXIT=$?"; tail -3 /tmp/gate.log
```

Expected `EXIT=0`. Check the exit code explicitly.

- [ ] **Step 6: Commit**

```bash
./gradlew :feature:videos:impl:spotlessApply -q
git add feature/videos/impl
git commit -m "feat(videos): wire the heart burst and interaction haptics

Double-tap spawns a heart at the touch point (page-scoped overlay, self-
removing, draws above the chrome without intercepting taps). Like/repost/
bookmark toggles and the double-tap fire the matching PostHaptics cue; the
double-tap buzzes only on the unliked->liked transition.

Refs: nubecita-zdv8"
```

---

### Task 4: Device verification on the Pixel Fold

**Files:** none.

- [ ] **Step 1: Check devices, install, DND**

```bash
adb devices -l
./gradlew :app:installBenchDebug
adb -s 37201FDHS002UN shell cmd notification set_dnd priority
```

Use `-s 37201FDHS002UN` for every adb call; pass `-d <onDisplayId>` (folded ⇒ outer `4619827677550801153`) to `screencap`.

- [ ] **Step 2: Verify the burst appears at the finger**

Open the feed. Double-tap the video **off-center** (e.g. upper-left of the frame, clear of the rail). Immediately capture — the burst runs ~700ms, and screencap's ~300ms latency lands inside it:

```bash
adb -s <serial> shell input tap <x> <y>; adb -s <serial> shell input tap <x> <y>
adb -s <serial> exec-out screencap -p -d <displayId> > /tmp/burst.png
```

Pass = a white heart is visible near `(x, y)` — not centered, not at the rail. If nothing appears, the burst didn't spawn or the capture missed the window (retry, or lengthen with a second capture ~150ms later).

- [ ] **Step 3: Verify rapid taps stack**

Fire four taps fast (two double-taps) at two different points; capture. Pass = more than one heart visible, at different positions.

- [ ] **Step 4: Verify the rail heart also fills**

After a double-tap on an unliked post, the rail like cell fills (the like committed) — same as PR3. Confirm both feedbacks happen: burst + rail fill.

- [ ] **Step 5: Haptics — manual**

Haptics can't be screenshotted. Confirm the code path is reached (the like/repost/bookmark taps invoke the wrapped lambdas — covered by the fact the like/repost/bookmark still function, verified in prior PRs). Note in the report that tactile confirmation is the reviewer's/user's to feel. Do not claim haptics "work" from a screenshot.

- [ ] **Step 6: Restore DND, report each check honestly.**

```bash
adb -s <serial> shell cmd notification set_dnd off
```

---

## PR completion

Open the PR referencing the epic (no `Closes:` unless a dedicated bd id is assigned). State that haptics are best-effort and not screenshot-verifiable. This diff adds `@Composable` lines → run the **compose-expert skill**. Reply to and resolve every review thread.
