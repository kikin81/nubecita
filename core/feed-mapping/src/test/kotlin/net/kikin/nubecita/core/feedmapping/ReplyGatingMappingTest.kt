package net.kikin.nubecita.core.feedmapping

import io.github.kikin81.atproto.app.bsky.feed.ViewerState
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Threadgate reply-permission mapping (lq9t.3.1). The appview computes
 * reply eligibility server-side and returns it as
 * `app.bsky.feed.defs#viewerState.replyDisabled`; we project the inverse onto
 * [net.kikin.nubecita.data.models.ViewerStateUi.canViewerReply] so the UI never
 * reimplements the follow-graph / mention rules. The mapping must fail OPEN
 * (reply allowed) when the flag is absent — most posts carry no threadgate.
 */
internal class ReplyGatingMappingTest {
    @Test
    fun `replyDisabled true maps to canViewerReply false`() {
        val ui = ViewerState(replyDisabled = true).toViewerStateUi()
        assertFalse(ui.canViewerReply, "a gated post must report the viewer cannot reply")
    }

    @Test
    fun `replyDisabled false maps to canViewerReply true`() {
        val ui = ViewerState(replyDisabled = false).toViewerStateUi()
        assertTrue(ui.canViewerReply)
    }

    @Test
    fun `absent replyDisabled fails open to canViewerReply true`() {
        // The common case: no threadgate → null replyDisabled → reply allowed.
        val ui = ViewerState().toViewerStateUi()
        assertTrue(ui.canViewerReply, "an absent threadgate flag must not disable replies")
    }

    @Test
    fun `a null post-viewer fails open to canViewerReply true`() {
        val ui = (null as ViewerState?).toViewerStateUi()
        assertTrue(ui.canViewerReply)
    }
}
