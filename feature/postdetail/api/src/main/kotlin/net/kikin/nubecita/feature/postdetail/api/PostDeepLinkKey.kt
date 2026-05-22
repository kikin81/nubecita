package net.kikin.nubecita.feature.postdetail.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Intermediate transport key emitted by the post deep-link matchers
 * (`https://bsky.app/profile/{handle}/post/{rkey}` and its
 * `https://nubecita.app` + `nubecita://` siblings — see
 * `ProfileDeepLinkModule`).
 *
 * Why an intermediate key instead of matching directly to
 * [PostDetailRoute]: alpha03's
 * [androidx.navigation3.runtime.deeplink.UriDeepLinkMatcher] populates
 * NavKey fields by exact name from the URI placeholders, so a URL
 * pattern `…/profile/{handle}/post/{rkey}` can only deserialise into a
 * NavKey whose serializable fields are `handle: String` + `rkey: String`.
 * [PostDetailRoute] carries a fully-formed `postUri: String`
 * (`at://<did|handle>/app.bsky.feed.post/<rkey>`) and is used directly
 * by `:feature:postdetail:impl`'s entry provider — reshaping it to
 * `(handle, rkey)` would force a touch of every internal caller (Feed →
 * PostDetail, Profile → PostDetail, reply-thread → PostDetail, etc.).
 *
 * Lifecycle: emitted only by the deep-link matcher and converted by
 * `MainActivity.handleIntent` into the canonical
 * `PostDetailRoute(postUri = "at://<handle>/app.bsky.feed.post/<rkey>")`
 * before publication to the `DeepLinkRouter`. The intermediate key
 * never reaches `MainShell` and never lands on the back stack —
 * see nubecita-kf6k.3 for the design decision.
 *
 * [handle] accepts either an AT Protocol handle (`alice.bsky.social`)
 * or a DID (`did:plc:abc...`) — same forms accepted by [PostDetailRoute]
 * and the `getPostThread` XRPC. Handle-form URIs round-trip opaquely
 * through `PostThreadRepository` to the appview, which resolves the
 * handle server-side; no client-side resolution hop is required.
 *
 * Lives in `:feature:postdetail:api` alongside [PostDetailRoute] so
 * the [toPostDetailRoute] mapper has a single import. The matcher
 * (in `:feature:profile:impl`) already depends on
 * `:feature:postdetail:api` for `PostDetailRoute` itself.
 */
@Serializable
data class PostDeepLinkKey(
    val handle: String,
    val rkey: String,
) : NavKey

/**
 * Translates the intermediate deep-link key into the canonical
 * back-stack-eligible [PostDetailRoute].
 *
 * The synthesised AT URI is a structurally-valid AT URI per the spec
 * (https://atproto.com/specs/at-uri-scheme) regardless of whether
 * [PostDeepLinkKey.handle] is in handle or DID form: both are
 * `at-identifier` shapes accepted in the authority segment.
 */
fun PostDeepLinkKey.toPostDetailRoute(): PostDetailRoute = PostDetailRoute(postUri = "at://$handle/app.bsky.feed.post/$rkey")
