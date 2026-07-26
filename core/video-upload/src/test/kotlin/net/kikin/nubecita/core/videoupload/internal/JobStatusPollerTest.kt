package net.kikin.nubecita.core.videoupload.internal

import io.github.kikin81.atproto.app.bsky.video.JobStatus
import io.github.kikin81.atproto.runtime.Blob
import io.github.kikin81.atproto.runtime.CidLink
import io.github.kikin81.atproto.runtime.Did
import net.kikin.nubecita.core.videoupload.VideoUploadError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class JobStatusPollerTest {
    private val blob =
        Blob(
            ref = CidLink(link = "bafyreiexamplecidforatestblobreference00000000000000"),
            mimeType = "video/mp4",
            size = 2_048L,
        )

    private fun status(
        state: String,
        blob: Blob? = null,
        error: String? = null,
        message: String? = null,
    ) = JobStatus(
        blob = blob,
        did = Did("did:plc:example"),
        error = error,
        jobId = "job-1",
        message = message,
        progress = null,
        state = state,
    )

    @Test
    fun `a blob resolves the job regardless of state string`() {
        val outcome = interpretJobStatus(status(state = "JOB_STATE_COMPLETED", blob = blob))

        assertEquals(JobOutcome.Ready(blob), outcome)
    }

    @Test
    fun `an explicit failure surfaces the server reason`() {
        val outcome = interpretJobStatus(status(state = "JOB_STATE_FAILED", error = "TranscodeFailed"))

        assertEquals(
            JobOutcome.Failed(VideoUploadError.ProcessingFailed("TranscodeFailed")),
            outcome,
        )
    }

    @Test
    fun `a failure falls back to message when error is absent`() {
        val outcome = interpretJobStatus(status(state = "JOB_STATE_FAILED", message = "codec unsupported"))

        assertEquals(
            JobOutcome.Failed(VideoUploadError.ProcessingFailed("codec unsupported")),
            outcome,
        )
    }

    /**
     * The lexicon is explicit: *"All values not listed as a known value
     * indicate that the job is in process."* A strict enum would turn any
     * state Bluesky adds later into a spurious failure on a video that
     * actually uploaded fine — so unknown must mean keep polling.
     */
    @ParameterizedTest(name = "unknown state {0} keeps polling")
    @ValueSource(
        strings = [
            "JOB_STATE_ENCODING",
            "JOB_STATE_SCANNING",
            "JOB_STATE_SOMETHING_BLUESKY_ADDS_LATER",
            "",
        ],
    )
    fun `unknown states are treated as in progress`(state: String) {
        assertNull(interpretJobStatus(status(state = state)))
    }

    /** Completed but no blob yet is still "not done" — do not resolve early. */
    @Test
    fun `completed without a blob keeps polling`() {
        assertNull(interpretJobStatus(status(state = "JOB_STATE_COMPLETED")))
    }

    @Test
    fun `failure matching is case insensitive`() {
        val outcome = interpretJobStatus(status(state = "job_state_failed", error = "nope"))

        assertTrue(outcome is JobOutcome.Failed)
    }
}
