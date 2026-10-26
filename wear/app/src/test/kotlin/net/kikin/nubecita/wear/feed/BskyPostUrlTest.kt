package net.kikin.nubecita.wear.feed

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BskyPostUrlTest {
    @Test
    fun aPostUri_becomesItsBskyLink() {
        assertEquals("https://bsky.app/profile/did:plc:bob/post/3kxyz", bskyPostUrl("at://did:plc:bob/app.bsky.feed.post/3kxyz"))
    }

    @Test
    fun anythingButAPost_hasNoLink() {
        assertNull(bskyPostUrl("at://did:plc:bob/app.bsky.feed.like/3k"))
        assertNull(bskyPostUrl("at://did:plc:bob/app.bsky.feed.post/"))
        assertNull(bskyPostUrl("https://bsky.app/profile/bob/post/3k"))
        assertNull(bskyPostUrl("at://did:plc:bob/app.bsky.feed.post/3k/extra"))
    }
}
