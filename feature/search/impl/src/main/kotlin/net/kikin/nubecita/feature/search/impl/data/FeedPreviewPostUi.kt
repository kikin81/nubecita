package net.kikin.nubecita.feature.search.impl.data

import androidx.compose.runtime.Immutable

/**
 * A minimal post projection for the feed-preview strip in the Discover tab.
 *
 * Maps the first ≤3 posts from `app.bsky.feed.getFeed` to this stripped-
 * down shape — enough to render a compact preview row (avatar + author
 * handle + text snippet + optional thumbnail). Full [net.kikin.nubecita.data.models.PostUi]
 * projection is not needed here; the Discover surface shows a lightweight
 * taster, not a fully-interactive post card.
 *
 * - [thumbnailUrl]: the first image/video-poster/external-thumb URL from
 *   the post's embed, or null if the post has no visual embed.
 */
@Immutable
internal data class FeedPreviewPostUi(
    val authorHandle: String,
    val authorAvatarUrl: String?,
    val text: String,
    val thumbnailUrl: String?,
)
