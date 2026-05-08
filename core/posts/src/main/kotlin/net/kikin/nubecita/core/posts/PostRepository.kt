package net.kikin.nubecita.core.posts

import net.kikin.nubecita.data.models.PostUi

/**
 * Single-post read surface backed by `app.bsky.feed.getPosts` (the
 * lexicon's hydration endpoint for a list of post AT-URIs).
 *
 * Intentionally separate from `:core:posting` (which owns the *write*
 * surface — creating posts, attachments, reply refs) and from
 * `:feature:postdetail:impl/data/PostThreadRepository` (which owns
 * thread fetches via `getPostThread`, returning ancestors / replies /
 * folds in addition to the focus post). When a consumer needs only the
 * focus post — image-viewer hydration today, deep-link landings or
 * notification post resolution tomorrow — it goes through this surface
 * to avoid pulling in thread context it doesn't need.
 *
 * The default implementation is the only place in the project that
 * imports `app.bsky.feed.getPosts`; consumers MUST go through the
 * interface. Same single-import discipline `PostThreadRepository`
 * enforces for `getPostThread`.
 */
interface PostRepository {
    suspend fun getPost(uri: String): Result<PostUi>
}
