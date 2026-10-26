package net.kikin.nubecita.core.wearcontract

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WearCodecTest {
    private val signedIn =
        WearSessionSnapshot(
            publishedAtMs = 1_792_966_736_170,
            account = WearAccount(did = "did:plc:alice", handle = "alice.bsky.social", displayName = "Alice", avatarAssetKey = "avatar"),
        )

    @Test
    fun session_roundTrips() {
        val json = WearCodec.encode(WearSessionSnapshot.serializer(), signedIn)

        assertEquals(WearDecodeResult.Decoded(signedIn), WearCodec.decode(WearSessionSnapshot.serializer(), json))
    }

    @Test
    fun signedOutSession_roundTrips() {
        val signedOut = WearSessionSnapshot(publishedAtMs = 1L)
        val json = WearCodec.encode(WearSessionSnapshot.serializer(), signedOut)

        assertEquals(WearDecodeResult.Decoded(signedOut), WearCodec.decode(WearSessionSnapshot.serializer(), json))
    }

    @Test
    fun encode_stampsTheCurrentSchemaVersion() {
        val json = WearCodec.encode(WearSessionSnapshot.serializer(), signedIn)

        assertTrue("\"schemaVersion\":${WearSchema.CURRENT}" in json) { json }
    }

    @Test
    fun newerMajorVersion_isUnsupported_evenWhenItsShapeNoLongerParses() {
        // A future v2 that renamed `account` and changed `publishedAtMs` to a string.
        val future = """{"schemaVersion":2,"publishedAtMs":"yesterday","owner":{"id":"did:plc:alice"}}"""

        assertEquals(WearDecodeResult.UnsupportedVersion(found = 2), WearCodec.decode(WearSessionSnapshot.serializer(), future))
    }

    @Test
    fun unknownFieldsFromAnAdditiveChange_areIgnored() {
        val withExtra = """{"schemaVersion":1,"publishedAtMs":5,"account":null,"addedLater":true}"""

        assertEquals(
            WearDecodeResult.Decoded(WearSessionSnapshot(publishedAtMs = 5)),
            WearCodec.decode(WearSessionSnapshot.serializer(), withExtra),
        )
    }

    @Test
    fun missingVersion_isMalformed() {
        val result = WearCodec.decode(WearSessionSnapshot.serializer(), """{"publishedAtMs":5}""")

        assertTrue(result is WearDecodeResult.Malformed) { "$result" }
    }

    @Test
    fun notJson_isMalformed() {
        val result = WearCodec.decode(WearSessionSnapshot.serializer(), "not json")

        assertTrue(result is WearDecodeResult.Malformed) { "$result" }
    }
}
