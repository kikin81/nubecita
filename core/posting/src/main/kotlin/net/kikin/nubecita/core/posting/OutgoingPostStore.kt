package net.kikin.nubecita.core.posting

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

/**
 * App-scoped home for posts the user is sending, so a post can appear in the
 * feed, the profile and its thread the moment the composer closes, instead of
 * after the next refresh.
 *
 * The composer calls [submit] and closes right away; the upload runs in the
 * application scope, so it outlives the composer's ViewModel. Screens read
 * [posts] and show each entry on top of what they loaded from the server,
 * deduped by post URI. In memory only: an entry doesn't survive process death.
 */
interface OutgoingPostStore {
    /** Newest first. */
    val posts: StateFlow<ImmutableList<OutgoingPost>>

    /** Starts sending [draft] and returns the new entry's [OutgoingPost.localId]. */
    fun submit(draft: PostDraft): String

    /** Re-sends a [OutgoingPostStatus.Failed] entry. No-op for any other status. */
    fun retry(localId: String)

    /**
     * Drops a failed entry the user gave up on and deletes its owned media
     * copies. Sent entries stay (several screens may still be showing them; each
     * dedupes against its own data), capped at the newest [MAX_SENT].
     */
    fun discard(localId: String)

    companion object {
        const val MAX_SENT = 20
    }
}
