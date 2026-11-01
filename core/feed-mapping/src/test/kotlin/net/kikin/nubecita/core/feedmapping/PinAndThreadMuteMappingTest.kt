package net.kikin.nubecita.core.feedmapping

import io.github.kikin81.atproto.app.bsky.feed.PostView
import io.github.kikin81.atproto.app.bsky.feed.ViewerState
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Server-sourced pin / thread-mute viewer flags and the reply's thread root
 * (nubecita-iwjz.3). Both flags are read straight off
 * `app.bsky.feed.defs#viewerState`; the thread root comes from the post
 * record's `reply.root.uri` and is null for a top-level post.
 */
internal class PinAndThreadMuteMappingTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Test
    fun `pinned true maps to isPinned`() {
        assertTrue(ViewerState(pinned = true).toViewerStateUi().isPinned)
    }

    @Test
    fun `threadMuted true maps to isThreadMuted`() {
        assertTrue(ViewerState(threadMuted = true).toViewerStateUi().isThreadMuted)
    }

    @Test
    fun `absent flags default to false`() {
        val ui = ViewerState().toViewerStateUi()
        assertFalse(ui.isPinned)
        assertFalse(ui.isThreadMuted)
    }

    @Test
    fun `a reply carries its thread root`() {
        val post = requireNotNull(decode(REPLY).toPostUiCore())
        assertEquals("at://did:plc:a/app.bsky.feed.post/root", post.threadRootUri)
    }

    @Test
    fun `a top-level post has no thread root`() {
        val post = requireNotNull(decode(TOP_LEVEL).toPostUiCore())
        assertNull(post.threadRootUri)
    }

    private fun decode(jsonString: String): PostView = json.decodeFromString(PostView.serializer(), jsonString)

    private companion object {
        const val TOP_LEVEL = """
            {
              "uri": "at://did:plc:fake/app.bsky.feed.post/p1",
              "cid": "bafyfake",
              "author": { "did": "did:plc:fake", "handle": "fake.bsky.social" },
              "record": {
                "${'$'}type": "app.bsky.feed.post",
                "text": "hello",
                "createdAt": "2026-01-01T12:00:00.000Z"
              },
              "indexedAt": "2026-01-01T12:00:00.000Z"
            }
        """

        const val REPLY = """
            {
              "uri": "at://did:plc:fake/app.bsky.feed.post/p2",
              "cid": "bafyfake2",
              "author": { "did": "did:plc:fake", "handle": "fake.bsky.social" },
              "record": {
                "${'$'}type": "app.bsky.feed.post",
                "text": "a reply",
                "createdAt": "2026-01-01T12:00:00.000Z",
                "reply": {
                  "root": { "uri": "at://did:plc:a/app.bsky.feed.post/root", "cid": "bafyroot" },
                  "parent": { "uri": "at://did:plc:a/app.bsky.feed.post/parent", "cid": "bafyparent" }
                }
              },
              "indexedAt": "2026-01-01T12:00:00.000Z"
            }
        """
    }
}
