# Profile Collapsible Toolbar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a threshold-fade `TopAppBar` to `:feature:profile:impl` that reserves the status-bar inset (fixing the camera-cutout overlap surfaced on PR #158) and cross-fades a display-name + handle title over the bold-hero gradient as the user scrolls past the hero.

**Architecture:** A new `ProfileTopBar` composable lives inside the Scaffold that `ProfileScreenContent` already owns. It reads `LazyListState` (already hoisted) via `derivedStateOf` to compute a 0..1 alpha applied to its backdrop and title. The backdrop color is sampled from the existing `:designsystem/hero/BoldHeroGradient` cache (zero extra Palette work). The sticky pills below get the same gradient color so the bar + pills read as one continuous header surface once the hero is gone.

**Tech Stack:** Jetpack Compose · Material 3 · `androidx.compose.material3.TopAppBar` (small) + `Scaffold` · `LazyListState` derived state · existing `:designsystem/hero/BoldHeroGradient` Palette cache · JUnit 5 for unit tests · Android Screenshot Test plugin (`*screenshotTest*` source set) for visual regression.

---

## Spec reference

Design doc: `docs/superpowers/specs/2026-05-13-profile-collapsible-toolbar-design.md`. Re-read it before starting; this plan implements the design verbatim.

## File structure

| File | Status | Responsibility |
|---|---|---|
| `feature/profile/impl/src/main/kotlin/.../ui/ProfileTopBar.kt` | NEW | The collapsing bar composable + the pure `computeBarAlpha` helper. ~120 lines. |
| `feature/profile/impl/src/test/kotlin/.../ui/ProfileTopBarTest.kt` | NEW | JUnit 5 unit tests for `computeBarAlpha`. |
| `feature/profile/impl/src/screenshotTest/kotlin/.../ui/ProfileTopBarScreenshotTest.kt` | NEW | 4 isolated bar variants × 2 themes = 8 baselines. |
| `feature/profile/impl/src/main/kotlin/.../ProfileScreenContent.kt` | MODIFY | Add `topBar = { ProfileTopBar(...) }` to the existing Scaffold; add `onBack` parameter; wrap `ProfilePillTabs` inside `stickyHeader` in a Box with the gradient backdrop; add `Modifier.consumeWindowInsets(padding)` on the LazyColumn. |
| `feature/profile/impl/src/main/kotlin/.../ProfileScreen.kt` | MODIFY | Read `LocalMainShellNavState.current`; compute `onBack` from `route.handle != null`; pass it into `ProfileScreenContent`. |
| `feature/profile/impl/src/screenshotTest/kotlin/.../ProfileScreenContentScreenshotTest.kt` | MODIFY | Add `listStateSeed: LazyListState?` parameter on the host; add two new "scrolled-away" fixtures (light + dark). |
| `feature/profile/impl/src/screenshotTestDebug/reference/.../*.png` | REGEN | All existing baselines shift by `statusBarInset + 64dp`; one regen pass via `:feature:profile:impl:updateDebugScreenshotTest`. |

## Conventions reminder

- **Branch already exists**: `feat/nubecita-1tc-feature-profile-impl-scroll-collapsing-hero` (created when the spec was committed).
- **Conventional commits with `Refs: nubecita-1tc` footer.** `Closes:` goes in the PR body only — never in individual commits, or squash-merge double-closes.
- **Pre-commit hooks gate spotless + ktlint + commitlint.** Never `--no-verify`. If a hook fails, fix the underlying issue.
- **Stability annotation precedent**: `:data:models/EmbedUi.kt` and the `ViewerRelationship` change in PR #167 — sealed interfaces + UI data classes get `@Immutable`.

---

## Task 1: Pure `computeBarAlpha` + unit tests (TDD)

**Files:**
- Create: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileTopBar.kt`
- Create: `feature/profile/impl/src/test/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileTopBarTest.kt`

- [ ] **Step 1: Write the failing test file**

`feature/profile/impl/src/test/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileTopBarTest.kt`:

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for [computeBarAlpha]. The function maps the LazyListState's
 * (firstVisibleItemIndex, firstVisibleItemScrollOffset) and the hero's
 * fade-window height into a 0..1 alpha applied to the bar's backdrop +
 * title.
 *
 * Threshold semantics:
 * - hero unscrolled (index=0, offset=0)            → alpha=0 (bar invisible)
 * - hero scrolled half-window                       → alpha=0.5
 * - hero scrolled full window                       → alpha=1 (clamp)
 * - hero scrolled past window (overshoot)           → alpha=1 (clamp)
 * - hero fully scrolled away (index>0)              → alpha=1
 * - hero hasn't measured (fadeWindowPx=0)           → alpha=0 (avoid divide-by-zero)
 */
internal class ProfileTopBarTest {
    @Test
    fun `unscrolled hero yields alpha 0`() {
        assertEquals(0f, computeBarAlpha(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0, fadeWindowPx = 100))
    }

    @Test
    fun `mid-window scroll yields linear-lerped alpha`() {
        assertEquals(0.5f, computeBarAlpha(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 50, fadeWindowPx = 100))
    }

    @Test
    fun `full-window scroll yields alpha 1`() {
        assertEquals(1f, computeBarAlpha(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 100, fadeWindowPx = 100))
    }

    @Test
    fun `overshoot beyond window clamps to alpha 1`() {
        assertEquals(1f, computeBarAlpha(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 250, fadeWindowPx = 100))
    }

    @Test
    fun `hero scrolled past first item yields alpha 1`() {
        assertEquals(1f, computeBarAlpha(firstVisibleItemIndex = 1, firstVisibleItemScrollOffset = 0, fadeWindowPx = 100))
    }

    @Test
    fun `unmeasured hero (fadeWindowPx 0) yields alpha 0`() {
        assertEquals(0f, computeBarAlpha(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0, fadeWindowPx = 0))
    }

    @Test
    fun `unmeasured hero with positive offset still yields alpha 0`() {
        // Defensive: if the hero hasn't measured but the LazyColumn somehow reports
        // a positive offset, we don't want a divide-by-zero or NaN to leak through.
        assertEquals(0f, computeBarAlpha(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 50, fadeWindowPx = 0))
    }

    @Test
    fun `negative fadeWindowPx defensively yields alpha 0`() {
        // Shouldn't be reachable in practice (fadeMultiplier * size is always non-negative),
        // but guard against future changes that could send a negative value through.
        assertEquals(0f, computeBarAlpha(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 50, fadeWindowPx = -10))
    }
}
```

- [ ] **Step 2: Run the test, confirm compile failure**

```bash
./gradlew :feature:profile:impl:testDebugUnitTest --tests "net.kikin.nubecita.feature.profile.impl.ui.ProfileTopBarTest"
```

Expected: **FAIL** — unresolved reference `computeBarAlpha`.

- [ ] **Step 3: Create `ProfileTopBar.kt` with just the helper (no composable yet)**

`feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileTopBar.kt`:

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

/**
 * Multiplier applied to the hero's measured height to derive the
 * fade window. With `0.5f` the bar is fully visible by the time the
 * hero is half-scrolled — empirically that's "around when the avatar
 * leaves the viewport."
 *
 * Tunable during on-device verification; private to keep the surface
 * area of [ProfileTopBar] minimal.
 */
internal const val PROFILE_BAR_FADE_MULTIPLIER: Float = 0.5f

/**
 * Maps the LazyColumn's scroll position into a 0..1 alpha for the
 * collapsing top bar.
 *
 * - Hero scrolled past the first item ([firstVisibleItemIndex] > 0) → 1f.
 * - Hero hasn't measured yet ([fadeWindowPx] <= 0) → 0f (the bar stays
 *   invisible until the hero reports a height; without this guard,
 *   the divide produces `Infinity`).
 * - Otherwise → `(offset / window).coerceIn(0f, 1f)` — linear lerp
 *   from invisible at 0 px scrolled to fully visible at `fadeWindowPx`
 *   pixels scrolled.
 *
 * Pure function so it's unit-testable without a Compose runtime. The
 * caller wraps this in `derivedStateOf { ... }` so the bar only
 * recomposes when the discrete alpha actually changes.
 */
internal fun computeBarAlpha(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    fadeWindowPx: Int,
): Float =
    when {
        firstVisibleItemIndex > 0 -> 1f
        fadeWindowPx <= 0 -> 0f
        else -> (firstVisibleItemScrollOffset.toFloat() / fadeWindowPx).coerceIn(0f, 1f)
    }
```

- [ ] **Step 4: Run the tests; confirm all 8 pass**

```bash
./gradlew :feature:profile:impl:testDebugUnitTest --tests "net.kikin.nubecita.feature.profile.impl.ui.ProfileTopBarTest"
```

Expected: **PASS** — 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileTopBar.kt \
        feature/profile/impl/src/test/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileTopBarTest.kt
git commit -m "$(cat <<'EOF'
feat(feature/profile): add computeBarAlpha helper for collapsing toolbar

Pure function mapping LazyListState scroll position into the 0..1 alpha
that drives the upcoming ProfileTopBar's backdrop + title cross-fade.
Unit-tested in isolation so the threshold semantics (unmeasured hero,
mid-window lerp, overshoot clamp, past-first-item, fadeWindowPx=0
guard) are locked in before the composable is wired.

Refs: nubecita-1tc
EOF
)"
```

---

## Task 2: `ProfileTopBar` composable

**Files:**
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileTopBar.kt`

- [ ] **Step 1: Add the composable below the existing helper**

Append to `ProfileTopBar.kt` (after the `computeBarAlpha` function):

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.derivedStateOf
import net.kikin.nubecita.designsystem.component.NubecitaIcon
import net.kikin.nubecita.designsystem.component.NubecitaIconName
import net.kikin.nubecita.designsystem.component.mirror
import net.kikin.nubecita.designsystem.hero.rememberBoldHeroGradient
import net.kikin.nubecita.feature.profile.impl.ProfileHeaderUi
import net.kikin.nubecita.feature.profile.impl.R

/**
 * Collapsing top bar for the Profile screen.
 *
 * Layout invariant: the bar always occupies the topBar slot of the
 * enclosing `Scaffold`. The status-bar inset reservation is therefore
 * unconditional — whether or not the title is visible, the bar's
 * height (status bar inset + 64dp small TopAppBar height) keeps the
 * scrolling content from drawing under the system clock / cutout.
 * This is what fixes the bd's camera-cutout-overlap reproducer.
 *
 * Visibility: the [header]-derived backdrop and title are
 * alpha-modulated by [computeBarAlpha] applied to [listState]. While
 * the hero is in view, alpha is 0 → the bar reads as a transparent
 * inset reservation. As the hero scrolls past its half-height
 * threshold, alpha climbs to 1 → the bar's gradient backdrop and
 * "Display name / @handle" title cross-fade in.
 *
 * When [onBack] is non-null the bar paints a back-arrow navigation
 * icon (also alpha-modulated). Null = own-profile root, no nav icon.
 *
 * Backdrop: [rememberBoldHeroGradient] with the same banner +
 * avatarHue the hero uses → cache hit, same `top` color, single
 * Palette extraction shared between bar and hero.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileTopBar(
    header: ProfileHeaderUi?,
    listState: LazyListState,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // Alpha derives from the LazyColumn's scroll + the first item's measured
    // height. derivedStateOf so the bar only recomposes when the discrete alpha
    // changes — not on every scroll frame.
    val alpha by remember(listState) {
        derivedStateOf {
            val firstItemSize =
                listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == 0 }
                    ?.size ?: 0
            val fadeWindowPx = (firstItemSize * PROFILE_BAR_FADE_MULTIPLIER).toInt()
            computeBarAlpha(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                fadeWindowPx = fadeWindowPx,
            )
        }
    }

    // Gradient sample shared with ProfileHero (cache hit when the hero has
    // already measured + extracted). Falls back to the avatarHue-derived
    // gradient when no banner; transparent when header itself is still null
    // (loading state).
    val gradientTop =
        if (header != null) {
            rememberBoldHeroGradient(banner = header.bannerUrl, avatarHue = header.avatarHue).top
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        }

    // Hoist the TopAppBarColors object so it's only re-allocated when alpha
    // or the gradient stops change — not on every recomposition triggered by
    // an unrelated parent.
    val barColors =
        remember(gradientTop, alpha) {
            TopAppBarDefaults.topAppBarColors(containerColor = gradientTop.copy(alpha = alpha))
        }

    TopAppBar(
        title = {
            if (header != null) {
                Column(modifier = Modifier.alpha(alpha)) {
                    Text(
                        text = header.displayName ?: header.handle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    Text(
                        text = "@${header.handle}",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.alpha(alpha)) {
                    NubecitaIcon(
                        name = NubecitaIconName.ArrowBack,
                        contentDescription = stringResource(R.string.profile_topbar_back_content_description),
                        filled = true,
                        modifier = Modifier.mirror(),
                    )
                }
            }
        },
        colors = barColors,
        modifier = modifier,
    )
}
```

- [ ] **Step 2: Add the new string resource**

Append to `feature/profile/impl/src/main/res/values/strings.xml`, just below the existing `profile_avatar_content_description` entry (or anywhere in the file — pick the section that matches existing organization):

```xml
<string name="profile_topbar_back_content_description">Go back</string>
```

- [ ] **Step 3: Compile-only check**

```bash
./gradlew :feature:profile:impl:compileDebugKotlin
```

Expected: **BUILD SUCCESSFUL.** No `unresolved reference`. If `NubecitaIcon` / `NubecitaIconName` / `mirror` imports don't resolve, grep for the actual import path used in `PostDetailScreen.kt`'s back button and adjust.

- [ ] **Step 4: Re-run unit tests to confirm no regression**

```bash
./gradlew :feature:profile:impl:testDebugUnitTest
```

Expected: **PASS.** (computeBarAlpha tests still green; no new tests yet for the composable.)

- [ ] **Step 5: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileTopBar.kt \
        feature/profile/impl/src/main/res/values/strings.xml
git commit -m "$(cat <<'EOF'
feat(feature/profile): add ProfileTopBar collapsing composable

Threshold-fade TopAppBar that reserves the status-bar inset
unconditionally and cross-fades a (displayName / @handle) title +
gradient-sampled backdrop as the hero scrolls past its half-height
threshold. Wires into the existing rememberBoldHeroGradient cache so
the bar's color tracks the hero's. Optional back-arrow nav icon is
alpha-modulated alongside the rest of the bar.

Refs: nubecita-1tc
EOF
)"
```

---

## Task 3: `ProfileTopBar` screenshot baselines

**Files:**
- Create: `feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileTopBarScreenshotTest.kt`

- [ ] **Step 1: Create the screenshot test file**

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.profile.impl.ProfileHeaderUi

/**
 * Screenshot baselines for [ProfileTopBar]. Four α stops × two themes
 * = 8 baselines:
 *
 * - α = 0.0, no nav icon (transparent inset reservation)
 * - α = 0.5, no nav icon (mid cross-fade)
 * - α = 1.0, no nav icon (own-profile root, fully visible)
 * - α = 1.0, with nav icon (other-user pushed, fully visible)
 *
 * The α value is forced via the LazyListState seed:
 * - alpha=0   : firstVisibleItemIndex=0, firstVisibleItemScrollOffset=0
 * - alpha=0.5 : firstVisibleItemIndex=0, firstVisibleItemScrollOffset=N/2
 *               where N is the actual measured first-item size at preview time.
 *               In practice we seed index=1 and rely on the helper math; for
 *               mid-fade we use a custom seed wrapper (see HostBox below).
 * - alpha=1   : firstVisibleItemIndex=1, firstVisibleItemScrollOffset=0
 *
 * Because the alpha derives from `listState.layoutInfo.visibleItemsInfo[0].size`
 * which is 0 at preview time (no LazyColumn host), we wrap the bar in a
 * minimal LazyColumn-free box that fakes the listState to a sentinel state
 * matching each α stop directly. See `previewListState` helper.
 */
private val SAMPLE_HEADER =
    ProfileHeaderUi(
        did = "did:plc:alice",
        handle = "alice.bsky.social",
        displayName = "Alice",
        avatarUrl = null,
        bannerUrl = null,
        avatarHue = 217,
        bio = null,
        location = null,
        website = null,
        joinedDisplay = null,
        postsCount = 0L,
        followersCount = 0L,
        followsCount = 0L,
    )

@PreviewTest
@Preview(name = "topbar-alpha0-light", showBackground = true, heightDp = 80)
@Preview(name = "topbar-alpha0-dark", showBackground = true, heightDp = 80, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileTopBarAlphaZeroScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        // The default LazyListState (no items measured) produces alpha=0
        // because firstItemSize defaults to 0 → fadeWindowPx=0 → returns 0f.
        ProfileTopBar(header = SAMPLE_HEADER, listState = LazyListState(), onBack = null)
    }
}

@PreviewTest
@Preview(name = "topbar-alpha-half-light", showBackground = true, heightDp = 80)
@Preview(name = "topbar-alpha-half-dark", showBackground = true, heightDp = 80, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileTopBarAlphaHalfScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        // For mid-fade we render a fixture variant: extract ProfileTopBar's
        // body into a `ProfileTopBarPreviewable` overload that takes alpha
        // directly, OR — simpler — construct a LazyListState with index=0,
        // offset=50 and rely on the helper to mid-lerp once the host
        // measures the first item. Layoutlib won't run the LazyColumn, so
        // alpha would stay 0. We need the alpha-injected variant.
        //
        // See Step 2 below: the implementation introduces an internal
        // overload `ProfileTopBar(header, alpha, onBack)` that the
        // listState-driven public composable delegates to. The screenshot
        // tests use the alpha-driven overload directly.
        ProfileTopBar(header = SAMPLE_HEADER, alpha = 0.5f, onBack = null)
    }
}

@PreviewTest
@Preview(name = "topbar-alpha1-light", showBackground = true, heightDp = 80)
@Preview(name = "topbar-alpha1-dark", showBackground = true, heightDp = 80, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileTopBarAlphaOneScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ProfileTopBar(header = SAMPLE_HEADER, alpha = 1f, onBack = null)
    }
}

@PreviewTest
@Preview(name = "topbar-alpha1-back-light", showBackground = true, heightDp = 80)
@Preview(name = "topbar-alpha1-back-dark", showBackground = true, heightDp = 80, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileTopBarAlphaOneWithBackScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ProfileTopBar(header = SAMPLE_HEADER, alpha = 1f, onBack = {})
    }
}
```

- [ ] **Step 2: Refactor `ProfileTopBar.kt` to expose an alpha-driven internal overload**

The screenshot tests need to drive `alpha` directly (LazyColumn doesn't render in Layoutlib so the `listState`-driven path is stuck at alpha=0). Refactor the composable into two functions:

Open `ProfileTopBar.kt`. Replace the existing `ProfileTopBar(header, listState, onBack, modifier)` body with a thin wrapper that computes alpha and delegates to a private overload. Final shape:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileTopBar(
    header: ProfileHeaderUi?,
    listState: LazyListState,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val alpha by remember(listState) {
        derivedStateOf {
            val firstItemSize =
                listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == 0 }
                    ?.size ?: 0
            val fadeWindowPx = (firstItemSize * PROFILE_BAR_FADE_MULTIPLIER).toInt()
            computeBarAlpha(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                fadeWindowPx = fadeWindowPx,
            )
        }
    }
    ProfileTopBar(header = header, alpha = alpha, onBack = onBack, modifier = modifier)
}

/**
 * Alpha-driven overload — the screenshot baselines render this directly
 * with hard-coded α values. The listState-driven public overload is a
 * thin computeBarAlpha + derivedStateOf wrapper around this body.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileTopBar(
    header: ProfileHeaderUi?,
    alpha: Float,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val gradientTop =
        if (header != null) {
            rememberBoldHeroGradient(banner = header.bannerUrl, avatarHue = header.avatarHue).top
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        }
    val barColors =
        remember(gradientTop, alpha) {
            TopAppBarDefaults.topAppBarColors(containerColor = gradientTop.copy(alpha = alpha))
        }
    TopAppBar(
        title = {
            if (header != null) {
                Column(modifier = Modifier.alpha(alpha)) {
                    Text(
                        text = header.displayName ?: header.handle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    Text(
                        text = "@${header.handle}",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.alpha(alpha)) {
                    NubecitaIcon(
                        name = NubecitaIconName.ArrowBack,
                        contentDescription = stringResource(R.string.profile_topbar_back_content_description),
                        filled = true,
                        modifier = Modifier.mirror(),
                    )
                }
            }
        },
        colors = barColors,
        modifier = modifier,
    )
}
```

- [ ] **Step 3: Compile-only check**

```bash
./gradlew :feature:profile:impl:compileDebugKotlin
```

Expected: **BUILD SUCCESSFUL.**

- [ ] **Step 4: Record screenshot baselines**

```bash
./gradlew :feature:profile:impl:updateDebugScreenshotTest
```

Expected: **BUILD SUCCESSFUL.** 8 new PNG files appear under `feature/profile/impl/src/screenshotTestDebug/reference/.../ProfileTopBarScreenshotTestKt/`.

- [ ] **Step 5: Visually inspect at least 2 baselines**

Open the alpha=0 and alpha=1-with-back baselines (use the Read tool on the PNG path). Verify:
- alpha=0: bar is fully transparent (just status-bar inset reservation visible).
- alpha=1-with-back: bar shows gradient color, "Alice / @alice.bsky.social" title, back arrow on the start side.

If the visuals are off, debug before proceeding (don't commit broken baselines).

- [ ] **Step 6: Validate**

```bash
./gradlew :feature:profile:impl:validateDebugScreenshotTest
```

Expected: **BUILD SUCCESSFUL.** All 8 new baselines match.

- [ ] **Step 7: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileTopBar.kt \
        feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileTopBarScreenshotTest.kt \
        feature/profile/impl/src/screenshotTestDebug/reference/net/kikin/nubecita/feature/profile/impl/ui/ProfileTopBarScreenshotTestKt/
git commit -m "$(cat <<'EOF'
feat(feature/profile): screenshot baselines for ProfileTopBar α stops

Lock in the four α stops (0, 0.5, 1 no-back, 1 with-back) × light + dark
themes. Refactor ProfileTopBar into a thin listState-driven public
overload that delegates to an alpha-driven internal overload, since
Layoutlib won't run the LazyColumn host and the listState path stalls
at alpha=0 in screenshot context.

Refs: nubecita-1tc
EOF
)"
```

---

## Task 4: Wire `ProfileTopBar` into `ProfileScreenContent`

**Files:**
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenContent.kt`

- [ ] **Step 1: Add `onBack` parameter and the topBar slot**

Open `ProfileScreenContent.kt`. Add `onBack: (() -> Unit)?` to the parameter list. Pass it into the new `topBar` slot of the existing Scaffold. Also add `Modifier.consumeWindowInsets(padding)` on the LazyColumn.

```kotlin
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ProfileScreenContent(
    state: ProfileScreenViewState,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    postCallbacks: PostCallbacks,
    onEvent: (ProfileEvent) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val pillTabs = rememberProfilePillTabs()
    val activeTabIsRefreshing = state.activeTabIsRefreshing()
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ProfileTopBar(
                header = state.header,
                listState = listState,
                onBack = onBack,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = activeTabIsRefreshing,
            onRefresh = { onEvent(ProfileEvent.Refresh) },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .consumeWindowInsets(padding),
                contentPadding = padding,
            ) {
                // existing item("hero"), stickyHeader("tabs"), tab body — unchanged for now
                // (Task 5 will wrap the pills inside the stickyHeader)
                item(key = "hero", contentType = "hero") { /* unchanged */ }
                stickyHeader(key = "tabs", contentType = "tabs") { /* unchanged */ }
                /* ... unchanged when block ... */
            }
        }
    }
    /* unchanged pagination LaunchedEffect */
}
```

Add the necessary imports at the top:

```kotlin
import androidx.compose.foundation.layout.consumeWindowInsets
import net.kikin.nubecita.feature.profile.impl.ui.ProfileTopBar
```

- [ ] **Step 2: Compile-only check**

```bash
./gradlew :feature:profile:impl:compileDebugKotlin
```

Expected: **BUILD FAILED** — `ProfileScreen.kt` still calls `ProfileScreenContent` without `onBack`. Fix-forward in Task 6, but for now we need to compile. Pass a temporary `onBack = null` from ProfileScreen:

Open `ProfileScreen.kt`, find the `ProfileScreenContent(...)` call site, add `onBack = null` to the argument list. (Task 6 replaces this with the real wiring.)

- [ ] **Step 3: Compile-only check, again**

```bash
./gradlew :feature:profile:impl:compileDebugKotlin
```

Expected: **BUILD SUCCESSFUL.**

- [ ] **Step 4: Run unit tests**

```bash
./gradlew :feature:profile:impl:testDebugUnitTest
```

Expected: **PASS.** (No new failures — `ProfileScreenContent` is screenshot-tested, not unit-tested.)

- [ ] **Step 5: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenContent.kt \
        feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreen.kt
git commit -m "$(cat <<'EOF'
feat(feature/profile): mount ProfileTopBar in the existing Scaffold

Add the topBar slot and onBack parameter to ProfileScreenContent;
add Modifier.consumeWindowInsets(padding) on the LazyColumn so the
existing contentPadding flow doesn't double-count the bar's reserved
status-bar inset. ProfileScreen passes onBack = null pending Task 6,
which wires the real MainShellNavState-driven callback.

Refs: nubecita-1tc
EOF
)"
```

---

## Task 5: Gradient backdrop on the sticky pills

**Files:**
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenContent.kt`

- [ ] **Step 1: Wrap `ProfilePillTabs` inside `stickyHeader` with the gradient backdrop**

Find the `stickyHeader(key = "tabs", contentType = "tabs") { ProfilePillTabs(...) }` block. Replace its body with a `Box` that paints the gradient-sampled backdrop:

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import net.kikin.nubecita.designsystem.hero.rememberBoldHeroGradient

// inside the LazyColumn:
stickyHeader(key = "tabs", contentType = "tabs") {
    val pillsBackdrop =
        if (state.header != null) {
            rememberBoldHeroGradient(
                banner = state.header.bannerUrl,
                avatarHue = state.header.avatarHue,
            ).top
        } else {
            Color.Transparent
        }
    Box(modifier = Modifier.background(pillsBackdrop)) {
        ProfilePillTabs(
            tabs = pillTabs,
            selectedValue = state.selectedTab,
            onSelect = { tab -> onEvent(ProfileEvent.TabSelected(tab)) },
        )
    }
}
```

- [ ] **Step 2: Compile-only check**

```bash
./gradlew :feature:profile:impl:compileDebugKotlin
```

Expected: **BUILD SUCCESSFUL.**

- [ ] **Step 3: Run unit tests**

```bash
./gradlew :feature:profile:impl:testDebugUnitTest
```

Expected: **PASS.**

- [ ] **Step 4: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenContent.kt
git commit -m "$(cat <<'EOF'
feat(feature/profile): paint sticky pills with the bold-hero gradient

Wraps the ProfilePillTabs call inside its stickyHeader in a Box whose
background is rememberBoldHeroGradient(...).top — the same color the
hero is painting at its top edge and the bar is painting at its
bottom. Bar + pills read as one continuous header surface once the
hero has scrolled away. Cache hit, no extra Palette work.

Refs: nubecita-1tc
EOF
)"
```

---

## Task 6: Wire `onBack` through `ProfileScreen`

**Files:**
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreen.kt`

- [ ] **Step 1: Read `LocalMainShellNavState` and derive `onBack`**

Open `ProfileScreen.kt`. Add the import:

```kotlin
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
```

Inside the `ProfileScreen` composable body, before the `ProfileScreenContent` call, derive the back callback. Other-user profiles (`state.handle != null`) get a real callback; own-profile root (handle == null) gets null.

```kotlin
val mainShellNavState = LocalMainShellNavState.current
val onBack: (() -> Unit)? =
    if (state.handle != null) {
        { mainShellNavState.removeLast() }
    } else {
        null
    }

ProfileScreenContent(
    state = state,
    listState = listState,
    snackbarHostState = snackbarHostState,
    postCallbacks = callbacks,
    onEvent = viewModel::handleEvent,
    onBack = onBack,
    modifier = modifier,
)
```

- [ ] **Step 2: Compile-only check**

```bash
./gradlew :feature:profile:impl:compileDebugKotlin
```

Expected: **BUILD SUCCESSFUL.**

- [ ] **Step 3: Spotless + lint**

```bash
./gradlew :feature:profile:impl:spotlessCheck :feature:profile:impl:lintDebug
```

Expected: **BUILD SUCCESSFUL.**

- [ ] **Step 4: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreen.kt
git commit -m "$(cat <<'EOF'
feat(feature/profile): wire ProfileTopBar's back arrow to MainShellNavState

Other-user profile routes (state.handle != null) get a real onBack
callback that calls MainShellNavState.removeLast(); the Profile-tab
root (state.handle == null) gets null so the bar paints no nav icon.
Mirrors the back-arrow conditionality the bd specifies.

Refs: nubecita-1tc
EOF
)"
```

---

## Task 7: Add scrolled-state screenshot fixtures

**Files:**
- Modify: `feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenContentScreenshotTest.kt`

- [ ] **Step 1: Expose a `listStateSeed` parameter on the screenshot host**

Open `ProfileScreenContentScreenshotTest.kt`. Locate the `ProfileScreenContentHost` (or similarly-named) helper that the existing `@PreviewTest` composables call. Add a `listStateSeed: LazyListState? = null` parameter and pass it into `ProfileScreenContent` (use the seed when non-null, else `rememberLazyListState()`):

```kotlin
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState

@Composable
private fun ProfileScreenContentHost(
    state: ProfileScreenViewState,
    listStateSeed: LazyListState? = null,
) {
    NubecitaTheme(dynamicColor = false) {
        val listState = listStateSeed ?: rememberLazyListState()
        ProfileScreenContent(
            state = state,
            listState = listState,
            snackbarHostState = remember { SnackbarHostState() },
            postCallbacks = previewPostCallbacks,
            onEvent = {},
            onBack = null,
        )
    }
}
```

(Adjust to match the actual existing helper signature — preserve all existing parameters; only add the new `listStateSeed`.)

- [ ] **Step 2: Add two new "scrolled-away" preview composables**

At the bottom of the file (alongside the existing `screen-other-user-follow*` previews):

```kotlin
@PreviewTest
@Preview(name = "screen-scrolled-away-light", showBackground = true, heightDp = 1100)
@Preview(
    name = "screen-scrolled-away-dark",
    showBackground = true,
    heightDp = 1100,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ProfileScreenScrolledAwayScreenshot() {
    // Pre-seed the LazyListState so the LazyColumn renders past the hero.
    // Index 1 means the hero (item index 0) is fully scrolled away → bar α = 1.
    ProfileScreenContentHost(
        state = sampleLoadedState(),
        listStateSeed = LazyListState(firstVisibleItemIndex = 1, firstVisibleItemScrollOffset = 0),
    )
}
```

- [ ] **Step 3: Record the new baselines**

```bash
./gradlew :feature:profile:impl:updateDebugScreenshotTest
```

Expected: **BUILD SUCCESSFUL.** 2 new PNGs appear (the scrolled-away light + dark variants).

- [ ] **Step 4: Visually inspect**

Read both new PNGs. Verify: the bar is fully painted (gradient color + name + handle), the pills sit flush below the bar with the same gradient color (continuous surface), the tab body content is visible below.

- [ ] **Step 5: Validate**

```bash
./gradlew :feature:profile:impl:validateDebugScreenshotTest
```

Expected: **FAIL** — the existing baselines no longer match because the bar has shifted everything down by `statusBarInset + 64dp`. This is expected; Task 8 regenerates them in one pass.

- [ ] **Step 6: Commit**

```bash
git add feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenContentScreenshotTest.kt \
        feature/profile/impl/src/screenshotTestDebug/reference/net/kikin/nubecita/feature/profile/impl/ProfileScreenContentScreenshotTestKt/ProfileScreenScrolledAwayScreenshot_*
git commit -m "$(cat <<'EOF'
test(feature/profile): screenshot fixtures for hero-scrolled-away state

ProfileScreenContentHost now accepts a listStateSeed so fixtures can
pre-seed firstVisibleItemIndex=1 to render with the hero fully gone
and the bar fully faded in. Locks in the bar + pills continuous-surface
visual once the user has scrolled past the hero.

Refs: nubecita-1tc
EOF
)"
```

---

## Task 8: Regenerate existing screenshot baselines

**Files:**
- Regenerate (in-place): `feature/profile/impl/src/screenshotTestDebug/reference/.../*.png`

- [ ] **Step 1: Update all baselines in one pass**

```bash
./gradlew :feature:profile:impl:updateDebugScreenshotTest
```

Expected: **BUILD SUCCESSFUL.** Many PNGs under `screenshotTestDebug/reference/` are modified — every baseline that includes the screen frame (bar offsets the hero down by `statusBarInset + 64dp`).

- [ ] **Step 2: Validate**

```bash
./gradlew :feature:profile:impl:validateDebugScreenshotTest
```

Expected: **BUILD SUCCESSFUL.** All baselines (existing + new from Tasks 3 + 7) match.

- [ ] **Step 3: Visual spot-check before committing**

Use `git diff --stat` to count modified PNGs. Then read 2-3 of the largest delta files (e.g., `ProfileScreenHeroWithBannerScreenshot_*`) to confirm: the hero has shifted down to make room for the bar, the bar itself is rendered at α=0 (transparent inset reservation since the hero is fully visible at the seed scroll position), pills and tab body have moved with the hero.

If anything looks visually wrong (e.g., bar is opaque when it shouldn't be, content overlaps), DO NOT commit — debug before proceeding.

```bash
git diff --stat 'feature/profile/impl/src/screenshotTestDebug/**'
```

- [ ] **Step 4: Commit**

```bash
git add 'feature/profile/impl/src/screenshotTestDebug/reference/'
git commit -m "$(cat <<'EOF'
test(feature/profile): regen screenshot baselines for new TopAppBar offset

Adding ProfileTopBar shifts every existing ProfileScreenContent
baseline down by statusBarInset + 64dp. Single regen pass; the bar
itself renders at α=0 (transparent inset reservation) for the
fully-expanded fixtures since the hero is in view. The new "scrolled
away" fixtures from the prior commit cover the α=1 visual.

Refs: nubecita-1tc
EOF
)"
```

---

## Task 9: Final integration check

**Files:** none (verification only).

- [ ] **Step 1: Repo-wide unit tests**

```bash
./gradlew testDebugUnitTest
```

Expected: **BUILD SUCCESSFUL.**

- [ ] **Step 2: Repo-wide spotless + lint**

```bash
./gradlew spotlessCheck lint
```

Expected: **BUILD SUCCESSFUL.** (Pre-existing baseline-filtered warnings are fine.)

- [ ] **Step 3: App debug build still assembles**

```bash
./gradlew :app:assembleDebug
```

Expected: **BUILD SUCCESSFUL.**

- [ ] **Step 4: Validate all screenshots one more time**

```bash
./gradlew :feature:profile:impl:validateDebugScreenshotTest
```

Expected: **BUILD SUCCESSFUL.**

- [ ] **Step 5: On-device smoke (manual; flag in PR description as a checkbox)**

The bd's reproducer is on Pixel 10 Pro XL: `Profile(handle = null)` → scroll until hero out of view → confirm pills no longer draw under the system clock / camera cutout. Add a checkbox to the PR's "Test plan" section noting this manual verification step. CI cannot catch this; the fix is verified visually in the regenerated baselines (the bar reserves the inset) but the real-device behavior should be eyeballed before merge.

- [ ] **Step 6: Push the branch and update the draft PR**

The branch already exists on origin (created when the spec was committed). Push the new commits:

```bash
git push
```

The draft PR (opened earlier) auto-updates with the new commits. No new PR creation needed.

---

## Self-review pass

After all tasks complete:

**Spec coverage:**
- ✅ Decision 1 (profile-only inline): all changes in `:feature:profile:impl`.
- ✅ Decision 2 (threshold fade): `computeBarAlpha` + `derivedStateOf` in Task 1 + Task 2.
- ✅ Decision 3 (gradient sample backdrop): `rememberBoldHeroGradient(...).top` in Task 2 (bar) + Task 5 (pills).
- ✅ Decision 4 (pills continuous backdrop): Task 5.
- ✅ Decision 5 (back arrow conditional on `state.handle != null`): Task 6.
- ✅ Decision 6 (Compact-only): no NavigationSuiteScaffold / two-pane logic in any task.
- ✅ Status-bar inset reservation: comes free with `Scaffold + TopAppBar`; explicitly noted in Task 4 + the `consumeWindowInsets` step.
- ✅ Error handling (`header == null` → transparent): `ProfileTopBar`'s null-check in Task 2 + Task 3.
- ✅ Screenshot tests: 8 isolated bar baselines (Task 3) + 2 scrolled-screen baselines (Task 7) + regen of existing (Task 8).
- ✅ Unit tests: 8 `computeBarAlpha` cases (Task 1).

No spec gaps.

**Type / API consistency:**
- `ProfileTopBar(header, listState, onBack, modifier)` defined in Task 2 — Task 4 calls it with the same signature.
- `ProfileTopBar(header, alpha, onBack, modifier)` overload introduced in Task 3 — used by screenshot tests in Task 3.
- `ProfileScreenContent(..., onBack: (() -> Unit)?, ...)` parameter added in Task 4 — `ProfileScreen` passes `null` placeholder in Task 4 then real callback in Task 6.
- `MainShellNavState.removeLast()` — confirmed real method (not `pop()`); Task 6 uses it.
- `LocalMainShellNavState` — confirmed exists in `:core:common`; Task 6 imports it.

No type drift.
