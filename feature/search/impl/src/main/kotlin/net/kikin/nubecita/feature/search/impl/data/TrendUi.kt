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
 * - [id]: the stable identity the UI keys rows by: [topic], or [link] when the topic is blank.
 */
@Immutable
internal data class TrendUi(
    val topic: String,
    val displayName: String,
    val link: String,
    val category: String?,
    val postCount: Long,
    val isHot: Boolean,
) {
    val id: String get() = topic.ifBlank { link }
}

/**
 * The trends the UI can show: drops a trend with neither a topic nor a link (it can be
 * neither keyed nor opened) and keeps only the first of any trends sharing an [TrendUi.id],
 * in server order. The server is an unspecced endpoint, so this is enforced at the source.
 */
internal fun List<TrendUi>.usableTrends(): List<TrendUi> = filter { it.id.isNotBlank() }.distinctBy { it.id }
