package net.kikin.nubecita.feature.search.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the tag strings that `benchmark/.../BenchmarkConstants.kt` hardcodes
 * (`SEARCH_POSTS_LIST_RES_ID`, `SEARCH_EXPLORE_LIST_RES_ID`,
 * `SEARCH_SUMMARY_LIST_RES_ID`). Rename both sides in the same PR.
 */
internal class SearchTestTagsTest {
    @Test
    fun `list tags are pinned to the benchmark's literals`() {
        assertEquals("search_posts_list", SearchTestTags.POSTS_LIST)
        assertEquals("search_explore_list", SearchTestTags.EXPLORE_LIST)
        assertEquals("search_summary_list", SearchTestTags.SUMMARY_LIST)
    }
}
