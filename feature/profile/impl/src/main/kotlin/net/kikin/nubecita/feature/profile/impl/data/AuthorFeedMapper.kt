package net.kikin.nubecita.feature.profile.impl.data

import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import io.github.kikin81.atproto.app.bsky.feed.ReasonRepost
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.core.feedmapping.toPostUiCore
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.thumbOrFullsize
import net.kikin.nubecita.feature.profile.impl.ProfileTab
import net.kikin.nubecita.feature.profile.impl.TabItemUi

/**
 * Maps a page of [FeedViewPost] (the wire model returned by
 * `app.bsky.feed.getAuthorFeed`) into the per-tab [TabItemUi] list
 * the ViewModel exposes via `TabLoadStatus.Loaded.items`.
 *
 * Behavior:
 * - Posts tab ([ProfileTab.Posts]) produces a [TabItemUi.Post] for every
 *   well-formed entry. Server-side `filter = posts_no_replies` already
 *   excludes replies.
 * - Replies tab ([ProfileTab.Replies]) produces a [TabItemUi.Post] only
 *   for entries that are themselves replies (`FeedViewPost.reply != null`).
 *   The atproto `posts_with_replies` filter is misleadingly named — it
 *   returns *posts and replies* (a superset of `posts_no_replies`), not
 *   replies only. Bluesky's lexicon offers no server-side "replies only"
 *   filter, so the projection happens here.
 * - Media tab ([ProfileTab.Media]) produces a [TabItemUi.MediaCell] for
 *   every entry whose embed yields a renderable thumbnail (image posts:
 *   first image; video posts: poster). Entries without media — text-only,
 *   link-only, record-only quotes — are dropped. Server-side
 *   `filter = posts_with_media` already excludes most non-media posts;
 *   the local drop is defensive against edge cases (e.g. external links
 *   with no thumbnail when `posts_with_media` is interpreted permissively
 *   by the appview).
 *
 * Reposting metadata is carried via `PostUi.repostedBy` (so the PostCard's
 * repost-author overlay renders the same way it does in the feed).
 *
 * Same delegation pattern as `:feature:postdetail:impl/data/PostThreadMapper`:
 * the embed + post-core conversion lives in `:core:feed-mapping`; only
 * the per-tab projection lives here.
 */
internal fun List<FeedViewPost>.toTabItems(tab: ProfileTab): ImmutableList<TabItemUi> =
    when (tab) {
        ProfileTab.Posts ->
            mapNotNull { it.toPostTabItemOrNull() }.toImmutableList()
        ProfileTab.Replies ->
            filter { it.reply != null }
                .mapNotNull { it.toPostTabItemOrNull() }
                .toImmutableList()
        ProfileTab.Media ->
            mapNotNull { it.toMediaCellOrNull() }.toImmutableList()
    }

private fun FeedViewPost.toPostTabItemOrNull(): TabItemUi.Post? {
    val core = post.toPostUiCore() ?: return null
    val repostedBy = (reason as? ReasonRepost)?.by?.let { it.displayName ?: it.handle.raw }
    val postUi = if (repostedBy != null) core.copy(repostedBy = repostedBy) else core
    return TabItemUi.Post(postUi)
}

private fun FeedViewPost.toMediaCellOrNull(): TabItemUi.MediaCell? {
    val core: PostUi = post.toPostUiCore() ?: return null
    val thumb = core.embed.toMediaThumbUrlOrNull() ?: return null
    return TabItemUi.MediaCell(postUri = core.id, thumbUrl = thumb)
}

/**
 * Pluck the first renderable thumb URL out of an embed. Returns
 * `null` for embed types that don't carry media — those entries
 * are dropped from the Media tab.
 *
 * Prefers `ImageUi.thumbUrl` over `ImageUi.fullsizeUrl` — the
 * appview ships pre-resized variants from `.../img/feed_thumbnail/...`
 * that are roughly an order of magnitude smaller than the fullsize
 * variant from `.../img/feed_fullsize/...`. Both URLs share the
 * `@jpeg` suffix; the variant differentiator is the path segment.
 * On a 3-col grid this is a real bandwidth win on cellular. Falls
 * back to fullsize when thumbUrl is null (defensive — the lexicon
 * requires both fields, but non-lexicon ImageUi sources may pass
 * null). Video posts already pass through `EmbedUi.Video.posterUrl`
 * (a server-side thumbnail) so they're unaffected.
 */
private fun EmbedUi.toMediaThumbUrlOrNull(): String? =
    when (this) {
        is EmbedUi.Images -> items.firstOrNull()?.thumbOrFullsize()
        is EmbedUi.Video -> posterUrl
        is EmbedUi.RecordWithMedia -> media.toMediaThumbUrlOrNull()
        EmbedUi.Empty, is EmbedUi.External, is EmbedUi.Record,
        is EmbedUi.RecordUnavailable, is EmbedUi.Unsupported,
        -> null
    }
