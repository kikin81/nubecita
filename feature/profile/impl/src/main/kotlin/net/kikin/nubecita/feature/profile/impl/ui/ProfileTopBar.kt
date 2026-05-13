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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import net.kikin.nubecita.designsystem.hero.rememberBoldHeroGradient
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
            Color.Transparent
        }

    // Derive the bar colors directly — TopAppBarDefaults.topAppBarColors is
    // @Composable and handles its own internal memoization, so we must call it
    // in the composable body rather than inside a remember { } lambda.
    val barColors =
        TopAppBarDefaults.topAppBarColors(
            containerColor = gradientTop.copy(alpha = alpha),
        )

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
