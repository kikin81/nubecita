package net.kikin.nubecita.data.models

import androidx.compose.runtime.Stable

/**
 * A post the signed-in user just submitted, rendered on top of a surface's
 * server-loaded list until that list includes it.
 *
 * Projected from `:core:posting`'s `OutgoingPost` so UI modules don't need the
 * posting types. [localId] is stable across retries and the Sending → Sent
 * transition, so it makes a stable list key.
 */
@Stable
public data class OutgoingPostUi(
    val localId: String,
    val post: PostUi,
    val status: OutgoingPostStatusUi,
    /** The parent post's AT URI when this is a reply; null for a top-level post. */
    val replyToUri: String? = null,
)

public enum class OutgoingPostStatusUi {
    Sending,

    /** On the server; renders as an ordinary post. */
    Sent,

    /** The send failed; the card offers Retry and Discard. */
    Failed,
}
