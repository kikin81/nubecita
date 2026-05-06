package net.kikin.nubecita.shell.composer

import net.kikin.nubecita.core.common.navigation.ComposerOverlayState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ComposerLauncherStateTest {
    @Test
    fun initialState_isClosed() {
        val state = ComposerLauncherState()
        assertEquals(ComposerOverlayState.Closed, state.state)
    }

    @Test
    fun show_withReplyUri_movesToOpenReply() {
        val state = ComposerLauncherState()

        state.show("at://did:plc:alice/app.bsky.feed.post/abc")

        assertEquals(
            ComposerOverlayState.Open(replyToUri = "at://did:plc:alice/app.bsky.feed.post/abc"),
            state.state,
        )
    }

    @Test
    fun show_withNullUri_movesToOpenNewPost() {
        val state = ComposerLauncherState()

        state.show(null)

        assertEquals(ComposerOverlayState.Open(replyToUri = null), state.state)
    }

    @Test
    fun close_movesBackToClosed() {
        val state = ComposerLauncherState()
        state.show("at://did:plc:alice/app.bsky.feed.post/abc")

        state.close()

        assertEquals(ComposerOverlayState.Closed, state.state)
    }

    @Test
    fun show_whileOpen_replacesReplyUri() {
        // Single-shot semantics: re-launching while already open
        // overwrites the previous replyToUri rather than stacking.
        // V1 doesn't have a UX surface that triggers this, but the
        // state-holder shape (single MutableState) makes it the
        // observable behavior; pinning it.
        val state = ComposerLauncherState()
        state.show("at://did:plc:alice/app.bsky.feed.post/abc")

        state.show("at://did:plc:bob/app.bsky.feed.post/xyz")

        assertEquals(
            ComposerOverlayState.Open(replyToUri = "at://did:plc:bob/app.bsky.feed.post/xyz"),
            state.state,
        )
    }
}
