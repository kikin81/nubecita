package net.kikin.nubecita.feature.search.impl.data

import io.github.kikin81.atproto.app.bsky.feed.SearchPostsV2Request
import io.github.kikin81.atproto.runtime.Language
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Whether results must carry media. */
internal enum class MediaFilter { ANY, HAS_MEDIA, VIDEO_ONLY }

/** How replies take part in results. */
internal enum class ReplyFilter { INCLUDE, HIDE, ONLY }

/** The time window a post search covers. */
internal sealed interface DateFilter {
    data object AllTime : DateFilter

    data object PastDay : DateFilter

    data object PastWeek : DateFilter

    data object PastMonth : DateFilter

    /** Both ends inclusive, in either order. */
    data class Custom(
        val from: LocalDate,
        val to: LocalDate,
    ) : DateFilter
}

/**
 * Everything the Posts tab can narrow a search by. [sort] is part of the
 * request but is not a "filter" the user can clear, so it does not count
 * toward [activeCount].
 */
internal data class PostSearchFilters(
    val sort: SearchPostsSort = SearchPostsSort.TOP,
    val followingOnly: Boolean = false,
    val media: MediaFilter = MediaFilter.ANY,
    val replies: ReplyFilter = ReplyFilter.INCLUDE,
    /** BCP-47 language code; null means any. */
    val language: String? = null,
    val date: DateFilter = DateFilter.AllTime,
) {
    /** The number of fields that differ from their default, [sort] excluded. */
    val activeCount: Int
        get() =
            listOf(
                followingOnly,
                media != MediaFilter.ANY,
                replies != ReplyFilter.INCLUDE,
                language != null,
                date != DateFilter.AllTime,
            ).count { it }
}

/**
 * Builds the `app.bsky.feed.searchPostsV2` request for [query] under these
 * filters. Unset filters send nothing; `allTime` is always sent so a query is
 * not limited to the server's recent-posts window.
 */
internal fun PostSearchFilters.toRequest(
    query: String,
    cursor: String?,
    limit: Int,
    now: Instant,
    zone: ZoneId,
): SearchPostsV2Request {
    var since: Instant? = null
    var until: Instant? = null
    when (val window = date) {
        DateFilter.AllTime -> Unit
        DateFilter.PastDay -> since = now.minus(1, ChronoUnit.DAYS)
        DateFilter.PastWeek -> since = now.minus(7, ChronoUnit.DAYS)
        DateFilter.PastMonth -> since = now.minus(30, ChronoUnit.DAYS)
        is DateFilter.Custom -> {
            val first = minOf(window.from, window.to)
            val last = maxOf(window.from, window.to)
            since = first.atStartOfDay(zone).toInstant()
            until = minOf(last.plusDays(1).atStartOfDay(zone).toInstant(), now)
        }
    }
    return SearchPostsV2Request(
        query = query,
        cursor = cursor,
        limit = limit.toLong(),
        sort = sort.v2WireValue,
        following = true.takeIf { followingOnly },
        hasMedia = true.takeIf { media == MediaFilter.HAS_MEDIA },
        hasVideo = true.takeIf { media == MediaFilter.VIDEO_ONLY },
        excludeReplies = true.takeIf { replies == ReplyFilter.HIDE },
        repliesOnly = true.takeIf { replies == ReplyFilter.ONLY },
        languages = language?.let { listOf(Language(it)) },
        since = since?.toWireDatetime(),
        until = until?.toWireDatetime(),
        allTime = true,
    )
}

/** `searchPostsV2` names the recency sort `recent` (v1 used `latest`). */
internal val SearchPostsSort.v2WireValue: String
    get() =
        when (this) {
            SearchPostsSort.TOP -> "top"
            SearchPostsSort.LATEST -> "recent"
        }

private fun Instant.toWireDatetime(): String = truncatedTo(ChronoUnit.SECONDS).toString()
