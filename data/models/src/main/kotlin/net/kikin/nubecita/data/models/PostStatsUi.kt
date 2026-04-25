package net.kikin.nubecita.data.models

import androidx.compose.runtime.Stable

/**
 * Aggregate counts shown in the action row of a [PostUi].
 *
 * All defaults to 0 so test fixtures and previews can construct this with
 * no arguments. `quoteCount` is included for future use (Bluesky's quote
 * post counter); v1 PostCard doesn't render it but the shape stays
 * forward-compatible.
 */
@Stable
public data class PostStatsUi(
    val replyCount: Int = 0,
    val repostCount: Int = 0,
    val likeCount: Int = 0,
    val quoteCount: Int = 0,
)
