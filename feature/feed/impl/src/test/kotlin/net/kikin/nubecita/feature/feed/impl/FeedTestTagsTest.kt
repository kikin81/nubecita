package net.kikin.nubecita.feature.feed.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the [FeedTestTags.LIST] string value. The :benchmark module's
 * `FeedScrollBenchmark` hardcodes the same literal in its `By.res(...)`
 * call (because the macrobench module deliberately does not depend on
 * :feature:feed:impl), so a silent rename here would only surface in the
 * `run-bench` CI job — which is opt-in via label and may go a week
 * without firing. This test catches the regression in every unit-test
 * run instead.
 *
 * To rename the tag: update both this value and
 * `benchmark/src/main/kotlin/.../FeedScrollBenchmark.kt`'s
 * `FEED_LIST_RES_ID` constant in the same PR.
 */
internal class FeedTestTagsTest {
    @Test
    fun `list tag value is pinned to feed_list`() {
        assertEquals("feed_list", FeedTestTags.LIST)
    }
}
