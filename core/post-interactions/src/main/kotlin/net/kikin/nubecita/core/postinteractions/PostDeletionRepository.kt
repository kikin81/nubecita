package net.kikin.nubecita.core.postinteractions

import io.github.kikin81.atproto.runtime.AtUri

/**
 * Deletes the signed-in user's own posts.
 *
 * Separate from [LikeRepostRepository] because the two differ in kind: an
 * unlike removes the viewer's *reaction* to somebody else's post, whereas this
 * removes the post record itself from the author's repo.
 *
 * **Irreversible.** `com.atproto.repo.deleteRecord` has no undo, which is why
 * the UI confirms before calling this rather than deleting optimistically with
 * an undo window: a deferred delete that never commits — the app killed inside
 * the window — would leave the user believing a post is gone while it is still
 * live.
 *
 * Callers are expected to have checked `PostUi.viewer.isOwnPost`. The PDS
 * enforces ownership regardless; the flag governs whether the affordance is
 * offered at all.
 */
interface PostDeletionRepository {
    /**
     * Delete the post record at [postUri].
     *
     * @return success once the PDS has accepted the deletion, or a failure
     *   carrying the underlying cause (no session, network, or a PDS refusal).
     */
    suspend fun deletePost(postUri: AtUri): Result<Unit>
}
