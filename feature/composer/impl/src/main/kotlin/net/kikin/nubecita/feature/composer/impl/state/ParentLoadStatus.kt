package net.kikin.nubecita.feature.composer.impl.state

import net.kikin.nubecita.core.posting.ComposerError

/**
 * Mutually-exclusive lifecycle for the reply-mode parent-post fetch.
 *
 * In new-post mode, [ComposerState.replyParentLoad] is `null` — this
 * type is only meaningful when the route's `replyToUri` is non-null.
 * Once the VM enters reply mode at construction, the field starts as
 * [Loading] and transitions to [Loaded] on a successful
 * `app.bsky.feed.getPostThread` response or [Failed] on any error.
 *
 * Submission is gated on `Loaded`: the spec requires that we never
 * submit a reply when we cannot prove the parent's CID — the reply
 * record's `reply.parent` ref needs both URI and CID, and we only
 * have the CID after the fetch completes successfully.
 */
sealed interface ParentLoadStatus {
    /** The parent fetch is in flight. */
    data object Loading : ParentLoadStatus

    /** Parent + root refs resolved; safe to submit a reply. */
    data class Loaded(
        val post: ParentPostUi,
    ) : ParentLoadStatus

    /**
     * Fetch failed (network, parent deleted, blocked, etc). UI
     * shows an inline retry tile; submit is hard-disabled until
     * the user dispatches `RetryParentLoad` and we transition back
     * to `Loading` → `Loaded`.
     */
    data class Failed(
        val cause: ComposerError,
    ) : ParentLoadStatus
}
