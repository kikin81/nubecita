package net.kikin.nubecita.core.common.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

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
 * cards on surfaces that cannot open the viewer (search results, bookmarks,
 * notification previews, Wear) keep Coil's size-aware default key.
 */
object SharedImageSurfaces {
    const val NONE: String = ""
    const val FEED: String = "feed"
    const val FEED_VIEW: String = "feedview"
    const val POST_DETAIL: String = "postdetail"
    const val PROFILE: String = "profile"
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
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun sharedImageModifier(
    postUri: String?,
    index: Int,
    enabled: Boolean = true,
    surface: String = LocalSharedImageSurface.current,
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
        if (enabled) {
            Modifier.sharedElement(
                sharedContentState = sharedContentState,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        } else {
            Modifier
        }
    }
}
