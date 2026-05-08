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
 * Both fields are primitives:
 * - `postUri` is a plain `String` matching the project's NavKey shape
 *   (`PostDetailRoute.postUri`, the `NavigateToPost` / `NavigateToAuthor`
 *   effect surface). `AtUri(...)` construction is deferred to the XRPC
 *   boundary inside `:feature:mediaviewer:impl`.
 * - `imageIndex` is the zero-based position into the post's
 *   `app.bsky.embed.images` payload. The viewer's `HorizontalPager`
 *   uses it as the initial page index.
 *
 * The `@Serializable` form is two primitive fields, so process death
 * round-trips it cleanly via `kotlinx.serialization`'s Nav 3 surface
 * — and a future deep-link wiring (`bsky.app/profile/<handle>/post/<rkey>?image=N`)
 * can construct it directly without schema work.
 */
@Serializable
data class MediaViewerRoute(
    val postUri: String,
    val imageIndex: Int,
) : NavKey
