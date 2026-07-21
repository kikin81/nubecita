package net.kikin.nubecita.feature.videos.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.videos.impl.R
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
 * [pageKey] is the stable identity of the post on this page (its id). The
 * VerticalPager already keys pages on it, so a rebind normally recomposes this
 * page fresh — but keying the transient heart-burst state on it too keeps the
 * reset guarantee local to the page rather than depending on the pager's key,
 * matching how the caption/overflow state is keyed on the post id.
 *
 * Overlay chrome (author, caption, interactions, mute) lands in PR2 and composes
 * into this Box above the poster.
 */
@Composable
internal fun VideoFeedPage(
    posterUrl: String?,
    aspectRatio: Float,
    posterAlpha: () -> Float,
    modifier: Modifier = Modifier,
    pageKey: Any = Unit,
    isPaused: Boolean = false,
    onTogglePlayPause: () -> Unit = {},
    onDoubleTapLike: () -> Unit = {},
    chrome: @Composable () -> Unit = {},
) {
    // One painter instance for all three poster states; ColorPainter is cheap but
    // there is no reason to reallocate it on every composition.
    val blackPainter = remember { ColorPainter(Color.Black) }
    // Each double-tap spawns a heart at its touch point; rapid taps stack. Each
    // self-removes when its animation finishes, so the list drains to empty.
    // Keyed on pageKey so a page rebound to a different post starts with no
    // leftover hearts and a fresh id counter (see the [pageKey] doc).
    val hearts = remember(pageKey) { mutableStateListOf<HeartBurst>() }
    var nextHeartId by remember(pageKey) { mutableIntStateOf(0) }
    val heartCenterPx = with(LocalDensity.current) { (HEART_SIZE / 2).roundToPx() }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // graphicsLayer (not Modifier.alpha) so a crossfade only re-runs the
        // layer block — no recomposition or relayout per frame at 120hz. The
        // alpha itself is a deferred () -> Float read, not a composition-scope
        // value, so an in-flight animation invalidates only this layer block,
        // never VideoFeedPage's composition.
        val posterModifier =
            Modifier
                .aspectRatio(aspectRatio)
                .graphicsLayer { alpha = posterAlpha() }
                .testTag(VideoFeedTestTags.POSTER)
        if (posterUrl == null) {
            // Spec D4: a missing poster degrades to flat black, NOT to
            // NubecitaAsyncImage's fallback painter. That painter is a light
            // surfaceContainerHighest tile, which on this always-black video
            // canvas would flash a grey rectangle exactly where the poster
            // layer exists to prevent one.
            Box(posterModifier.background(Color.Black))
        } else {
            // Coil's AsyncImage directly, NOT NubecitaAsyncImage: that wrapper's
            // placeholder/error/fallback are all a light surfaceContainerHighest
            // tile, correct on normal app surfaces but wrong here — on this
            // always-black video canvas it would paint a full-bleed near-white
            // rectangle for the entire poster download, and again on load
            // failure, exactly where this layer exists to prevent one. Black
            // painters keep every poster state (loading, loaded, error) matching
            // the canvas it degrades onto.
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                modifier = posterModifier,
                contentScale = ContentScale.Fit,
                placeholder = blackPainter,
                error = blackPainter,
                fallback = blackPainter,
            )
        }
        // Gesture layer sits ABOVE the poster but BELOW the chrome, so the rail's
        // own clickables win over the page tap instead of being swallowed by it.
        // detectTapGestures does not consume drags, so the pager keeps its swipe.
        //
        // rememberUpdatedState is load-bearing: pointerInput(Unit) never restarts, so
        // it would otherwise capture the FIRST lambdas forever. The double-tap lambda
        // closes over the page's PostUi, so a stale capture keeps reporting the post
        // as unliked — and since onLike toggles, the second double tap silently
        // UNLIKED. Caught on device; a unit test calling handleEvent directly with a
        // fresh post cannot see it.
        val currentOnTogglePlayPause by rememberUpdatedState(onTogglePlayPause)
        val currentOnDoubleTapLike by rememberUpdatedState(onDoubleTapLike)
        // detectTapGestures is RAW pointer input and contributes no semantics, unlike
        // Modifier.clickable. Play/pause exists only as this gesture — there is no rail
        // cell for it — so without an explicit semantics action a TalkBack user could be
        // told the video is paused (the glyph carries a contentDescription) with no way
        // to resume it. The label tracks state so the announcement matches the outcome.
        val playPauseLabel =
            stringResource(if (isPaused) R.string.videos_action_play else R.string.videos_action_pause)
        Box(
            Modifier
                .matchParentSize()
                .semantics {
                    onClick(label = playPauseLabel) {
                        currentOnTogglePlayPause()
                        true
                    }
                }.pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            hearts.add(HeartBurst(id = nextHeartId, position = offset))
                            nextHeartId++
                            currentOnDoubleTapLike()
                        },
                        onTap = { currentOnTogglePlayPause() },
                    )
                },
        )
        if (isPaused) {
            NubecitaIcon(
                name = NubecitaIconName.PlayArrow,
                contentDescription = stringResource(R.string.videos_paused),
                tint = Color.White.copy(alpha = PAUSE_GLYPH_ALPHA),
                opticalSize = PAUSE_GLYPH_SIZE,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .testTag(VideoFeedTestTags.PAUSE_INDICATOR),
            )
        }
        // Chrome draws last so it sits above the poster — and therefore above the
        // video too, since the poster fades out to reveal the surface behind.
        chrome()
        // Heart bursts draw on top of everything at the touch point. This overlay
        // has no pointer input, so it never intercepts a tap.
        Box(Modifier.matchParentSize()) {
            hearts.forEach { heart ->
                key(heart.id) {
                    LikeBurstHeart(
                        heart = heart,
                        onFinish = { hearts.remove(heart) },
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
    }
}

private val PAUSE_GLYPH_SIZE = 72.dp
private const val PAUSE_GLYPH_ALPHA = 0.85f
