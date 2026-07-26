package net.kikin.nubecita.feature.composer.impl

import androidx.compose.foundation.text.input.TextFieldState
import io.github.kikin81.atproto.app.bsky.embed.AspectRatio
import io.github.kikin81.atproto.runtime.Blob
import io.github.kikin81.atproto.runtime.CidLink
import io.mockk.mockk
import net.kikin.nubecita.core.videoupload.VideoUploadError
import net.kikin.nubecita.core.videoupload.VideoUploadState
import net.kikin.nubecita.feature.composer.impl.state.ComposerState
import net.kikin.nubecita.feature.composer.impl.state.ComposerVideo
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The submit gate for the video slot.
 *
 * This is the guard that matters: a post must never be created that silently
 * drops a video the user attached. `readyEmbed()` is a second, type-level
 * guard — it cannot fabricate an embed without a `Ready` state — but this is
 * the one that keeps the button disabled and the user informed.
 */
class ComposerVideoSubmitGateTest {
    private val ready =
        VideoUploadState.Ready(
            blob =
                Blob(
                    ref = CidLink(link = "bafyreiexamplecidforatestblobreference00000000000000"),
                    mimeType = "video/mp4",
                    size = 1_024L,
                ),
            aspectRatio = AspectRatio(width = 1080, height = 1920),
        )

    private fun stateWith(uploadState: VideoUploadState) = ComposerState(video = ComposerVideo(uri = mockk(relaxed = true), uploadState = uploadState))

    /** A video is content in its own right — no text required. */
    @Test
    fun `a ready video alone is postable`() {
        assertTrue(canPost(stateWith(ready), TextFieldState()))
    }

    @Test
    fun `an in-flight upload blocks posting`() {
        listOf(
            VideoUploadState.CheckingLimits,
            VideoUploadState.Compressing(0.1f),
            VideoUploadState.Uploading(0.99f),
            VideoUploadState.Processing(0.5f),
        ).forEach { state ->
            assertFalse(
                canPost(stateWith(state), TextFieldState("some text")),
                "${state::class.simpleName} must block submission",
            )
        }
    }

    /**
     * Failed stays disabled so the retry affordance is the only way forward.
     * Allowing submission here would publish a post missing the video the user
     * explicitly attached.
     */
    @Test
    fun `a failed upload blocks posting even with text`() {
        val failed = stateWith(VideoUploadState.Failed(VideoUploadError.Network("dropped")))

        assertFalse(canPost(failed, TextFieldState("plenty of text here")))
    }

    @Test
    fun `an empty composer with no video is not postable`() {
        assertFalse(canPost(ComposerState(), TextFieldState()))
    }
}
