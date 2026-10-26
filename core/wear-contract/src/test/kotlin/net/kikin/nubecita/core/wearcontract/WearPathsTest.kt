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

    @Test
    fun parseFeed_roundTripsAFeedPath_andRejectsOthers() {
        val key = WearFeedKey.forFeedUri("at://did:plc:x/app.bsky.feed.generator/cats")

        assertEquals(did to key, WearPaths.parseFeed(WearPaths.feed(did, key)))
        assertNull(WearPaths.parseFeed(WearPaths.SESSION))
        assertNull(WearPaths.parseFeed(WearPaths.dms(did)))
        assertNull(WearPaths.parseFeed(WearPaths.action(did, "a1")))
        assertNull(WearPaths.parseFeed("/elsewhere/feed/x"))
    }

    @Test
    fun parseAction_roundTripsAnActionPath_andRejectsOthers() {
        assertEquals(did to "a1", WearPaths.parseAction(WearPaths.action(did, "a1")))
        assertNull(WearPaths.parseAction(WearPaths.actionResult(did, "a1")))
        assertNull(WearPaths.parseAction(WearPaths.feed(did, WearFeedKey.FOLLOWING)))
        assertNull(WearPaths.parseAction(WearPaths.SESSION))
        assertNull(WearPaths.parseAction("/v1/$did/action/"))
    }

    @Test
    fun parseDms_roundTripsTheDmsPath_andRejectsOthers() {
        assertEquals(did, WearPaths.parseDms(WearPaths.dms(did)))
        assertNull(WearPaths.parseDms(WearPaths.feed(did, WearFeedKey.FOLLOWING)))
        assertNull(WearPaths.parseDms(WearPaths.SESSION))
        assertNull(WearPaths.parseDms("/v1//dms"))
    }

    @Test
    fun parseActionResult_roundTripsAResultPath_andRejectsActions() {
        assertEquals(did to "a1", WearPaths.parseActionResult(WearPaths.actionResult(did, "a1")))
        assertNull(WearPaths.parseActionResult(WearPaths.action(did, "a1")))
        assertNull(WearPaths.parseActionResult(WearPaths.SESSION))
    }
}
