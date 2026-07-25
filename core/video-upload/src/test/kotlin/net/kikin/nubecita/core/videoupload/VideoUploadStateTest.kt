package net.kikin.nubecita.core.videoupload

import io.github.kikin81.atproto.app.bsky.embed.AspectRatio
import io.github.kikin81.atproto.runtime.Blob
import io.github.kikin81.atproto.runtime.CidLink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class VideoUploadStateTest {
    private val blob =
        Blob(
            ref = CidLink(link = "bafyreiexamplecidforatestblobreference00000000000000"),
            mimeType = "video/mp4",
            size = 1_024L,
        )

    @Test
    fun `Ready is terminal`() {
        assertTrue(VideoUploadState.Ready(blob, AspectRatio(height = 1920, width = 1080)).isTerminal)
    }

    @Test
    fun `Failed is terminal`() {
        assertTrue(VideoUploadState.Failed(VideoUploadError.Network(null)).isTerminal)
    }

    /**
     * The contract consumers rely on: everything except Ready and Failed is a
     * step the flow will emit past. If a new in-flight stage is ever added and
     * accidentally reports terminal, the composer's submit gate would unblock
     * mid-upload and post without the video.
     */
    @Test
    fun `no other state is terminal`() {
        val inFlight =
            listOf(
                VideoUploadState.CheckingLimits,
                VideoUploadState.Compressing(0f),
                VideoUploadState.Uploading(0.5f),
                VideoUploadState.Processing(1f),
            )

        inFlight.forEach { assertFalse(it.isTerminal, "${it::class.simpleName} must not be terminal") }
    }

    @ParameterizedTest
    @ValueSource(floats = [0f, 0.5f, 1f])
    fun `asUploadProgress passes valid values through unchanged`(progress: Float) {
        assertEquals(progress, progress.asUploadProgress())
    }

    /**
     * `Transformer` and Ktor's `onUpload` derive fractions from integer
     * counters, which can land marginally outside the range. Clamping rather
     * than throwing is deliberate: a progress bar is cosmetic, so crashing an
     * in-flight upload over one would be strictly worse than a pinned bar.
     */
    @ParameterizedTest
    @ValueSource(floats = [1.01f, 1.0000001f, Float.POSITIVE_INFINITY])
    fun `asUploadProgress clamps above the range to 1`(progress: Float) {
        assertEquals(1f, progress.asUploadProgress())
    }

    @ParameterizedTest
    @ValueSource(floats = [-0.01f, -1f, Float.NEGATIVE_INFINITY])
    fun `asUploadProgress clamps below the range to 0`(progress: Float) {
        assertEquals(0f, progress.asUploadProgress())
    }

    /**
     * NaN needs its own branch: every NaN comparison is false, so
     * `Float.NaN.coerceIn(0f, 1f)` returns NaN and clamping alone would let it
     * reach the UI. A clip whose duration metadata is unreadable can produce
     * one.
     */
    @Test
    fun `asUploadProgress maps NaN to zero rather than passing it through`() {
        assertEquals(0f, Float.NaN.asUploadProgress())
        assertFalse(Float.NaN.asUploadProgress().isNaN())
    }
}
