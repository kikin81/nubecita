package net.kikin.nubecita.core.videofeed

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AuthorVideoSourceTest {
    @Test
    fun request_usesPostsWithVideoFilterAndActor() {
        val request = authorVideoFeedRequest(actor = "did:plc:abc", cursor = null)
        assertEquals("posts_with_video", request.filter)
        assertEquals("did:plc:abc", request.actor.raw)
        assertEquals(30L, request.limit)
        assertNull(request.cursor)
    }

    @Test
    fun request_threadsCursor() {
        val request = authorVideoFeedRequest(actor = "did:plc:abc", cursor = "page2")
        assertEquals("page2", request.cursor)
    }
}
