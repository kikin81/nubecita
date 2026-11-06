package net.kikin.nubecita.core.postinteractions

/**
 * Told when the PDS has accepted the deletion of one of the viewer's posts, so a
 * store holding its own copy can forget it.
 *
 * [PostInteractionsCache.markDeleted] only hides a post from surfaces that filter
 * on it, and only until the process dies. A store outside that filter has to drop
 * the post itself, or the post comes back: on the next launch for the feed cache,
 * and at once for the outgoing-post store.
 *
 * A multibinding rather than a direct call because the dependency cannot run
 * the other way: `:core:feed-cache`'s bench variant reaches this module through
 * `:core:posts`, so this module depending on the cache would be a cycle.
 */
interface PostDeletionListener {
    /**
     * [postUri] no longer exists on the network.
     *
     * Runs before the post is marked deleted, and before the surface hears
     * about it, while the surface's scope is still alive. A failure here is
     * logged and does not undo the deletion — the record is already gone.
     */
    suspend fun onPostDeleted(postUri: String)
}
