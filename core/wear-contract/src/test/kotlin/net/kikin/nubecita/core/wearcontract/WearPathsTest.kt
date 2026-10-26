package net.kikin.nubecita.core.wearcontract

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WearPathsTest {
    private val did = "did:plc:alice"

    @Test
    fun everyPath_isUnderTheV1Prefix() {
        listOf(
            WearPaths.SESSION,
            WearPaths.SYNC_REQUEST,
            WearPaths.account(did),
            WearPaths.feed(did, WearFeedKey.FOLLOWING),
            WearPaths.dms(did),
            WearPaths.action(did, "a1"),
            WearPaths.actionResult(did, "a1"),
        ).forEach { assertTrue(it.startsWith(WearPaths.PREFIX)) { it } }
    }

    @Test
    fun accountScopedPaths_shareTheAccountPrefix() {
        val root = WearPaths.account(did)

        assertEquals("/v1/did:plc:alice/", root)
        listOf(
            WearPaths.feed(did, WearFeedKey.FOLLOWING),
            WearPaths.dms(did),
            WearPaths.action(did, "a1"),
            WearPaths.actionResult(did, "a1"),
        ).forEach { assertTrue(it.startsWith(root)) { it } }
    }

    @Test
    fun actionAndResultPaths_neverOverlap() {
        // A listener matching actions by prefix must never pick up results.
        val actionPrefix = WearPaths.action(did, "a1").removeSuffix("a1")

        assertFalse(WearPaths.actionResult(did, "a1").startsWith(actionPrefix))
    }

    @Test
    fun segmentsWithSlashes_areRejected() {
        assertThrows<IllegalArgumentException> { WearPaths.feed(did, "at://did:plc:x/app.bsky.feed.generator/y") }
        assertThrows<IllegalArgumentException> { WearPaths.action(did, "") }
    }

    @Test
    fun feedKey_forAUri_isPathSafe_andReversible() {
        val uri = "at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.generator/whats-hot"

        val key = WearFeedKey.forFeedUri(uri)

        assertFalse('/' in key) { key }
        assertEquals("/v1/did:plc:alice/feed/$key", WearPaths.feed(did, key))
        assertEquals(uri, WearFeedKey.feedUriOrNull(key))
    }

    @Test
    fun followingKey_hasNoUri() {
        assertNull(WearFeedKey.feedUriOrNull(WearFeedKey.FOLLOWING))
    }
}
