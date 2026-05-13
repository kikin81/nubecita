package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.icon.mirror
import net.kikin.nubecita.feature.profile.impl.ProfileHeaderUi
import net.kikin.nubecita.feature.profile.impl.R

/**
 * Multiplier applied to the hero's measured height to derive the
 * fade window. With `0.5f` the bar is fully visible by the time the
 * hero is half-scrolled — empirically that's "around when the avatar
 * leaves the viewport."
 *
 * Tunable during on-device verification; internal to keep the surface
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
 * Visibility: the backdrop, title, and navigation icon are
 * alpha-modulated by [computeBarAlpha] applied to [listState]. While
 * the hero is in view, alpha is 0 → the bar reads as a transparent
 * inset reservation. As the hero scrolls past its half-height
 * threshold, alpha climbs to 1 → the bar fades up to a fully opaque
 * `MaterialTheme.colorScheme.surface` backdrop with the
 * "Display name / @handle" title.
 *
 * Backdrop = standard Material surface color, NOT a sample of the
 * hero gradient. An earlier iteration sampled
 * `rememberBoldHeroGradient(...).top` so bar + hero shared a hue, but
 * on-device verification surfaced unfixable contrast issues between
 * title text and saturated gradient tones. The surface backdrop gives
 * up the visual continuity for a baseline-AAA contrast contract with
 * `onSurface` content.
 *
 * When [onBack] is non-null the bar paints a back-arrow navigation
 * icon (also alpha-modulated). Null = own-profile root, no nav icon.
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
    // Surface backdrop, alpha-modulated. When the hero is in view (alpha=0)
    // the bar is fully transparent — just the status-bar inset reservation.
    // As the user scrolls past the hero, the bar fades up to the standard M3
    // surface color, which carries the WCAG-guaranteed contrast contract with
    // onSurface title + icon content. `topAppBarColors` is @Composable so it
    // can't be wrapped in remember(...) (the calculation lambda is non-
    // composable); the per-call allocation is bounded by the derivedStateOf
    // wrapper on `alpha` — once per discrete alpha step, not per render frame.
    val barColors =
        TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = alpha),
        )

    TopAppBar(
        title = {
            if (header != null) {
                Column(modifier = Modifier.alpha(alpha)) {
                    Text(
                        text = header.displayName ?: header.handle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "@${header.handle}",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                // Back arrow is NOT alpha-modulated — it stays fully visible
                // regardless of scroll position so a user who landed on a
                // pushed profile always has a clear way back. The bar's
                // backdrop + title still fade with scroll; only this nav
                // affordance is unconditionally opaque. Contrast over a
                // saturated banner is acceptable in current testing; if it
                // turns out to be a real legibility issue, wrap the icon
                // in a small surface-tinted scrim circle.
                IconButton(onClick = onBack) {
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
