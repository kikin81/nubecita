package net.kikin.nubecita.feature.postdetail.impl.data

import kotlinx.collections.immutable.ImmutableList

/**
 * `app.bsky.feed.getPostThread` fetch surface scoped to
 * `:feature:postdetail:impl`.
 *
 * Like `:feature:feed:impl`'s [net.kikin.nubecita.feature.feed.impl.data.FeedRepository],
 * the interface is package-internal — no other module imports it. If a
 * second consumer (notifications detail, deep-link landing) later needs
 * the same fetch, the change that adds the consumer also promotes this
 * interface to a new shared module (e.g. `:core:thread`).
 */
internal interface PostThreadRepository {
    suspend fun getPostThread(uri: String): Result<ImmutableList<ThreadItem>>
}
