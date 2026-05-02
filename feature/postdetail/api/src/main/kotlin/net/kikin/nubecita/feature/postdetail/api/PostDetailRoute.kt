package net.kikin.nubecita.feature.postdetail.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the post-detail screen.
 *
 * Carries the AT URI of the focused post as a plain `String` (e.g.
 * `at://did:plc:.../app.bsky.feed.post/3kxyz`). Stored as a String — not
 * the lexicon-typed `AtUri` value class — so the NavKey serialization
 * format stays a single primitive field; consumers wrap to `AtUri` at
 * the call site to the atproto runtime, mirroring how
 * `:feature:feed:impl`'s like/repost path passes around `PostUi.id` as a
 * String and constructs `AtUri(...)` only at the XRPC boundary.
 *
 * Lives in `:feature:postdetail:api` so cross-feature modules that need
 * to push `PostDetailRoute` onto the back stack can depend on
 * `:feature:postdetail:api` alone — never on `:feature:postdetail:impl`.
 * The Feed tab's `FeedScreen` collects a `NavigateToPost` MVI effect and
 * pushes a `PostDetailRoute(postUri = post.id)` onto the active tab's
 * stack via `LocalMainShellNavState.current.add(...)`.
 */
@Serializable
data class PostDetailRoute(
    val postUri: String,
) : NavKey
