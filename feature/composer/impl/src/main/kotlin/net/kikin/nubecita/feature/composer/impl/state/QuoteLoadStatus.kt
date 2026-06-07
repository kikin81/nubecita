package net.kikin.nubecita.feature.composer.impl.state

import net.kikin.nubecita.core.posting.ComposerError

/**
 * Mutually-exclusive lifecycle for the quote-mode quoted-post fetch.
 *
 * In non-quote mode, [ComposerState.quotePostLoad] is `null` — this type is only
 * meaningful when the composer has a `quotePostUri` (from the route's
 * `quotePostUri`, or set when the user pastes a post link). When quoting begins
 * the field starts as [Loading] and transitions to [Loaded] on a successful
 * `app.bsky.feed.getPosts` response or [Failed] on any error.
 *
 * Submission is gated on [Loaded]: the quote embed's `record` ref needs both URI
 * and CID, and we only have the CID after the fetch completes.
 *
 * Mirrors [ParentLoadStatus] (the reply-mode counterpart).
 */
sealed interface QuoteLoadStatus {
    /** The quote fetch is in flight. */
    data object Loading : QuoteLoadStatus

    /** Quoted post resolved; safe to embed it and submit. */
    data class Loaded(
        val post: QuotePostUi,
    ) : QuoteLoadStatus

    /**
     * Fetch failed (network, post deleted, blocked, etc). UI shows an inline
     * retry tile; submit is hard-disabled until the user dispatches
     * `RetryQuoteLoad` and we transition back to `Loading` → `Loaded`, or
     * dismisses the quote entirely.
     */
    data class Failed(
        val cause: ComposerError,
    ) : QuoteLoadStatus
}
