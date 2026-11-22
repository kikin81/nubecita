package net.kikin.nubecita.feature.search.impl.data

import io.github.kikin81.atproto.runtime.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PostSearchFiltersTest {
    private val now = Instant.parse("2026-11-22T15:30:00Z")
    private val zone = ZoneId.of("UTC")

    private fun PostSearchFilters.request(
        query: String = "kotlin",
        cursor: String? = null,
        limit: Int = 25,
    ) = toRequest(query, cursor, limit, now, zone)

    @Test
    fun `defaults send only the query, paging, top sort and allTime`() {
        val request = PostSearchFilters().request(cursor = "c1")

        assertEquals("kotlin", request.query)
        assertEquals("c1", request.cursor)
        assertEquals(25L, request.limit)
        assertEquals("top", request.sort)
        assertEquals(true, request.allTime)
        assertNull(request.following)
        assertNull(request.hasMedia)
        assertNull(request.hasVideo)
        assertNull(request.excludeReplies)
        assertNull(request.repliesOnly)
        assertNull(request.languages)
        assertNull(request.since)
        assertNull(request.until)
    }

    @Test
    fun `latest sort maps to recent`() {
        assertEquals("recent", PostSearchFilters(sort = SearchPostsSort.LATEST).request().sort)
    }

    @Test
    fun `following only maps to following`() {
        assertEquals(true, PostSearchFilters(followingOnly = true).request().following)
    }

    @Test
    fun `has media maps to hasMedia`() {
        val request = PostSearchFilters(media = MediaFilter.HAS_MEDIA).request()
        assertEquals(true, request.hasMedia)
        assertNull(request.hasVideo)
    }

    @Test
    fun `video only maps to hasVideo`() {
        val request = PostSearchFilters(media = MediaFilter.VIDEO_ONLY).request()
        assertEquals(true, request.hasVideo)
        assertNull(request.hasMedia)
    }

    @Test
    fun `hiding replies maps to excludeReplies`() {
        val request = PostSearchFilters(replies = ReplyFilter.HIDE).request()
        assertEquals(true, request.excludeReplies)
        assertNull(request.repliesOnly)
    }

    @Test
    fun `replies only maps to repliesOnly`() {
        val request = PostSearchFilters(replies = ReplyFilter.ONLY).request()
        assertEquals(true, request.repliesOnly)
        assertNull(request.excludeReplies)
    }

    @Test
    fun `language maps to a one element languages list`() {
        assertEquals(listOf(Language("pt-BR")), PostSearchFilters(language = "pt-BR").request().languages)
    }

    @Test
    fun `past day since is now minus one day`() {
        val request = PostSearchFilters(date = DateFilter.PastDay).request()
        assertEquals("2026-11-21T15:30:00Z", request.since)
        assertNull(request.until)
    }

    @Test
    fun `past week since is now minus seven days`() {
        assertEquals("2026-11-15T15:30:00Z", PostSearchFilters(date = DateFilter.PastWeek).request().since)
    }

    @Test
    fun `past month since is now minus thirty days`() {
        assertEquals("2026-10-23T15:30:00Z", PostSearchFilters(date = DateFilter.PastMonth).request().since)
    }

    @Test
    fun `custom range runs from the start of from to the start of the day after to`() {
        val request =
            PostSearchFilters(date = DateFilter.Custom(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3))).request()
        assertEquals("2026-11-01T00:00:00Z", request.since)
        assertEquals("2026-11-04T00:00:00Z", request.until)
    }

    @Test
    fun `custom range uses the given zone for day boundaries`() {
        val request =
            PostSearchFilters(date = DateFilter.Custom(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1)))
                .toRequest("kotlin", null, 25, now, ZoneId.of("America/Los_Angeles"))
        assertEquals("2026-11-01T07:00:00Z", request.since)
        assertEquals("2026-11-02T08:00:00Z", request.until)
    }

    @Test
    fun `reversed custom range is ordered`() {
        val request =
            PostSearchFilters(date = DateFilter.Custom(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 1))).request()
        assertEquals("2026-11-01T00:00:00Z", request.since)
        assertEquals("2026-11-04T00:00:00Z", request.until)
    }

    @Test
    fun `custom range ending today caps until at now`() {
        val request =
            PostSearchFilters(date = DateFilter.Custom(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 22))).request()
        assertEquals("2026-11-20T00:00:00Z", request.since)
        assertEquals("2026-11-22T15:30:00Z", request.until)
    }

    @Test
    fun `one day custom range spans that whole day`() {
        val request =
            PostSearchFilters(date = DateFilter.Custom(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 10))).request()
        assertEquals("2026-11-10T00:00:00Z", request.since)
        assertEquals("2026-11-11T00:00:00Z", request.until)
    }

    @Test
    fun `activeCount ignores sort`() {
        assertEquals(0, PostSearchFilters(sort = SearchPostsSort.LATEST).activeCount)
        assertEquals(
            5,
            PostSearchFilters(
                sort = SearchPostsSort.LATEST,
                followingOnly = true,
                media = MediaFilter.HAS_MEDIA,
                replies = ReplyFilter.ONLY,
                language = "en",
                date = DateFilter.PastDay,
            ).activeCount,
        )
    }
}
