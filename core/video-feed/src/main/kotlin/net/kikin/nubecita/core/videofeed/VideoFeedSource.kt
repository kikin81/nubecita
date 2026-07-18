package net.kikin.nubecita.core.videofeed

import net.kikin.nubecita.data.models.PostUi

/**
 * One page of video posts plus the cursor for the next page (`null` = end of
 * feed). [items] are guaranteed to carry an
 * [net.kikin.nubecita.data.models.EmbedUi.Video] embed.
 */
data class VideoFeedPage(
    val items: List<PostUi>,
    val cursor: String?,
)

/**
 * A cursor-paginated source of video posts for the vertical video feed. Each
 * entry point (trending, a profile's videos, …) is a different implementation;
 * the vertical player consumes this abstraction and stays source-agnostic.
 */
interface VideoFeedSource {
    /** Fetch one page starting at [cursor] (`null` for the first page). */
    suspend fun loadPage(cursor: String?): Result<VideoFeedPage>
}
