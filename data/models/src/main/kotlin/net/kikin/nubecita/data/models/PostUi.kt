package net.kikin.nubecita.data.models

import androidx.compose.runtime.Stable
import io.github.kikin81.atproto.app.bsky.richtext.Facet
import kotlinx.collections.immutable.ImmutableList
import kotlin.time.Instant

/**
 * One frame's worth of state for rendering a Bluesky post in the UI.
 *
 * Produced by the feed / profile / notification / thread mapper layers in
 * `:feature:*:impl` from atproto wire types; consumed by `PostCard` and
 * its peers in `:designsystem`. PostCard never owns mutable state — every
 * change to a post (like toggle, repost, edit) is a new `PostUi` instance
 * emitted from the host VM.
 *
 * `facets` carries the lexicon `Facet` type directly. This is the only
 * AT Protocol type that appears in this module's UI models — see the
 * `data-models` capability spec for the wire-data-primitives policy.
 *
 * `embed` is a sealed `EmbedUi` so PostCard's dispatch is exhaustive at
 * compile time. v1 supports `Empty` and `Images`; everything else is
 * `Unsupported(typeUri)`.
 */
@Stable
public data class PostUi(
    val id: String,
    val author: AuthorUi,
    val createdAt: Instant,
    val text: String,
    val facets: ImmutableList<Facet>,
    val embed: EmbedUi,
    val stats: PostStatsUi,
    val viewer: ViewerStateUi,
    /**
     * Display name of the user who reposted this post into the current
     * feed view (e.g. `"Alice Chen"`). Null when the post appears in the
     * feed as the original author's own post.
     */
    val repostedBy: String?,
)
