package net.kikin.nubecita.designsystem.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the [PostCardTestTags.BODY] value. `:benchmark`'s
 * `BaselineProfileGenerator` hardcodes the same literal (the macrobench
 * module deliberately does not depend on `:designsystem`), so a silent rename
 * would only surface as a skipped journey during a manual profile regen.
 */
internal class PostCardTestTagsTest {
    @Test
    fun `body tag value is pinned to post_card_body`() {
        assertEquals("post_card_body", PostCardTestTags.BODY)
    }
}
