package net.kikin.nubecita.feature.composer.impl.state

import io.github.kikin81.atproto.com.atproto.repo.StrongRef

/**
 * Resolved quoted post for the composer's quote slot — rendering + embed-ref
 * construction. Carried inside [QuoteLoadStatus.Loaded] once the quote fetch
 * (`app.bsky.feed.getPosts`) completes.
 *
 * Mirrors [ParentPostUi] (the reply-mode counterpart), but a quote needs only a
 * single [StrongRef]: the quoted post's own `uri` + `cid`, used to build the
 * `app.bsky.embed.record` embed at submit time.
 *
 * Display fields ([authorHandle], [authorDisplayName], [text]) feed the minimal
 * quote context card shipped here; nubecita-8g28.7 upgrades that card to a fuller
 * post presentation.
 *
 * @property ref the quoted post's strong ref (`uri` + `cid`), passed to
 *   `PostingRepository.createPost(quote = …)`.
 * @property canViewerQuote whether the viewer is allowed to quote this post — the
 *   inverse of the post viewer's `embeddingDisabled` (the appview's server-computed
 *   postgate result). Defaults to `true` (fail open). The composer checks this at
 *   launch and short-circuits with a snackbar if the gate changed after the user
 *   chose to quote (the repost menu is the primary gate; this is defensive).
 */
data class QuotePostUi(
    val ref: StrongRef,
    val authorHandle: String,
    val authorDisplayName: String?,
    val text: String,
    /** Author avatar URL for the quote card, or `null` when the author has none. */
    val avatarUrl: String? = null,
    /**
     * Thumbnail URL of the quoted post's first image embed, or `null` when it has
     * no image media. Rendered as a small trailing thumbnail on the quote card.
     */
    val thumbnailUrl: String? = null,
    val canViewerQuote: Boolean = true,
)
