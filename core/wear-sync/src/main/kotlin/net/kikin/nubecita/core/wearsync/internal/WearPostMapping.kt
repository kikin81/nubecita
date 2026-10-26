package net.kikin.nubecita.core.wearsync.internal

import net.kikin.nubecita.core.wearcontract.WearAuthor
import net.kikin.nubecita.core.wearcontract.WearMedia
import net.kikin.nubecita.core.wearcontract.WearPost
import net.kikin.nubecita.core.wearcontract.WearViewer
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.thumbOrFullsize

/**
 * A feed snapshot ready to publish: the posts, plus which image URL each asset
 * key stands for. Avatars are deduplicated by URL, so an author who posts five
 * times costs one avatar.
 */
internal data class WearFeedDraft(
    val posts: List<WearPost>,
    val avatarUrls: Map<String, String>,
    val thumbnailUrls: Map<String, String>,
)

/**
 * Maps cached feed posts to the watch contract. Pure, so it is unit-tested
 * without Coil or the Data Layer.
 *
 * - Posts from authors the viewer mutes or blocks (either way) are dropped:
 *   the watch has no moderation UI to put them behind.
 * - Media behind a content warning keeps its counts but gets no thumbnail, so
 *   the watch never shows it unasked (same rule as the home-screen widget).
 */
internal fun List<PostUi>.toWearFeedDraft(): WearFeedDraft {
    val avatarKeys = LinkedHashMap<String, String>() // url -> key
    val thumbnails = LinkedHashMap<String, String>() // key -> url
    val posts =
        filterNot { it.viewer.isAuthorMutedByViewer || it.viewer.isAuthorBlockedByViewer || it.viewer.isAuthorBlockingViewer }
            .map { post ->
                val avatarKey = post.author.avatarUrl?.let { url -> avatarKeys.getOrPut(url) { "a${avatarKeys.size}" } }
                val media =
                    post.embed.toWearMedia()?.let { (media, thumbnailUrl) ->
                        if (thumbnailUrl == null) {
                            media
                        } else {
                            val key = "t${thumbnails.size}"
                            thumbnails[key] = thumbnailUrl
                            media.copy(thumbnailAssetKey = key)
                        }
                    }
                WearPost(
                    uri = post.id,
                    cid = post.cid,
                    author =
                        WearAuthor(
                            did = post.author.did,
                            handle = post.author.handle,
                            displayName = post.author.displayName.takeIf { it.isNotBlank() },
                            avatarAssetKey = avatarKey,
                        ),
                    text = post.text,
                    createdAtMs = post.createdAt.toEpochMilliseconds(),
                    replyCount = post.stats.replyCount,
                    repostCount = post.stats.repostCount,
                    likeCount = post.stats.likeCount,
                    repostedBy = post.repostedBy,
                    viewer = WearViewer(likeUri = post.viewer.likeUri, repostUri = post.viewer.repostUri, bookmarked = post.viewer.isBookmarked),
                    media = media,
                )
            }
    return WearFeedDraft(
        posts = posts,
        avatarUrls = avatarKeys.entries.associate { (url, key) -> key to url },
        thumbnailUrls = thumbnails,
    )
}

/** The watch's view of an embed and the URL of its thumbnail (null when hidden or absent). */
private fun EmbedUi.toWearMedia(): Pair<WearMedia, String?>? =
    when (this) {
        is EmbedUi.ImageContainerEmbed -> {
            val warned = contentWarning != null
            WearMedia(imageCount = items.size, hasContentWarning = warned) to
                (if (warned) null else items.firstOrNull()?.thumbOrFullsize())
        }
        is EmbedUi.Video -> {
            val warned = contentWarning != null
            WearMedia(isVideo = true, hasContentWarning = warned) to (if (warned) null else posterUrl)
        }
        is EmbedUi.RecordWithMedia -> media.toWearMedia()
        is EmbedUi.External,
        is EmbedUi.Gif,
        is EmbedUi.Record,
        is EmbedUi.RecordUnavailable,
        is EmbedUi.Unsupported,
        EmbedUi.Empty,
        -> null
    }
