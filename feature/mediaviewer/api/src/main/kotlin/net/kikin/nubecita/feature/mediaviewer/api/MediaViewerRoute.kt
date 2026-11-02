package net.kikin.nubecita.feature.mediaviewer.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the fullscreen image viewer.
 *
 * Lives in `:feature:mediaviewer:api` so cross-feature modules that
 * need to push the viewer onto the back stack (today only
 * `:feature:postdetail:impl`'s focus-image tap) can depend on
 * `:feature:mediaviewer:api` alone — never on `:feature:mediaviewer:impl`.
 *
 * All fields are primitives:
 * - `postUri` is a plain `String` matching the project's NavKey shape
 *   (`PostDetailRoute.postUri`, the `NavigateToPost` / `NavigateToAuthor`
 *   effect surface). `AtUri(...)` construction is deferred to the XRPC
 *   boundary inside `:feature:mediaviewer:impl`.
 * - `imageIndex` is the zero-based position into the post's
 *   `app.bsky.embed.images` payload. The viewer's `HorizontalPager`
 *   uses it as the initial page index.
 * - `contextKey` is the `SharedImageSurfaces` value of the surface that
 *   pushed the viewer. It is half of the shared-element key, so it MUST
 *   match the `LocalSharedImageSurface` the pushing surface provides to its
 *   post cards — the transition is keyed on the pair, and a mismatch fails
 *   silently (no match, no animation, no error). It defaults to the empty
 *   string, which means "no shared transition", so a push site that does not
 *   opt in gets a plain fade rather than a broken half-transition.
 *
 * The `@Serializable` form is three primitive fields, so process death
 * round-trips it cleanly via `kotlinx.serialization`'s Nav 3 surface
 * — and a future deep-link wiring (`bsky.app/profile/<handle>/post/<rkey>?image=N`)
 * can construct it directly without schema work.
 */
@Serializable
data class MediaViewerRoute(
    val postUri: String,
    val imageIndex: Int,
    val contextKey: String = "",
) : NavKey
