package net.kikin.nubecita.core.posting

import io.github.kikin81.atproto.com.atproto.repo.StrongRef

/**
 * The two AT Protocol references required to construct an
 * `app.bsky.feed.post` record's `reply` field.
 *
 * - [parent] — the post being directly replied to.
 * - [root] — the originating post of the thread. When replying to a
 *   thread root, [root] equals [parent]. When replying to a reply,
 *   [root] is inherited from the parent's own `reply.root` ref.
 *
 * The composer's reply-mode flow resolves both refs via
 * `app.bsky.feed.getPostThread` before submitting (see the
 * `:feature:composer:impl` reply-parent fetch logic) and passes them
 * to [PostingRepository.createPost] as a single value object so the
 * repository can't accidentally drop one of them.
 */
data class ReplyRefs(
    val parent: StrongRef,
    val root: StrongRef,
)
