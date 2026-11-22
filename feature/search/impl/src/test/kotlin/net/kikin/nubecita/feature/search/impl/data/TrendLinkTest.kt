package net.kikin.nubecita.feature.search.impl.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TrendLinkTest {
    @Test
    fun `a feed link with a did actor is a Feed`() {
        assertEquals(TrendLink.Feed("did:plc:abc", "xyz"), parseTrendLink("/profile/did:plc:abc/feed/xyz"))
    }

    @Test
    fun `a feed link with a handle actor is a Feed`() {
        assertEquals(TrendLink.Feed("bsky.app", "xyz"), parseTrendLink("/profile/bsky.app/feed/xyz"))
    }

    @Test
    fun `a starter pack link is a StarterPack`() {
        assertEquals(TrendLink.StarterPack("alice.test", "3k"), parseTrendLink("/starter-pack/alice.test/3k"))
    }

    @Test
    fun `a search link carries its decoded query`() {
        assertEquals(TrendLink.Search("lunar eclipse"), parseTrendLink("/search?q=lunar%20eclipse"))
    }

    @Test
    fun `a search link with other params still finds q`() {
        assertEquals(TrendLink.Search("a b"), parseTrendLink("/search?ref=x&q=a+b"))
    }

    @Test
    fun `a search link without a query is Unknown`() {
        assertEquals(TrendLink.Unknown, parseTrendLink("/search"))
        assertEquals(TrendLink.Unknown, parseTrendLink("/search?q="))
    }

    @Test
    fun `an empty link is Unknown`() {
        assertEquals(TrendLink.Unknown, parseTrendLink(""))
    }

    @Test
    fun `a bare slash is Unknown`() {
        assertEquals(TrendLink.Unknown, parseTrendLink("/"))
    }

    @Test
    fun `a profile link without a feed is Unknown`() {
        assertEquals(TrendLink.Unknown, parseTrendLink("/profile/did:plc:abc"))
    }

    @Test
    fun `a feed link without a record key is Unknown`() {
        assertEquals(TrendLink.Unknown, parseTrendLink("/profile/did:plc:abc/feed/"))
    }

    @Test
    fun `an unrelated link is Unknown`() {
        assertEquals(TrendLink.Unknown, parseTrendLink("/hashtag/kotlin"))
        assertEquals(TrendLink.Unknown, parseTrendLink("/profile/did:plc:abc/feed/xyz/extra"))
    }

    @Test
    fun `an absolute bsky app link is read like the relative one`() {
        assertEquals(TrendLink.Feed("did:plc:abc", "xyz"), parseTrendLink("https://bsky.app/profile/did:plc:abc/feed/xyz"))
    }

    @Test
    fun `a trailing slash does not change a feed link`() {
        assertEquals(TrendLink.Feed("did:plc:abc", "xyz"), parseTrendLink("/profile/did:plc:abc/feed/xyz/"))
    }

    @Test
    fun `a query string does not change a feed link`() {
        assertEquals(TrendLink.Feed("did:plc:abc", "xyz"), parseTrendLink("/profile/did:plc:abc/feed/xyz?ref=x"))
    }
}
