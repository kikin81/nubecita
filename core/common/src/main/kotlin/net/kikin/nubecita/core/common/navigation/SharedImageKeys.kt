package net.kikin.nubecita.core.common.navigation

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * The post-rendering surfaces that opt into feed → media-viewer shared image
 * transitions.
 *
 * The surface is part of both the shared-element key and the Coil memory-cache
 * key, for two reasons:
 *
 * 1. **Shared-element keys must be unique among simultaneously-composed cards.**
 *    The same post renders in more than one place at once — the Feed list and
 *    the PostDetail pane are both composed in `ListDetailSceneStrategy`'s
 *    two-pane scene, and the previous entry stays composed underneath an
 *    overlay scene. Keying on `postUri` alone registers the same key twice in
 *    one `SharedTransitionLayout`, which Compose does not reject — it just
 *    resolves the match non-deterministically.
 * 2. **An explicit Coil `memoryCacheKey` replaces Coil's default key, which
 *    includes the requested size.** The same image is requested at carousel
 *    slide width in the Feed and at full width in PostDetail; without the
 *    surface those two requests collide on one cache entry.
 *
 * [NONE] is the default and means "this surface does not participate" — no
 * shared element is registered and no explicit memory-cache key is set, so
 * cards on surfaces that cannot open the viewer (bookmarks, notification
 * previews, Wear) keep Coil's size-aware default key.
 */
object SharedImageSurfaces {
    const val NONE: String = ""
    const val FEED: String = "feed"
    const val FEED_VIEW: String = "feedview"
    const val POST_DETAIL: String = "postdetail"
    const val PROFILE: String = "profile"

    /** Search's Posts results. */
    const val SEARCH: String = "search"

    /**
     * A hashtag's results. Separate from [SEARCH] because the hashtag screen
     * opens in the detail pane beside search results, so the same post can be
     * on screen in both at once.
     */
    const val HASHTAG: String = "hashtag"
}

/**
 * The [SharedImageSurfaces] value for the surface currently rendering post
 * cards.
 *
 * Provided by the `EntryProviderInstaller` of each participating feature, and
 * read by `PostCardImageEmbed` — the card sits several layers below the screen
 * (screen → lazy list → item → `PostCard` → `EmbedSlot` → embed), so an
 * ambient value is cheaper than threading a parameter through every one of the
 * ten surfaces that render a `PostCard`.
 *
 * The media viewer does NOT read this local — it is its own `NavEntry` on the
 * outer `NavDisplay`, so it takes the surface from `MediaViewerRoute.contextKey`
 * instead, which the push site set to the surface it pushed from.
 */
@Suppress("ktlint:compose:compositionlocal-allowlist")
val LocalSharedImageSurface: ProvidableCompositionLocal<String> =
    compositionLocalOf { SharedImageSurfaces.NONE }

/**
 * Key for the `sharedElement` registration bridging a post card's image at
 * [index] and the same image in the full-screen viewer.
 *
 * Both halves MUST derive the key from this function — a shared element only
 * animates when the two registrations agree on the key byte for byte, and a
 * mismatch fails silently (no match, no animation, no error).
 */
fun sharedImageKey(
    surface: String,
    postUri: String,
    index: Int,
): String = "shared_image_${surface}_${postUri}_$index"

/**
 * Coil memory-cache key for a post's image at [index].
 *
 * The card writes its thumbnail under this key; the viewer passes the same key
 * as `placeholderMemoryCacheKey` so the already-decoded thumbnail paints on the
 * first frame instead of a blank black one.
 */
fun postImageMemoryCacheKey(
    surface: String,
    postUri: String,
    index: Int,
): String = "coil_${surface}_${postUri}_$index"

/**
 * The `sharedElement` modifier bridging a post image at [index] and the same
 * image in the full-screen viewer, or [Modifier] when this surface does not
 * participate (see [SharedImageSurfaces]).
 *
 * `sharedElement`, not `sharedBounds`: the card and the viewer show the *same*
 * image, which is the case `sharedElement` is for. `sharedBounds` keeps both
 * halves composed and crossfades them, which is for container-to-page
 * transforms where the two sides show different content.
 *
 * Shared by the card ([surface] defaulting to the ambient
 * [LocalSharedImageSurface]) and by the viewer, which passes
 * `MediaViewerRoute.contextKey` explicitly because it is its own `NavEntry`
 * and has no ambient surface. One implementation, so the two halves cannot
 * drift apart on key format.
 *
 * [enabled] gates only the returned modifier, never the
 * `rememberSharedContentState` call — the viewer flips it per page as the user
 * swipes, and re-running the remember would unregister and re-register the
 * shared element on every swipe.
 *
 * [cornerRadius] is this side's resting corner radius and [peerCornerRadius]
 * is the other side's. Only ONE of the two halves draws during the transition
 * — `sharedElement` renders the target and skips the source — so whichever
 * side is arriving would otherwise paint its own fixed radius across the whole
 * animation while the bounds travel between a card-sized and a full-screen
 * rect. Interpolating between the two radii is what keeps the corners from
 * popping at the hand-off.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun sharedImageModifier(
    postUri: String?,
    index: Int,
    enabled: Boolean = true,
    surface: String = LocalSharedImageSurface.current,
    cornerRadius: Dp = SharedImageCorners.CARD,
    peerCornerRadius: Dp = SharedImageCorners.VIEWER,
): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    if (sharedTransitionScope == null ||
        animatedVisibilityScope == null ||
        postUri.isNullOrBlank() ||
        surface == SharedImageSurfaces.NONE
    ) {
        return Modifier
    }
    return with(sharedTransitionScope) {
        val sharedContentState = rememberSharedContentState(key = sharedImageKey(surface, postUri, index))
        // Held as State, not unwrapped by `by`: the overlay clip reads it at
        // draw time on every frame of the transition.
        val animatedCorner =
            animatedVisibilityScope.transition.animateDp(label = "sharedImageCorner") { state ->
                when (state) {
                    EnterExitState.Visible -> cornerRadius
                    EnterExitState.PreEnter, EnterExitState.PostExit -> peerCornerRadius
                }
            }
        val overlayClip = remember(animatedCorner) { AnimatedCornerOverlayClip(animatedCorner) }
        if (enabled) {
            Modifier.sharedElement(
                sharedContentState = sharedContentState,
                animatedVisibilityScope = animatedVisibilityScope,
                clipInOverlayDuringTransition = overlayClip,
            )
        } else {
            Modifier
        }
    }
}

/** Resting corner radii of the two halves of the shared image transition. */
object SharedImageCorners {
    /** Post-card images, matching the embed's `RoundedCornerShape(16.dp)`. */
    val CARD: Dp = 16.dp

    /** The full-screen viewer, which runs edge to edge. */
    val VIEWER: Dp = 0.dp
}

/**
 * The overlay clip's corner radius in pixels, clamped at zero.
 *
 * Split out of [AnimatedCornerOverlayClip] so the clamp is unit-testable:
 * `Path` is backed by `android.graphics.Path`, which is not available to a
 * plain JVM test, whereas this is pure arithmetic over [Density].
 *
 * The clamp exists because a bouncy animation spec would undershoot past
 * `0.dp` and a negative `CornerRadius` is not a defined input. The current
 * default spec (`animateDp` -> `spring(DampingRatioNoBouncy)`) is critically
 * damped and cannot overshoot, so this guards a future spec change rather
 * than a live defect.
 */
internal fun sharedImageCornerRadiusPx(
    cornerRadius: Dp,
    density: Density,
): Float = with(density) { cornerRadius.toPx() }.coerceAtLeast(0f)

/**
 * Clips the shared image to a rounded rect whose radius follows
 * [cornerRadius] while the element is in the shared-transition overlay.
 *
 * This is deliberately an [SharedTransitionScope.OverlayClip] rather than a
 * `Modifier.clip` on either half. The overlay clip applies *only* while the
 * transition is running, so the card keeps M3's own
 * `CarouselItemScope.maskClip` — which does the carousel's item masking, not
 * just corner rounding — completely untouched at rest and during carousel
 * scrolling. Nothing here reaches into the Material carousel.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
private class AnimatedCornerOverlayClip(
    private val cornerRadius: State<Dp>,
) : SharedTransitionScope.OverlayClip {
    // Reused across frames per the OverlayClip contract — it asks
    // implementations to mutate one Path rather than allocate per frame.
    private val path = Path()

    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path {
        val radiusPx = sharedImageCornerRadiusPx(cornerRadius.value, density)
        path.reset()
        // `bounds` is already in the SharedTransitionScope's coordinate space,
        // so the rect is used as-is with no extra offset.
        path.addRoundRect(RoundRect(rect = bounds, cornerRadius = CornerRadius(radiusPx, radiusPx)))
        return path
    }
}
