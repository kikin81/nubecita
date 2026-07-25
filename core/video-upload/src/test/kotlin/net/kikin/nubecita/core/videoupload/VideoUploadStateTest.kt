package net.kikin.nubecita.core.videoupload

import io.github.kikin81.atproto.app.bsky.embed.AspectRatio
import io.github.kikin81.atproto.runtime.Blob
import io.github.kikin81.atproto.runtime.CidLink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
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
    fun `progress accepts the full valid range`(progress: Float) {
        assertEquals(progress, VideoUploadState.Compressing(progress).progress)
        assertEquals(progress, VideoUploadState.Uploading(progress).progress)
        assertEquals(progress, VideoUploadState.Processing(progress).progress)
    }

    /**
     * Producers must coerce. `Transformer` and Ktor's `onUpload` both report
     * fractions computed from integer counters, which can land marginally
     * outside the range; the invariant lives here so that surfaces as a test
     * failure in this module rather than a nonsensical progress bar.
     */
    @ParameterizedTest
    @ValueSource(floats = [-0.01f, 1.01f, Float.NaN])
    fun `progress rejects values outside the valid range`(progress: Float) {
        assertThrows(IllegalArgumentException::class.java) { VideoUploadState.Compressing(progress) }
        assertThrows(IllegalArgumentException::class.java) { VideoUploadState.Uploading(progress) }
        assertThrows(IllegalArgumentException::class.java) { VideoUploadState.Processing(progress) }
    }
}
