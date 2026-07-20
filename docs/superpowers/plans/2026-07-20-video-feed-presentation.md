# Video Feed Presentation (Slice 3b, PR1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the vertical video feed's video slide with the swipe gesture and never show a black frame, by switching the surface to `TextureView`, translating it by the pager's displacement, and crossfading a poster over each page.

**Architecture:** One persistent `PlayerSurface` (`TextureView`) sits behind a transparent `VerticalPager` and is translated via `graphicsLayer` by the settled page's displacement. Each pager page renders a poster *over* the video which fades out once the active player renders its first frame. All slide/alpha math is extracted into pure functions so it is unit-testable; the composables around it are covered by screenshot tests.

**Tech Stack:** Kotlin 2.3, Jetpack Compose, Material 3 Expressive, Media3 1.10.1 (`media3-ui-compose`), Coil 3, JUnit Jupiter, AGP Compose screenshot testing.

**Spec:** `docs/superpowers/specs/2026-07-19-video-feed-chrome-design.md` (decisions D1–D4). This plan covers **PR1 only**. PRs 2 and 3 are outlined at the end and get their own plans once PR1 is verified on device.

## Global Constraints

- Module is `:feature:videos:impl`; package root `net.kikin.nubecita.feature.videos.impl`.
- Every `Scaffold(` sets `containerColor` explicitly. This screen uses `Color.Black` (full-bleed video canvas), which is already the case — do not change it to a surface token.
- MVI per CLAUDE.md: flat state, `VideoFeedStatus` sealed sum for the load lifecycle. **PR1 adds no new state fields, events, or effects.**
- No Kotlin `!!` anywhere. Reorder `if`/`when` on a positive `!= null` so the compiler smart-casts.
- ktlint via Spotless. Run `./gradlew :feature:videos:impl:spotlessApply` before every commit.
- New `testTag` values are pinned by a unit test in `VideoFeedTestTagsTest` — the `:benchmark` module hardcodes the literals and does not depend on this module.
- JUnit **Jupiter** (`org.junit.jupiter.api.Test`), not JUnit 4, for JVM unit tests.
- `@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)` is required in any file touching `PlayerSurface`, `rememberPresentationState`, or the `SURFACE_TYPE_*` constants — all are `@UnstableApi`.
- Conventional Commits. Footer `Refs: nubecita-zdv8.9` on every commit. **`Closes:` goes in the PR body only**, and **not for this PR** — nubecita-zdv8.9 stays open through PR3.
- Commit on a branch off fresh `main`. Never commit or push to `main`.

## File Structure

| File | Responsibility |
|---|---|
| `.../impl/ui/VideoFeedPresentation.kt` | **Create.** Pure functions: surface translation + poster alpha. No Compose imports beyond none — plain Kotlin, fully unit-testable. |
| `.../impl/ui/VideoFeedPage.kt` | **Create.** Stateless page composable. PR1: the poster layer only. PR2 adds chrome inside it — this is the seam. |
| `.../impl/VideoFeedTestTags.kt` | **Modify.** Add `POSTER`. |
| `.../impl/VideoFeedScreen.kt` | **Modify.** Surface type constant, `graphicsLayer` translation, pager pages render `VideoFeedPage`. |
| `.../impl/src/test/.../ui/VideoFeedPresentationTest.kt` | **Create.** JVM unit tests for the pure functions. |
| `.../impl/src/test/.../VideoFeedTestTagsTest.kt` | **Modify.** Pin `POSTER`. |
| `.../impl/src/screenshotTest/.../ui/VideoFeedPageScreenshotTest.kt` | **Create.** Poster previews + committed baselines. |

`VideoFeedPage` takes `player: Player?` from PR2 onward; in PR1 it needs no player at all, which keeps it trivially renderable by layoutlib.

---

### Task 1: Pure presentation math

The two subtle rules in the design (D2, D3) are pure arithmetic. Extract them first so they carry real tests — the surrounding Compose code cannot be unit-tested.

**Files:**
- Create: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoFeedPresentation.kt`
- Test: `feature/videos/impl/src/test/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoFeedPresentationTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `internal fun surfaceTranslationPx(currentPage: Int, currentPageOffsetFraction: Float, settledPage: Int, pageHeightPx: Float): Float`
  - `internal fun posterAlphaTarget(isSettledPage: Boolean, coverSurface: Boolean): Float`

- [ ] **Step 1: Write the failing tests**

Create `feature/videos/impl/src/test/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoFeedPresentationTest.kt`:

```kotlin
package net.kikin.nubecita.feature.videos.impl.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class VideoFeedPresentationTest {
    @Test
    fun `settled surface is not translated`() {
        val translation =
            surfaceTranslationPx(
                currentPage = 3,
                currentPageOffsetFraction = 0f,
                settledPage = 3,
                pageHeightPx = 2000f,
            )
        assertEquals(0f, translation)
    }

    @Test
    fun `dragging toward the next page moves the surface up`() {
        // Scroll position 3.5 with the loaded page at 3: the video is half a
        // page above centre, i.e. negative translationY.
        val translation =
            surfaceTranslationPx(
                currentPage = 3,
                currentPageOffsetFraction = 0.5f,
                settledPage = 3,
                pageHeightPx = 2000f,
            )
        assertEquals(-1000f, translation)
    }

    @Test
    fun `dragging toward the previous page moves the surface down`() {
        val translation =
            surfaceTranslationPx(
                currentPage = 3,
                currentPageOffsetFraction = -0.5f,
                settledPage = 3,
                pageHeightPx = 2000f,
            )
        assertEquals(1000f, translation)
    }

    @Test
    fun `translation stays continuous when currentPage flips past settledPage`() {
        // The regression this function exists to prevent. Past the halfway point
        // the pager reports currentPage = 4 with a negative offset rather than
        // currentPage = 3 with a large positive one. Measuring against
        // settledPage keeps the value continuous; measuring against currentPage
        // would snap the video a whole page sideways mid-drag.
        val justBefore =
            surfaceTranslationPx(
                currentPage = 3,
                currentPageOffsetFraction = 0.49f,
                settledPage = 3,
                pageHeightPx = 2000f,
            )
        val justAfter =
            surfaceTranslationPx(
                currentPage = 4,
                currentPageOffsetFraction = -0.49f,
                settledPage = 3,
                pageHeightPx = 2000f,
            )
        assertEquals(-980f, justBefore)
        assertEquals(-1020f, justAfter)
    }

    @Test
    fun `poster hides only on the settled page once the first frame has rendered`() {
        assertEquals(0f, posterAlphaTarget(isSettledPage = true, coverSurface = false))
    }

    @Test
    fun `poster covers the settled page until the first frame renders`() {
        assertEquals(1f, posterAlphaTarget(isSettledPage = true, coverSurface = true))
    }

    @Test
    fun `poster always covers a non-settled page`() {
        // Neighbours have no player bound, so their poster is the only content
        // they ever show — this is what keeps a cold page from flashing black.
        assertEquals(1f, posterAlphaTarget(isSettledPage = false, coverSurface = false))
        assertEquals(1f, posterAlphaTarget(isSettledPage = false, coverSurface = true))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew :feature:videos:impl:testDebugUnitTest --tests '*VideoFeedPresentationTest*'
```

Expected: FAIL — compilation error, `Unresolved reference: surfaceTranslationPx`.

- [ ] **Step 3: Write the implementation**

Create `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoFeedPresentation.kt`:

```kotlin
package net.kikin.nubecita.feature.videos.impl.ui

/**
 * Vertical offset, in pixels, to apply to the single persistent video surface so
 * it slides with the page it belongs to.
 *
 * The surface is measured against [settledPage] — the page whose player is
 * actually bound — and NOT against `currentPage`, which flips to the next page
 * at the halfway point of a drag and would snap the video a full page sideways
 * mid-gesture.
 *
 * Negative moves the surface up (dragging toward later pages).
 */
internal fun surfaceTranslationPx(
    currentPage: Int,
    currentPageOffsetFraction: Float,
    settledPage: Int,
    pageHeightPx: Float,
): Float {
    val scrollPosition = currentPage + currentPageOffsetFraction
    return (settledPage - scrollPosition) * pageHeightPx
}

/**
 * Target alpha for a page's poster.
 *
 * The poster renders *over* the video (the pager sits above the surface), so it
 * starts opaque and fades out to reveal the frame. Only the settled page has a
 * player bound, so every other page keeps its poster at full opacity — that is
 * what stops a cold page from showing black.
 */
internal fun posterAlphaTarget(
    isSettledPage: Boolean,
    coverSurface: Boolean,
): Float = if (isSettledPage && !coverSurface) 0f else 1f
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew :feature:videos:impl:testDebugUnitTest --tests '*VideoFeedPresentationTest*'
```

Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
./gradlew :feature:videos:impl:spotlessApply
git add feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoFeedPresentation.kt \
        feature/videos/impl/src/test/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoFeedPresentationTest.kt
git commit -m "feat(videos): pure slide and poster-alpha math for the vertical feed

Surface translation is measured against settledPage rather than
currentPage, which flips mid-drag and would snap the video a full page
sideways. Extracted as pure functions so the subtlety carries tests.

Refs: nubecita-zdv8.9"
```

---

### Task 2: Poster page composable + screenshot baselines

**Files:**
- Create: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoFeedPage.kt`
- Modify: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/VideoFeedTestTags.kt`
- Modify: `feature/videos/impl/src/test/kotlin/net/kikin/nubecita/feature/videos/impl/VideoFeedTestTagsTest.kt`
- Create: `feature/videos/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoFeedPageScreenshotTest.kt`

**Interfaces:**
- Consumes: `posterAlphaTarget` (Task 1) — used by the caller in Task 3, not here.
- Produces:
  - `internal fun VideoFeedPage(posterUrl: String?, aspectRatio: Float, posterAlpha: Float, modifier: Modifier = Modifier)`
  - `VideoFeedTestTags.POSTER: String = "video_feed_poster"`

- [ ] **Step 1: Write the failing test-tag pin**

Add to `VideoFeedTestTagsTest`, inside the existing `VideoFeedTestTagsTest` class:

```kotlin
    @Test
    fun `poster tag value is pinned to video_feed_poster`() {
        assertEquals("video_feed_poster", VideoFeedTestTags.POSTER)
    }
```

- [ ] **Step 2: Run it to verify it fails**

```bash
./gradlew :feature:videos:impl:testDebugUnitTest --tests '*VideoFeedTestTagsTest*'
```

Expected: FAIL — `Unresolved reference: POSTER`.

- [ ] **Step 3: Add the tag**

In `VideoFeedTestTags.kt`, add inside `object VideoFeedTestTags`:

```kotlin
    /** A single page's poster layer, which covers the video until its first frame. */
    const val POSTER: String = "video_feed_poster"
```

- [ ] **Step 4: Run it to verify it passes**

```bash
./gradlew :feature:videos:impl:testDebugUnitTest --tests '*VideoFeedTestTagsTest*'
```

Expected: PASS, 2 tests.

- [ ] **Step 5: Write the page composable**

Create `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoFeedPage.kt`:

```kotlin
package net.kikin.nubecita.feature.videos.impl.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.feature.videos.impl.VideoFeedTestTags

/**
 * One page of the vertical video feed.
 *
 * The video itself is NOT here: a single persistent surface lives behind the
 * pager and is shared by every page. This composable is the poster layer that
 * renders *over* that surface and fades out once the active player has a frame
 * — so a page is never black, and the swap between outgoing video, poster and
 * incoming video is covered at every instant.
 *
 * [aspectRatio] must be the same ratio the surface is using, or the crossfade
 * reads as a jump rather than a dissolve.
 *
 * Overlay chrome (author, caption, interactions, mute) lands in PR2 and composes
 * into this Box above the poster.
 */
@Composable
internal fun VideoFeedPage(
    posterUrl: String?,
    aspectRatio: Float,
    posterAlpha: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // graphicsLayer (not Modifier.alpha) so a crossfade only re-runs the
        // layer block — no recomposition or relayout per frame at 120hz.
        NubecitaAsyncImage(
            model = posterUrl,
            contentDescription = null,
            modifier =
                Modifier
                    .aspectRatio(aspectRatio)
                    .graphicsLayer { alpha = posterAlpha }
                    .testTag(VideoFeedTestTags.POSTER),
            contentScale = ContentScale.Fit,
        )
    }
}
```

Note: a null `posterUrl` is passed straight through to `NubecitaAsyncImage`, which renders its `fallback` painter. No branch is needed — that is exactly the degraded state the spec calls for.

- [ ] **Step 6: Write the screenshot test**

Create `feature/videos/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoFeedPageScreenshotTest.kt`:

```kotlin
package net.kikin.nubecita.feature.videos.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

private const val CANVAS_HEIGHT_DP = 600

/** Portrait clip, poster fully covering — the state every cold page opens in. */
@PreviewTest
@Preview(name = "poster-portrait-light", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Preview(
    name = "poster-portrait-dark",
    showBackground = true,
    heightDp = CANVAS_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun VideoFeedPagePortraitPreview() {
    NubecitaCanvasPreviewTheme {
        VideoFeedPage(posterUrl = null, aspectRatio = 9f / 16f, posterAlpha = 1f)
    }
}

/** Landscape clip — pins the letterbox bars the deferred blur fill will replace. */
@PreviewTest
@Preview(name = "poster-landscape-light", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Preview(
    name = "poster-landscape-dark",
    showBackground = true,
    heightDp = CANVAS_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun VideoFeedPageLandscapePreview() {
    NubecitaCanvasPreviewTheme {
        VideoFeedPage(posterUrl = null, aspectRatio = 16f / 9f, posterAlpha = 1f)
    }
}

/** Mid-crossfade, so a regression that breaks the alpha plumbing is visible. */
@PreviewTest
@Preview(name = "poster-midfade-light", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Preview(
    name = "poster-midfade-dark",
    showBackground = true,
    heightDp = CANVAS_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun VideoFeedPageMidFadePreview() {
    NubecitaCanvasPreviewTheme {
        VideoFeedPage(posterUrl = null, aspectRatio = 9f / 16f, posterAlpha = 0.5f)
    }
}
```

`posterUrl = null` in every preview is deliberate: layoutlib has no network, so a real URL would render the fallback painter anyway but non-deterministically. Passing null pins the same visual explicitly.

- [ ] **Step 7: Generate and inspect the baselines**

```bash
./gradlew :feature:videos:impl:updateScreenshots
```

Then **look at the generated PNGs** under `feature/videos/impl/src/screenshotTestDebug/reference/` before committing. Confirm: portrait fills the frame edge-to-edge, landscape shows black bars top and bottom, mid-fade is visibly translucent. A baseline is only worth committing if it shows what it claims to.

- [ ] **Step 8: Verify validation passes against the committed baselines**

```bash
./gradlew :feature:videos:impl:validateScreenshots
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
./gradlew :feature:videos:impl:spotlessApply
git add feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/ui/VideoFeedPage.kt \
        feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/VideoFeedTestTags.kt \
        feature/videos/impl/src/test/kotlin/net/kikin/nubecita/feature/videos/impl/VideoFeedTestTagsTest.kt \
        feature/videos/impl/src/screenshotTest \
        feature/videos/impl/src/screenshotTestDebug
git commit -m "feat(videos): poster layer for vertical feed pages

Poster renders over the shared surface and fades out on first frame, so
a cold page shows the poster rather than black. Baselines cover portrait,
landscape letterbox and mid-crossfade.

Refs: nubecita-zdv8.9"
```

---

### Task 3: Wire the surface — TextureView, slide, crossfade

**Files:**
- Modify: `feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/VideoFeedScreen.kt:86-128`

**Interfaces:**
- Consumes: `surfaceTranslationPx`, `posterAlphaTarget` (Task 1); `VideoFeedPage` (Task 2).
- Produces: nothing downstream.

- [ ] **Step 1: Add imports and the surface-type constant**

Add to the imports in `VideoFeedScreen.kt`:

```kotlin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import net.kikin.nubecita.feature.videos.impl.ui.VideoFeedPage
import net.kikin.nubecita.feature.videos.impl.ui.posterAlphaTarget
import net.kikin.nubecita.feature.videos.impl.ui.surfaceTranslationPx
```

Add at the bottom of the file, after `snapshotFlowSettledPage`:

```kotlin
/**
 * Surface backing for the feed's video.
 *
 * `TextureView` composites through the view hierarchy, so it translates and
 * alpha-blends exactly in step with the pager — a `SurfaceView`'s position is
 * owned by the window compositor and can visibly lag the app frame during a
 * drag. The cost is real: full-screen video goes through the GPU every frame
 * instead of a hardware overlay, which is a battery cost on the surface users
 * linger on longest. Deliberately isolated here so a battery pass can flip it
 * back to SURFACE_TYPE_SURFACE_VIEW as a one-line change. See design D1.
 */
private const val FEED_SURFACE_TYPE = SURFACE_TYPE_TEXTURE_VIEW

/** Crossfade duration, ms, from full poster to the decoded first frame. */
private const val POSTER_FADE_MS = 150
```

- [ ] **Step 2: Add `posterUrl` and `aspectRatio` to `VideoFeedItem`**

`VideoFeedItem` holds `post: PostUi` and `source: VideoSource`; the page needs the poster and ratio without the composable reaching into `EmbedUi`. This must land before the screen edit, which consumes both. Add to `VideoFeedContract.kt`, inside `data class VideoFeedItem`:

```kotlin
    /** Poster frame for this clip, if the embed declared one. */
    val posterUrl: String? get() = (post.embed as? EmbedUi.Video)?.posterUrl

    /** Declared frame ratio, available before any decode. Falls back to portrait. */
    val aspectRatio: Float get() = (post.embed as? EmbedUi.Video)?.aspectRatio ?: (9f / 16f)
```

Add the import `net.kikin.nubecita.data.models.EmbedUi` to `VideoFeedContract.kt`.

- [ ] **Step 3: Replace the `is VideoFeedStatus.Content` body**

Replace `VideoFeedScreen.kt:77-129` (the whole `is VideoFeedStatus.Content -> { … }` branch) with:

```kotlin
            is VideoFeedStatus.Content -> {
                // Open at the VM's initial active index (route.startIndex, e.g. from the
                // Trending carousel); rememberPagerState only reads initialPage on first use.
                val pagerState = rememberPagerState(initialPage = state.activeIndex, pageCount = { status.items.size })
                LaunchedEffect(pagerState) {
                    snapshotFlowSettledPage(pagerState) { settled ->
                        viewModel.handleEvent(VideoFeedEvent.ActiveIndexChanged(settled))
                    }
                }
                Box(contentModifier) {
                    // ONE persistent video surface for the whole feed, sitting behind the
                    // pager. Because it is never recreated as the active page changes,
                    // promoting a pooled player only re-binds it — there is no async
                    // surface-attach race and no black first frame. The pool guarantees
                    // exactly one active player, so a single surface is all the feed needs.
                    val settledItem = status.items.getOrNull(pagerState.settledPage)
                    // rememberPresentationState accepts a nullable player and re-observes on
                    // change, so it is called unconditionally — a conditional call would
                    // discard the state whenever the pool briefly has no active player.
                    // The instance survives player swaps (remember has no key), and
                    // coverSurface flips true on promotion and false on the next first
                    // frame: exactly the crossfade signal, with no extra bookkeeping.
                    val presentationState = rememberPresentationState(activePlayer)
                    val videoSize = presentationState.videoSizeDp
                    // Poster and surface MUST resolve to the same ratio or the crossfade
                    // reads as a jump. Prefer the decoded size once known; fall back to the
                    // embed's declared ratio, which is available before any decode (D4).
                    val settledAspectRatio =
                        if (videoSize != null && videoSize.width > 0f && videoSize.height > 0f) {
                            videoSize.width / videoSize.height
                        } else {
                            settledItem?.aspectRatio ?: DEFAULT_ASPECT_RATIO
                        }

                    activePlayer?.let { player ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            PlayerSurface(
                                player = player,
                                surfaceType = FEED_SURFACE_TYPE,
                                modifier =
                                    Modifier
                                        .aspectRatio(settledAspectRatio)
                                        // Deferred read: a swipe re-runs only this layer
                                        // block, never composition or layout. That is what
                                        // holds the gesture at 120hz.
                                        .graphicsLayer {
                                            translationY =
                                                surfaceTranslationPx(
                                                    currentPage = pagerState.currentPage,
                                                    currentPageOffsetFraction = pagerState.currentPageOffsetFraction,
                                                    settledPage = pagerState.settledPage,
                                                    pageHeightPx = size.height,
                                                )
                                        },
                            )
                        }
                    }
                    // The pager is a transparent gesture + snapping layer on top. Its pages
                    // carry the poster (and, from PR2, the chrome), so the surface behind
                    // shows through wherever the poster has faded out. A stable per-item key
                    // keeps page state aligned as the feed paginates (appends).
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize().testTag(VideoFeedTestTags.PAGER),
                        key = { index -> status.items[index].post.id },
                    ) { page ->
                        val item = status.items[page]
                        val isSettled = page == pagerState.settledPage
                        val targetAlpha =
                            posterAlphaTarget(
                                isSettledPage = isSettled,
                                coverSurface = presentationState.coverSurface,
                            )
                        val posterAlpha by animateFloatAsState(
                            targetValue = targetAlpha,
                            animationSpec = tween(durationMillis = POSTER_FADE_MS),
                            label = "VideoFeedPoster-alpha",
                        )
                        VideoFeedPage(
                            posterUrl = item.posterUrl,
                            aspectRatio = if (isSettled) settledAspectRatio else item.aspectRatio,
                            posterAlpha = posterAlpha,
                        )
                    }
                }
            }
```

Add alongside `POSTER_FADE_MS`:

```kotlin
/** Fallback frame ratio before any size is known — portrait, the common case. */
private const val DEFAULT_ASPECT_RATIO = 9f / 16f
```

Note `size.height` inside `graphicsLayer` is the layer's own measured height in px, which for a full-bleed page equals the page height — no `LocalDensity` conversion is needed.

- [ ] **Step 4: Verify it compiles and all unit tests pass**

```bash
./gradlew :feature:videos:impl:assembleDebug :feature:videos:impl:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Verify screenshots still validate**

```bash
./gradlew :feature:videos:impl:validateScreenshots
```

Expected: PASS — Task 3 touches no previewed composable.

- [ ] **Step 6: Commit**

```bash
./gradlew :feature:videos:impl:spotlessApply
git add feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/VideoFeedScreen.kt \
        feature/videos/impl/src/main/kotlin/net/kikin/nubecita/feature/videos/impl/VideoFeedContract.kt
git commit -m "feat(videos): slide the video with the swipe and crossfade the poster

Surface switches to TextureView so it translates in step with the pager,
offset by the settled page's displacement via a deferred graphicsLayer
read. Each page's poster fades out on first frame.

Refs: nubecita-zdv8.9"
```

---

### Task 4: Device verification

Neither the slide nor the crossfade can be asserted by any automated test in this repo — the emulator's goldfish decoder cannot render the bundled clips. This task is manual and is **not optional**: it is the only thing standing between this PR and a regression of the black-first-frame bug.

**Files:** none.

- [ ] **Step 1: Build and install the bench flavor**

```bash
./gradlew :app:installBenchDebug
```

The bench build is fully offline (bundled `asset:///video/clip-*.mp4`, including landscape 16:9 clips) and skips OAuth.

- [ ] **Step 2: Confirm the device is present and unlocked**

```bash
adb devices
```

The Pixel 10 Pro XL auto-locks; ask the user to unlock it if the launch shows a lock screen.

- [ ] **Step 3: Launch the video feed and capture**

Navigate to Discover → Trending Videos → open the feed. Capture with:

```bash
adb exec-out screencap -p > /tmp/videofeed.png
```

- [ ] **Step 4: Verify each claim against what you actually see**

Check, and write down the result of each — do not assume:

1. Opening a cold page shows the **poster**, never a black frame.
2. Mid-swipe, the video moves **with** its page rather than staying put.
3. On settle, there is no visible flash of the outgoing video at the new position.
4. A landscape 16:9 clip letterboxes with the poster and video on **identical** bounds — no jump at the moment the poster fades.
5. Scrolling several pages does not accumulate any drift in the surface position.

- [ ] **Step 5: Report honestly**

If any of the five fails, that is a finding to fix or surface, not to smooth over. Item 2 failing means the `TextureView` change did not achieve its purpose and the PR's premise needs revisiting. Item 4 failing means the aspect-ratio resolution in Task 3 Step 3 is wrong.

---

## PR1 completion

Open the PR with `Refs: nubecita-zdv8.9` — **not** `Closes:`, since PRs 2 and 3 remain. Post `/gemini review` after pushing code (Gemini only reviews automatically at PR open, and this PR opens with code). Reply to and resolve every review thread; the repo blocks merge on unresolved threads.

---

## PRs 2 and 3 (outline — plan after PR1 is verified)

Deliberately not detailed yet. Both depend on how the slide and crossfade actually look on device, and writing exact chrome layout code now would be inventing detail that the device pass will change.

**PR2 — chrome + interactions.** Add `PostSurface.Videos("videos")` to `:core:analytics`. Wire `VideoFeedViewModel : …, PostInteractionHandler by handler` with `bind(PostSurface.Videos, viewModelScope)`, keeping the `postInteractionsCache.state → items` read-merge and seed (D5 — dropping it has regressed once before, so it gets its own test). Add `rememberVideoFeedInteractions` mirroring `FeedInteractions.kt`. Build the right-rail chrome into `VideoFeedPage` above the poster. Add `VideoFeedEffect.NavigateTo(target: NavKey)`, collected into `LocalMainShellNavState`. ~17 string keys × 3 locales; toggles take a static noun label on the icon's `contentDescription`, one-shot cells take `onClickLabel`. New deps: `:core:post-interactions`, `:core:post-interactions-ui`, `:core:analytics` (`checkSortDependencies` enforces ordering). Run the compose-expert skill — this diff adds `@Composable`s. Bench-smoke the DI/VM wiring.

**PR3 — gestures + progress.** `detectTapGestures(onTap = togglePlayPause, onDoubleTap = like)`, double-tap likes only and never unlikes. `VideoFeedEvent.TogglePlayPause`. Draw-phase progress: a `withFrameNanos` loop writing a `MutableFloatState` read only by `drawBehind`.
