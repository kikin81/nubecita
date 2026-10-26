package net.kikin.nubecita.core.wearcontract

import kotlinx.serialization.Serializable

/**
 * One synced feed, published at [WearPaths.feed] (nubecita-jh9c.2).
 *
 * Images never travel inline: an [WearAuthor.avatarAssetKey] or
 * [WearMedia.thumbnailAssetKey] names an `Asset` stored beside the payload in
 * the same Data Layer item (Data Layer payloads are capped at 100 KB; a feed of
 * ~50 posts is ~19 KB of JSON, the images are most of the bytes). Avatars are
 * shared across a snapshot, so one author's avatar is sent once.
 */
@Serializable
data class WearFeedSnapshot(
    override val schemaVersion: Int = WearSchema.CURRENT,
    val publishedAtMs: Long,
    val accountDid: String,
    val feedKey: String,
    /** Display name of a custom feed or list; null for Following (the watch localizes it). */
    val feedName: String? = null,
    val posts: List<WearPost>,
) : WearPayload

@Serializable
data class WearPost(
    val uri: String,
    val cid: String,
    val author: WearAuthor,
    val text: String,
    val createdAtMs: Long,
    val replyCount: Int = 0,
    val repostCount: Int = 0,
    val likeCount: Int = 0,
    /** Handle of the account that reposted this into the feed, if it's a repost. */
    val repostedBy: String? = null,
    val viewer: WearViewer = WearViewer(),
    val media: WearMedia? = null,
)

@Serializable
data class WearAuthor(
    val did: String,
    val handle: String,
    val displayName: String? = null,
    val avatarAssetKey: String? = null,
)

/**
 * The viewer's own state on a post. [likeUri] is the like record to delete when
 * the watch unlikes (W3); a non-null value means "liked".
 */
@Serializable
data class WearViewer(
    val likeUri: String? = null,
    /** The viewer's repost record, needed to undo a repost from the watch. */
    val repostUri: String? = null,
    val bookmarked: Boolean = false,
)

/**
 * What the watch shows for a post's media: one thumbnail (the first image, or a
 * video's poster frame) plus how many images there are. [thumbnailAssetKey] is
 * null when the media sits behind a content warning, so the watch never shows
 * it unasked.
 */
@Serializable
data class WearMedia(
    val imageCount: Int = 0,
    val isVideo: Boolean = false,
    val hasContentWarning: Boolean = false,
    val thumbnailAssetKey: String? = null,
)
