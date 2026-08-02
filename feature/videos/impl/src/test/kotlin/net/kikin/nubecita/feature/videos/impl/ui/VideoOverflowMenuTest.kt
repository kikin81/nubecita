package net.kikin.nubecita.feature.videos.impl.ui

import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.component.PostOverflowAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class VideoOverflowMenuTest {
    @Test
    fun `neutral viewer shows report, mute, block, mute-thread, copy`() {
        assertEquals(
            listOf(
                PostOverflowAction.ReportPost,
                PostOverflowAction.MuteAuthor,
                PostOverflowAction.BlockAuthor,
                PostOverflowAction.MuteThread,
                PostOverflowAction.CopyPostText,
                PostOverflowAction.TranslatePost,
            ),
            videoOverflowActions(ViewerStateUi()),
        )
    }

    @Test
    fun `a caption-less video omits Translate rather than offering a dead action`() {
        val actions = videoOverflowActions(ViewerStateUi(), hasText = false)
        assertFalse(actions.contains(PostOverflowAction.TranslatePost))
        // Everything else still there — only the text-dependent action goes.
        assertTrue(actions.contains(PostOverflowAction.CopyPostText))
    }

    @Test
    fun `a muted author shows Unmute, not Mute`() {
        val actions = videoOverflowActions(ViewerStateUi(isAuthorMutedByViewer = true))
        assertTrue(PostOverflowAction.UnmuteAuthor in actions)
        assertFalse(PostOverflowAction.MuteAuthor in actions)
    }

    @Test
    fun `a blocked author shows Unblock, not Block`() {
        val actions = videoOverflowActions(ViewerStateUi(isAuthorBlockedByViewer = true))
        assertTrue(PostOverflowAction.UnblockAuthor in actions)
        assertFalse(PostOverflowAction.BlockAuthor in actions)
    }

    @Test
    fun `the closed menu never offers UnmuteThread`() {
        assertFalse(PostOverflowAction.UnmuteThread in videoOverflowActions(ViewerStateUi()))
    }
}
