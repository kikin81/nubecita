package net.kikin.nubecita.core.videoupload

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class VideoAspectRatioTest {
    @ParameterizedTest(name = "{0}x{1} rot={2} -> {3}x{4}")
    @CsvSource(
        // Landscape source, no rotation — passes through.
        "1920, 1080,   0, 1920, 1080",
        "1920, 1080, 180, 1920, 1080",
        // The case that matters: a portrait recording stored as landscape
        // frames plus a rotation flag. Reporting 1920x1080 here would
        // letterbox the clip in every AT Protocol client, not just this one.
        "1920, 1080,  90, 1080, 1920",
        "1920, 1080, 270, 1080, 1920",
        // Genuinely portrait frames stay portrait.
        "1080, 1920,   0, 1080, 1920",
        "1080, 1920,  90, 1920, 1080",
        // Square is unaffected either way.
        "1080, 1080,  90, 1080, 1080",
    )
    fun `rotation swaps width and height for 90 and 270`(
        width: Int,
        height: Int,
        rotation: Int,
        expectedWidth: Long,
        expectedHeight: Long,
    ) {
        val ratio = deriveAspectRatio(width, height, rotation)

        assertEquals(expectedWidth, ratio?.width)
        assertEquals(expectedHeight, ratio?.height)
    }

    /**
     * Some containers report rotation outside 0..359. Normalizing means a
     * `-90` source is treated as the portrait case rather than falling through
     * to the unswapped branch.
     */
    @ParameterizedTest(name = "rotation {0} normalizes to a swap")
    @CsvSource("-90", "-270", "450", "630")
    fun `out of range rotations normalize before the swap decision`(rotation: Int) {
        val ratio = deriveAspectRatio(1920, 1080, rotation)

        assertEquals(1080L, ratio?.width)
        assertEquals(1920L, ratio?.height)
    }

    @Test
    fun `absent rotation is treated as zero`() {
        val ratio = deriveAspectRatio(1920, 1080, null)

        assertEquals(1920L, ratio?.width)
        assertEquals(1080L, ratio?.height)
    }

    /**
     * Omission, not a 1:1 placeholder. `aspectRatio` is optional in
     * `app.bsky.embed.video`, and a substituted square would be a silent lie
     * every client renders — the same failure the rotation handling exists to
     * prevent. An absent ratio lets each client measure for itself.
     */
    @ParameterizedTest(name = "{0}x{1} is unusable -> omitted")
    @CsvSource(
        "0, 1080",
        "1920, 0",
        "0, 0",
        "-1920, 1080",
        "1920, -1080",
    )
    fun `non-positive dimensions omit the ratio`(
        width: Int,
        height: Int,
    ) {
        assertNull(deriveAspectRatio(width, height, 0))
    }

    @Test
    fun `missing dimensions omit the ratio`() {
        assertNull(deriveAspectRatio(null, 1080, 0))
        assertNull(deriveAspectRatio(1920, null, 0))
        assertNull(deriveAspectRatio(null, null, 0))
    }
}
