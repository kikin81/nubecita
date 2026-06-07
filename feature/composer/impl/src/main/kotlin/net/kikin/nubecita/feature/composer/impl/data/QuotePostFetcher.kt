package net.kikin.nubecita.feature.composer.impl.data

import io.github.kikin81.atproto.runtime.AtUri
import net.kikin.nubecita.feature.composer.impl.state.QuotePostUi

/**
 * Resolves a quote target's display + embed data for the composer's quote slot.
 *
 * Returns the resolved [QuotePostUi] inside `Result.success`, or a
 * `Result.failure` carrying a [net.kikin.nubecita.core.posting.ComposerError]-typed
 * throwable on failure (network, NotFound/deleted, blocked, etc.). Consumers map
 * `result.exceptionOrNull() as? ComposerError` exhaustively.
 *
 * Distinct from [ParentFetchSource]: quoting needs only the post's own strong ref
 * (no thread-root walk), so this uses the lighter `app.bsky.feed.getPosts` rather
 * than `getPostThread`.
 */
interface QuotePostFetcher {
    /**
     * @param uri the AT URI of the post being quoted (lifted from
     *   `ComposerRoute.quotePostUri` / the pasted link).
     */
    suspend fun fetchQuote(uri: AtUri): Result<QuotePostUi>
}
