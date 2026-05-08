package net.kikin.nubecita.core.posts

/**
 * Sentinel exception surfaced by [PostRepository] implementations when
 * `app.bsky.feed.getPosts` returns an empty `posts` array for the
 * requested URI. The most common causes are: the post was deleted,
 * the requesting account no longer has visibility (block / private),
 * or the URI is malformed.
 *
 * Exposed as a public type (rather than the previous `internal` shape)
 * so consumers can `catch` / pattern-match on it without falling back
 * to stringly-typed `simpleName` checks — the latter breaks under R8
 * minification when class names are obfuscated.
 */
class PostNotFoundException(
    uri: String,
) : RuntimeException("getPosts returned empty list for uri rkey=${uri.substringAfterLast('/')}")

/**
 * Sentinel exception surfaced by [PostRepository] implementations when
 * the wire-level `PostView` cannot be projected to a `PostUi` (e.g.,
 * the embedded `record: JsonObject` fails to decode as
 * `app.bsky.feed.post`). Distinct from [PostNotFoundException] so
 * callers can choose to surface a different user-visible error.
 */
class PostProjectionException(
    uri: String,
) : RuntimeException("toPostUiCore returned null for uri rkey=${uri.substringAfterLast('/')}")
