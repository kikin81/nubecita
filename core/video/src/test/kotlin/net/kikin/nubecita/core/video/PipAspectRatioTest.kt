package net.kikin.nubecita.core.video

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PipAspectRatioTest {
    @Test
    fun `an in-range ratio is returned unchanged`() {
        assertEquals(1.7777778f, clampPipAspectRatio(1.7777778f)) // 16:9
    }

    @Test
    fun `a too-wide ratio is clamped to the max Android allows`() {
        // Android rejects PiP aspect ratios wider than ~2.39:1 (setAspectRatio throws).
        assertEquals(MAX_PIP_ASPECT_RATIO, clampPipAspectRatio(5.0f))
    }

    @Test
    fun `a too-tall ratio is clamped to the min Android allows`() {
        assertEquals(MIN_PIP_ASPECT_RATIO, clampPipAspectRatio(0.1f))
    }

    @Test
    fun `a non-positive ratio falls back to the default`() {
        assertEquals(DEFAULT_PIP_ASPECT_RATIO, clampPipAspectRatio(0f))
        assertEquals(DEFAULT_PIP_ASPECT_RATIO, clampPipAspectRatio(-2f))
    }

    @Test
    fun `a non-finite ratio falls back to the default`() {
        assertEquals(DEFAULT_PIP_ASPECT_RATIO, clampPipAspectRatio(Float.NaN))
        assertEquals(DEFAULT_PIP_ASPECT_RATIO, clampPipAspectRatio(Float.POSITIVE_INFINITY))
    }
}
