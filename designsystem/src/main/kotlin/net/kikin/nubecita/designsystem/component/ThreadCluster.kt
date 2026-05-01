package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.QuotedEmbedUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import kotlin.time.Instant

/**
 * Renders a feed-level reply cluster: root post on top, optional [ThreadFold]
 * between root and parent (when [hasEllipsis] is true), then the immediate
 * parent, then the leaf reply — joined by avatar-gutter connector lines.
 *
 * The cluster is a single `LazyColumn` item from the host's perspective, so
 * scroll-position is anchored on the leaf's URI (the most recent post in the
 * chain). Tap targets on root, parent, leaf, and the fold are no-ops in v1
 * via [PostCallbacks.None]; the [onFoldTap] parameter exists so callers can
 * wire post-detail navigation when that screen lands.
 *
 * Connector geometry: each `PostCard` in the cluster applies its own
 * `Modifier.threadConnector` (via the `connectAbove` / `connectBelow` flags)
 * with `gutterX = 40.dp` matching PostCard's 40dp avatar at 20dp horizontal
 * padding. [ThreadFold] is invoked with `gutterX = 40.dp` for the same
 * reason — keeps the line unbroken across the fold gap.
 *
 * Video slots: the leaf is the only post in the cluster that participates in
 * the feed video coordinator. Root and parent posts pass `videoEmbedSlot =
 * null`, so any video embeds in those positions render as static-poster
 * fallbacks via PostCard's internal `Unsupported` chip path. Reasoning is in
 * the openspec design (decision D3): the coordinator's "most visible" math
 * operates on whole `LazyColumn` items, but a cluster carries three
 * potentially-video posts; binding three video players per cluster would
 * expand coordinator complexity for a marginal use case.
 *
 * @param root The thread starter (`replyRef.root` from the lexicon).
 * @param parent The immediate parent (`replyRef.parent` from the lexicon).
 * @param leaf The reply post itself (the [io.github.kikin81.atproto.app.bsky.feed.FeedViewPost.post]).
 * @param hasEllipsis Whether to render a [ThreadFold] between root and
 *   parent. Wired from the mapper's grandparentAuthor heuristic — see
 *   `add-feed-cross-author-thread-cluster` design decision D5.
 * @param leafVideoEmbedSlot Forwarded to the leaf `PostCard`. Root and
 *   parent receive `null`.
 * @param leafQuotedVideoEmbedSlot Forwarded to the leaf `PostCard`'s quoted
 *   post slot. Root and parent receive `null`.
 * @param onFoldTap Tap handler for the [ThreadFold]. Wires to a no-op in v1
 *   until post-detail navigation exists.
 */
@Composable
fun ThreadCluster(
    root: PostUi,
    parent: PostUi,
    leaf: PostUi,
    modifier: Modifier = Modifier,
    callbacks: PostCallbacks = PostCallbacks.None,
    hasEllipsis: Boolean = false,
    leafVideoEmbedSlot: (@Composable (EmbedUi.Video) -> Unit)? = null,
    leafQuotedVideoEmbedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? = null,
    onFoldTap: () -> Unit = {},
) {
    // When the reply is direct to the root post, replyRef.parent and
    // replyRef.root point to the SAME post. Without this collapse the
    // cluster would render the same PostCard twice (root slot + parent
    // slot), creating visible duplication. bsky.app handles this the
    // same way — direct-reply-to-root renders as root + leaf, no parent
    // slot. The mapper still produces a ReplyCluster with both fields
    // populated; this composable owns the dedup so the data shape stays
    // simple (no Optional<parent>).
    val parentEqualsRoot = parent.id == root.id
    Column(modifier = modifier) {
        PostCard(
            post = root,
            callbacks = callbacks,
            connectAbove = false,
            connectBelow = true,
        )
        if (hasEllipsis) {
            ThreadFold(gutterX = 40.dp, onClick = onFoldTap)
        }
        if (!parentEqualsRoot) {
            PostCard(
                post = parent,
                callbacks = callbacks,
                connectAbove = true,
                connectBelow = true,
            )
        }
        PostCard(
            post = leaf,
            callbacks = callbacks,
            connectAbove = true,
            connectBelow = false,
            videoEmbedSlot = leafVideoEmbedSlot,
            quotedVideoEmbedSlot = leafQuotedVideoEmbedSlot,
        )
    }
}

private val PREVIEW_CREATED_AT: Instant = Instant.parse("2025-10-15T12:00:00Z")

private fun previewPost(
    id: String,
    handle: String,
    displayName: String,
    text: String,
): PostUi =
    PostUi(
        id = id,
        cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
        author =
            AuthorUi(
                did = "did:plc:$id",
                handle = handle,
                displayName = displayName,
                avatarUrl = null,
            ),
        createdAt = PREVIEW_CREATED_AT,
        text = text,
        facets = persistentListOf(),
        embed = EmbedUi.Empty,
        stats = PostStatsUi(replyCount = 3, repostCount = 1, likeCount = 8),
        viewer = ViewerStateUi(),
        repostedBy = null,
    )

@Preview
@Composable
private fun ThreadClusterWithoutEllipsisPreview() {
    NubecitaTheme(dynamicColor = false) {
        ThreadCluster(
            root = previewPost("root", "rootauthor.bsky.social", "Root Author", "Starting a thread."),
            parent = previewPost("parent", "parentauthor.bsky.social", "Parent Author", "Direct child of root."),
            leaf = previewPost("leaf", "leafauthor.bsky.social", "Leaf Author", "And this is the reply."),
            hasEllipsis = false,
        )
    }
}

@Preview
@Composable
private fun ThreadClusterWithEllipsisPreview() {
    NubecitaTheme(dynamicColor = false) {
        ThreadCluster(
            root = previewPost("root", "rootauthor.bsky.social", "Root Author", "Starting a thread."),
            parent = previewPost("parent", "parentauthor.bsky.social", "Parent Author", "Replying further down the chain."),
            leaf = previewPost("leaf", "leafauthor.bsky.social", "Leaf Author", "And this is the latest reply."),
            hasEllipsis = true,
        )
    }
}
