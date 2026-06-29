package net.kikin.nubecita.feature.search.impl.data

import androidx.compose.runtime.Immutable

/**
 * UI-ready projection of an `app.bsky.feed.defs#generatorView` from the
 * `app.bsky.feed.getSuggestedFeeds` surface. Drives the suggested-feeds
 * section in the Discover tab.
 *
 * - [description]: blank upstream values normalize to null (same rule as
 *   [net.kikin.nubecita.feature.search.impl.data.FeedGeneratorUi]).
 * - [avatarUrl]: null when the feed has no custom icon; callers fall back
 *   to the standard `NubecitaAsyncImage` placeholder tile.
 * - [isPinned]: false by default from the API; callers overlay true when
 *   the feed appears in the viewer's saved feeds list (Discover-tab merge).
 */
@Immutable
internal data class SuggestedFeedUi(
    val uri: String,
    val displayName: String,
    val creatorHandle: String,
    val avatarUrl: String?,
    val description: String?,
    val isPinned: Boolean = false,
)
