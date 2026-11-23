package net.kikin.nubecita.feature.search.impl.data

import androidx.compose.runtime.Immutable

/**
 * UI-ready projection of an `app.bsky.unspecced.defs#trendView` from
 * `app.bsky.unspecced.getTrends`.
 *
 * - [topic]: the server's stable id for the trend.
 * - [link]: the app-relative route the trend opens (see [parseTrendLink]).
 * - [category]: null when the server sent none.
 * - [isHot]: the server flagged the trend `hot` (rising fast).
 */
@Immutable
internal data class TrendUi(
    val topic: String,
    val displayName: String,
    val link: String,
    val category: String?,
    val postCount: Long,
    val isHot: Boolean,
)
