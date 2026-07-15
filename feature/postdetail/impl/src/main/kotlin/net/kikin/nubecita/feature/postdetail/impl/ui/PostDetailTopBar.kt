package net.kikin.nubecita.feature.postdetail.impl.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.designsystem.component.avatarFallbackFor
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.icon.mirror
import net.kikin.nubecita.feature.postdetail.impl.R

/** How far the focus card must tuck under the bar before the author appears. */
internal val AUTHOR_BAR_ENTER_THRESHOLD = 56.dp

/** How far it must come back out before the author leaves. Hysteresis. */
internal val AUTHOR_BAR_EXIT_THRESHOLD = 40.dp

/** Fixed horizontal travel for the author block. NOT a fraction of its width. */
internal val AUTHOR_BAR_SLIDE_DISTANCE = 24.dp

/** Avatar diameter in the bar. */
private val AUTHOR_BAR_AVATAR_SIZE = 28.dp

/**
 * Whether the toolbar should show the focus author instead of the "Post" title.
 *
 * Pure so every branch is JVM-testable without a device or a Compose runtime.
 *
 * [focusItemTopPx] is the focus card's top edge **relative to the bottom of the
 * app bar** (negative once it has tucked underneath). In this screen the bar's
 * height reaches the list as its top `contentPadding`, so `LazyListItemInfo
 * .offset` is *already* in that frame and the caller passes it straight through
 * (verified on device: `viewportStartOffset == -beforeContentPadding`). A caller
 * whose bar is NOT the list's top padding must translate `offset` into this
 * frame first; this function never sees raw lazy-list coordinates.
 *
 * The `focusItemTopPx == null` case is the subtle one: a focus card absent from
 * `visibleItemsInfo` is EITHER scrolled off the top (show the author) OR still
 * below the fold (don't) — opposite answers that a null lookup alone cannot
 * distinguish, hence the [firstVisibleItemIndex] comparison.
 */
internal fun shouldShowAuthorInBar(
    focusIndex: Int,
    firstVisibleItemIndex: Int,
    focusItemTopPx: Int?,
    enterThresholdPx: Int,
    exitThresholdPx: Int,
    currentlyShown: Boolean,
): Boolean {
    if (focusIndex < 0) return false
    if (focusItemTopPx == null) return focusIndex < firstVisibleItemIndex
    val tucked = -focusItemTopPx
    return if (currentlyShown) tucked > exitThresholdPx else tucked >= enterThresholdPx
}

/**
 * Scroll-reactive post-detail toolbar. Derives the show/hide decision from
 * [listState] and delegates rendering to the stateless overload.
 *
 * [focusIndex] is the index of the `ThreadItem.Focus` row in the same list the
 * `LazyColumn` renders, or -1 when no focus has resolved.
 */
@Composable
internal fun PostDetailTopBar(
    author: AuthorUi?,
    listState: LazyListState,
    focusIndex: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val enterPx = with(density) { AUTHOR_BAR_ENTER_THRESHOLD.roundToPx() }
    val exitPx = with(density) { AUTHOR_BAR_EXIT_THRESHOLD.roundToPx() }

    // Hysteresis makes this a fold over its own previous output, not a pure
    // projection of scroll state — so it can't be a plain derivedStateOf (that
    // would be a backwards write during composition). snapshotFlow feeds the
    // fold instead: it ticks every scroll frame, but `shown` is a MutableState,
    // so it only invalidates the bar when the boolean actually FLIPS. The 120hz
    // guarantee survives.
    var shown by remember { mutableStateOf(false) }

    LaunchedEffect(listState, focusIndex, enterPx, exitPx) {
        snapshotFlow {
            val focus =
                listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == focusIndex }
            // LazyListItemInfo.offset is already expressed relative to the bottom
            // of the app bar (the bar's height arrives as the list's top
            // contentPadding, and viewportStartOffset == -beforeContentPadding
            // exactly, so their sum is 0). Verified on device; no normalization
            // needed. Pass the raw offset straight through as focusItemTopPx.
            listState.firstVisibleItemIndex to focus?.offset
        }.collect { (firstVisible, focusTop) ->
            shown =
                shouldShowAuthorInBar(
                    focusIndex = focusIndex,
                    firstVisibleItemIndex = firstVisible,
                    focusItemTopPx = focusTop,
                    enterThresholdPx = enterPx,
                    exitThresholdPx = exitPx,
                    currentlyShown = shown,
                )
        }
    }

    PostDetailTopBar(
        author = author,
        showAuthor = shown,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Stateless post-detail toolbar. Previews and screenshot tests drive this
 * directly, so [showAuthor] can be pinned at either end of the transition
 * without a scrolling list (and without capturing an in-flight spring).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PostDetailTopBar(
    author: AuthorUi?,
    showAuthor: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // slideInHorizontally takes RAW pixels and does not mirror for layout
    // direction — the sign has to come from the locale, or RTL slides in from
    // the wrong edge. Same class of bug Modifier.mirror() exists to prevent.
    val layoutDirection = LocalLayoutDirection.current
    val slidePx =
        with(LocalDensity.current) { AUTHOR_BAR_SLIDE_DISTANCE.roundToPx() }
            .let { if (layoutDirection == LayoutDirection.Rtl) it else -it }

    // Specs come from the theme's MotionScheme, which already collapses to short
    // linear tweens under reduce-motion. Hand-rolling a spring()/tween() here
    // would silently opt this surface out of that.
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    TopAppBar(
        modifier = modifier,
        title = {
            AnimatedContent(
                targetState = showAuthor && author != null,
                transitionSpec = {
                    val transform =
                        if (targetState) {
                            // Author in: it carries all the motion.
                            // "Post" out: fades in place, does not travel.
                            (slideInHorizontally(spatialSpec) { slidePx } + fadeIn(effectsSpec)) togetherWith
                                fadeOut(effectsSpec)
                        } else {
                            fadeIn(effectsSpec) togetherWith
                                (slideOutHorizontally(spatialSpec) { slidePx } + fadeOut(effectsSpec))
                        }
                    // No width animation between the two titles; don't clip the
                    // avatar mid-slide.
                    transform using SizeTransform(clip = false)
                },
                label = "postdetail-title",
            ) { showingAuthor ->
                if (showingAuthor && author != null) {
                    AuthorTitle(author)
                } else {
                    Text(stringResource(R.string.postdetail_title))
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                NubecitaIcon(
                    name = NubecitaIconName.ArrowBack,
                    contentDescription = stringResource(R.string.postdetail_back_content_description),
                    filled = true,
                    modifier = Modifier.mirror(),
                )
            }
        },
    )
}

/**
 * The author identity block. A label, not a tap target — tapping the author on
 * the focus card is already the route to their profile.
 *
 * The avatar is decorative (`contentDescription = null`): the display name sits
 * immediately beside it, and "Jane Appleseed, avatar. Jane Appleseed" is a worse
 * TalkBack read than silence.
 */
@Composable
private fun AuthorTitle(author: AuthorUi) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        NubecitaAvatar(
            model = author.avatarUrl,
            contentDescription = null,
            size = AUTHOR_BAR_AVATAR_SIZE,
            fallback = avatarFallbackFor(author),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = author.displayName.takeIf { it.isNotBlank() } ?: "@${author.handle}",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
