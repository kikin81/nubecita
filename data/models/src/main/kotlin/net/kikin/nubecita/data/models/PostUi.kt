package net.kikin.nubecita.data.models

import androidx.compose.runtime.Stable
import io.github.kikin81.atproto.app.bsky.richtext.Facet
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
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
    /**
     * The post's content-addressed CID, paired with [id] (its AT URI) to
     * form the `StrongRef` that `app.bsky.feed.like` and
     * `app.bsky.feed.repost` records target. Required because the host
     * VM constructs the StrongRef when calling
     * `LikeRepostRepository.like(...)` / `repost(...)` — without the CID
     * we'd be left guessing or fetching the post again.
     */
    val cid: String,
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
    /**
     * AT URI of the root post of the thread this post replies into — the
     * record's `reply.root.uri` — or null for a top-level post. A thread is
     * keyed by `threadRootUri ?: id`; thread mute targets that key.
     */
    val threadRootUri: String? = null,
    /**
     * The BCP-47 language tags the author's client declared on the record
     * (`app.bsky.feed.post.langs`), or empty when the field was absent.
     *
     * A HINT, never a gate. The field is optional on the wire and frequently
     * missing, so an empty list means "unknown", never "the reader's
     * language" — anything that gated on `langs.isNotEmpty()` would skip
     * exactly the unlabelled foreign post a reader most needs help with.
     *
     * It earns its place because the other source is worse on its own:
     * on-device language identification reads a German greeting followed by
     * English hashtags as English. Declared tags and identification each miss
     * a different case, so translation consults the declaration first and
     * falls back to identification (epic `nubecita-s6xk`).
     *
     * Defaulted so the ~40 existing fixture and test construction sites —
     * none of which care about language — stay untouched.
     */
    val langs: ImmutableList<String> = persistentListOf(),
)
