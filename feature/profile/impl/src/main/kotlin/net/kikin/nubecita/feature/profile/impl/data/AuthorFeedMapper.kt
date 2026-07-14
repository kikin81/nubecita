package net.kikin.nubecita.feature.profile.impl.data

import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import io.github.kikin81.atproto.app.bsky.feed.ReasonRepost
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.core.feedmapping.applyModeration
import net.kikin.nubecita.core.feedmapping.toPostUiCore
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.MediaContentWarning
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
 *
 * Content moderation runs here, off the render path, against the cached
 * [prefs] + [viewerDid]: every tab uses `dropFiltered = true` (profile feeds
 * are list surfaces). Posts/Replies render PostCards, so warned media is
 * *covered* (the warning is baked onto the embed). The Media grid renders raw
 * thumbnails with no cover affordance, so a *covered* post is dropped from it
 * too — never show an uncovered NSFW thumbnail in the grid.
 */
internal fun List<FeedViewPost>.toTabItems(
    tab: ProfileTab,
    prefs: ModerationPrefs,
    viewerDid: String?,
): ImmutableList<TabItemUi> =
    when (tab) {
        ProfileTab.Posts ->
            mapNotNull { it.toPostTabItemOrNull(prefs, viewerDid) }.toImmutableList()
        ProfileTab.Replies ->
            filter { it.reply != null }
                .mapNotNull { it.toPostTabItemOrNull(prefs, viewerDid) }
                .toImmutableList()
        ProfileTab.Media ->
            mapNotNull { it.toMediaCellOrNull(prefs, viewerDid) }.toImmutableList()
        // Liked posts (getActorLikes) render as normal post cards, same as Posts.
        ProfileTab.Likes ->
            mapNotNull { it.toPostTabItemOrNull(prefs, viewerDid) }.toImmutableList()
    }

private fun FeedViewPost.toPostTabItemOrNull(
    prefs: ModerationPrefs,
    viewerDid: String?,
): TabItemUi.Post? {
    val core =
        post
            .toPostUiCore()
            ?.applyModeration(post.labels, viewerDid, prefs, dropFiltered = true)
            ?: return null
    val repost = reason as? ReasonRepost
    val repostedBy = repost?.by?.let { it.displayName ?: it.handle.raw }
    val postUi = if (repostedBy != null) core.copy(repostedBy = repostedBy) else core
    return TabItemUi.Post(postUi, reposterDid = repost?.by?.did?.raw)
}

private fun FeedViewPost.toMediaCellOrNull(
    prefs: ModerationPrefs,
    viewerDid: String?,
): TabItemUi.MediaCell? {
    val core: PostUi =
        post
            .toPostUiCore()
            ?.applyModeration(post.labels, viewerDid, prefs, dropFiltered = true)
            ?: return null
    // The grid has no cover affordance, so a covered (warned-but-not-filtered)
    // post is dropped rather than shown as an uncovered thumbnail.
    if (core.embed.mediaContentWarning() != null) return null
    val thumb = core.embed.toMediaThumbUrlOrNull() ?: return null
    return TabItemUi.MediaCell(
        postUri = core.id,
        thumbUrl = thumb,
        isVideo = core.embed.isVideoMedia(),
        reposterDid = (reason as? ReasonRepost)?.by?.did?.raw,
    )
}

/**
 * The content warning baked onto this embed's media slot by `applyModeration`,
 * or `null` when none. Reads the direct [EmbedUi.MediaEmbed] cover or the
 * `media` half of a [EmbedUi.RecordWithMedia].
 */
private fun EmbedUi.mediaContentWarning(): MediaContentWarning? =
    when (this) {
        is EmbedUi.MediaEmbed -> contentWarning
        is EmbedUi.RecordWithMedia -> media.contentWarning
        else -> null
    }

/**
 * True when the embed surfaces a playable video — either as a direct
 * [EmbedUi.Video] or nested inside a [EmbedUi.RecordWithMedia]'s media
 * slot. The Media tab routes these taps to the fullscreen player
 * instead of the MediaViewer.
 */
private fun EmbedUi.isVideoMedia(): Boolean =
    when (this) {
        is EmbedUi.Video -> true
        is EmbedUi.RecordWithMedia -> media.isVideoMedia()
        else -> false
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
        is EmbedUi.ImageContainerEmbed -> items.firstOrNull()?.thumbOrFullsize()
        is EmbedUi.Video -> posterUrl
        is EmbedUi.RecordWithMedia -> media.toMediaThumbUrlOrNull()
        // GIFs are intentionally excluded from the Media grid. A Media-cell tap
        // routes through ProfileViewModel to MediaViewer, which only resolves
        // image-container embeds (Images / Gallery) and would dead-end a GIF as
        // MediaViewerError.NoImages ("This post has no images"). Re-add once a
        // GIF tap path exists.
        EmbedUi.Empty, is EmbedUi.External, is EmbedUi.Gif, is EmbedUi.Record,
        is EmbedUi.RecordUnavailable, is EmbedUi.Unsupported,
        -> null
    }
