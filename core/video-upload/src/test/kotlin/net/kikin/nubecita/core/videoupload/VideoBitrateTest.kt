package net.kikin.nubecita.core.videoupload

import net.kikin.nubecita.core.videoupload.VideoUploadLimits.DEFAULT_BITRATE_BPS
import net.kikin.nubecita.core.videoupload.VideoUploadLimits.MAX_DURATION_MS
import net.kikin.nubecita.core.videoupload.VideoUploadLimits.MAX_UPLOAD_BYTES
import net.kikin.nubecita.core.videoupload.VideoUploadLimits.SIZE_BUDGET_BYTES
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class VideoBitrateTest {
    @Test
    fun `a long clip gets a lower bitrate than a short one`() {
        val short = targetBitrateBps(durationMs = 15_000)
        val long = targetBitrateBps(durationMs = MAX_DURATION_MS)

        assertTrue(long < short, "expected $long < $short — bitrate must scale down with duration")
    }

    /**
     * The reason a fixed bitrate cannot work: at any value high enough to look
     * acceptable on a short clip, a clip near the duration limit overflows the
     * cap. A short clip should therefore sit at the default rather than being
     * handed the whole budget.
     */
    @Test
    fun `a short clip is capped at the default rather than consuming the budget`() {
        assertEquals(DEFAULT_BITRATE_BPS, targetBitrateBps(durationMs = 5_000))
    }

    /**
     * The property the whole decision exists to provide, checked across the
     * accepted duration range rather than at one point.
     */
    @ParameterizedTest(name = "{0}ms encodes within the cap")
    @ValueSource(longs = [1_000, 15_000, 60_000, 120_000, 180_000])
    fun `the computed bitrate keeps the encode inside the service cap`(durationMs: Long) {
        val bitrate = targetBitrateBps(durationMs)
        val projectedBytes = bitrate * durationMs / 1000 / 8

        assertTrue(
            projectedBytes <= MAX_UPLOAD_BYTES,
            "projected ${projectedBytes}B exceeds cap ${MAX_UPLOAD_BYTES}B at ${durationMs}ms",
        )
    }

    /**
     * `MediaMetadataRetriever` returns null for a corrupt container, and a zero
     * would be a division by zero. Falling back is the safe branch — and it is
     * precisely why the caller must still size-check the encoded file, since
     * this path abandons the computed bound.
     */
    @ParameterizedTest(name = "duration {0} falls back without dividing")
    @ValueSource(longs = [0, -1, -180_000])
    fun `non-positive duration falls back to the default`(durationMs: Long) {
        assertEquals(DEFAULT_BITRATE_BPS, targetBitrateBps(durationMs))
    }

    @Test
    fun `unknown duration falls back to the default`() {
        assertEquals(DEFAULT_BITRATE_BPS, targetBitrateBps(durationMs = null))
    }

    /**
     * A clip long enough to drive the computed value into unwatchable territory
     * is clamped, so it produces a file that fails the post-encode size check
     * with a clear error rather than uploading a smear.
     */
    @Test
    fun `an absurdly long clip clamps at the floor rather than approaching zero`() {
        val bitrate = targetBitrateBps(durationMs = 10 * 60 * 60 * 1000L)

        assertEquals(MIN_BITRATE_BPS, bitrate)
    }

    @Test
    fun `the size budget leaves headroom under the hard cap`() {
        assertTrue(
            SIZE_BUDGET_BYTES < MAX_UPLOAD_BYTES,
            "the encoder targets a rate rather than a ceiling, so the budget needs margin",
        )
    }
}
