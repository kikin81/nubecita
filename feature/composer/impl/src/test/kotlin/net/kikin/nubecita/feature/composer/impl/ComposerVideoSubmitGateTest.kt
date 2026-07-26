package net.kikin.nubecita.feature.composer.impl

import androidx.compose.foundation.text.input.TextFieldState
import io.mockk.mockk
import net.kikin.nubecita.feature.composer.impl.state.ComposerState
import net.kikin.nubecita.feature.composer.impl.state.ComposerVideo
import net.kikin.nubecita.feature.composer.impl.state.ComposerVideoStage
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
    private fun stateWith(stage: ComposerVideoStage) = ComposerState(video = ComposerVideo(uri = mockk(relaxed = true), stage = stage))

    /** A video is content in its own right — no text required. */
    @Test
    fun `a ready video alone is postable`() {
        assertTrue(canPost(stateWith(ComposerVideoStage.Ready), TextFieldState()))
    }

    @Test
    fun `an in-flight upload blocks posting`() {
        listOf(
            ComposerVideoStage.CheckingLimits,
            ComposerVideoStage.Compressing,
            ComposerVideoStage.Uploading,
            ComposerVideoStage.Processing,
        ).forEach { stage ->
            assertFalse(
                canPost(stateWith(stage), TextFieldState("some text")),
                "$stage must block submission",
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
        val failed = stateWith(ComposerVideoStage.Failed)

        assertFalse(canPost(failed, TextFieldState("plenty of text here")))
    }

    @Test
    fun `an empty composer with no video is not postable`() {
        assertFalse(canPost(ComposerState(), TextFieldState()))
    }
}
