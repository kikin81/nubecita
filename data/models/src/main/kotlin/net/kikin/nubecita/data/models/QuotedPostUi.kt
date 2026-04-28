package net.kikin.nubecita.data.models

import androidx.compose.runtime.Immutable
import io.github.kikin81.atproto.app.bsky.richtext.Facet
import kotlinx.collections.immutable.ImmutableList
import kotlin.time.Instant

/**
 * One frame's worth of state for rendering a Bluesky quoted post
 * (`app.bsky.embed.record#viewRecord`) inside another post's
 * `PostCard`.
 *
 * Carried by [EmbedUi.Record]. The render layer renders this at
 * near-parent density (smaller avatar, single-line author row, no
 * action row, no interaction stats) — the data model deliberately
 * omits counts and viewer state because v1 does not surface them.
 * Reserved for future per-variant copy upgrades.
 *
 * - [uri] is the quoted post's AT URI (`at://<did>/<collection>/<rkey>`);
 *   used as the post-identity key for navigation and as the
 *   video-binding identity when [embed] is [QuotedEmbedUi.Video].
 * - [cid] is the quoted post's content ID — carried for caching and
 *   intent-handoff scenarios that need the exact-version contract
 *   the AT Protocol provides via `cid`. The render layer does NOT
 *   display this.
 * - [embed] is bounded to one level of recursion at the type
 *   system: [QuotedEmbedUi] deliberately excludes a `Record`
 *   variant. A nested record in the wire data maps to
 *   [QuotedEmbedUi.QuotedThreadChip] at the mapper boundary.
 */
@Immutable
public data class QuotedPostUi(
    val uri: String,
    val cid: String,
    val author: AuthorUi,
    val createdAt: Instant,
    val text: String,
    val facets: ImmutableList<Facet>,
    val embed: QuotedEmbedUi,
)
