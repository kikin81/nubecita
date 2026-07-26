package net.kikin.nubecita.core.videoupload.internal

import net.kikin.nubecita.core.videoupload.VideoUploadError
import net.kikin.nubecita.core.videoupload.VideoUploadLimits.MAX_UPLOAD_BYTES
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VideoCompressorTest {
    @Test
    fun `a file within the cap passes`() {
        assertNull(verifyWithinCap(sizeBytes = 50L * 1024 * 1024))
    }

    @Test
    fun `a file exactly at the cap passes`() {
        assertNull(verifyWithinCap(sizeBytes = MAX_UPLOAD_BYTES))
    }

    /**
     * The check exists because neither bitrate path guarantees the result: an
     * unreadable duration falls back to a default, and an encoder targets a
     * rate rather than obeying a ceiling. Failing here costs one wasted
     * transcode; not failing costs a transcode *and* a full upload of a file
     * the service will refuse.
     */
    @Test
    fun `a file over the cap fails rather than being uploaded`() {
        val error = verifyWithinCap(sizeBytes = MAX_UPLOAD_BYTES + 1)

        assertNotNull(error)
        assertTrue(error is VideoUploadError.CompressionFailed)
    }

    @Test
    fun `the failure message names both sizes so the user can act on it`() {
        val error = verifyWithinCap(sizeBytes = 150L * 1024 * 1024) as VideoUploadError.CompressionFailed

        assertTrue(error.message!!.contains("150MB"), "actual size missing from: ${error.message}")
        assertTrue(error.message!!.contains("100MB"), "limit missing from: ${error.message}")
    }
}
