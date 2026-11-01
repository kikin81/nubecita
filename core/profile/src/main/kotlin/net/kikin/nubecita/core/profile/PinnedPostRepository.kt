package net.kikin.nubecita.core.profile

/**
 * Pin / unpin one of the signed-in viewer's posts on their profile
 * (`app.bsky.actor.profile#pinnedPost`, a `com.atproto.repo.strongRef`).
 */
interface PinnedPostRepository {
    /** Point the profile's `pinnedPost` at the post [uri] / [cid]. */
    suspend fun pin(
        uri: String,
        cid: String,
    ): Result<Unit>

    /**
     * Clear `pinnedPost` **only if** it still points at [uri]. When it points
     * elsewhere (pinned from another device, or a stale view) nothing is
     * written and the call succeeds — a stale "Unpin" can never clear a
     * different post's pin.
     */
    suspend fun unpin(uri: String): Result<Unit>
}
