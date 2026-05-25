package net.kikin.nubecita.core.push

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class PushPayloadParseTest {
    @Test
    fun `parses a like payload with all fields populated`() {
        val data =
            mapOf(
                "reason" to "like",
                "uri" to "at://did:plc:alice/app.bsky.feed.like/3kabc",
                "subject" to "at://did:plc:bob/app.bsky.feed.post/3kxyz",
                "actorDid" to "did:plc:alice",
                "actorHandle" to "alice.bsky.social",
                "actorDisplayName" to "Alice",
                "recipientDid" to "did:plc:bob",
            )

        val payload = PushPayload.parse(data)

        assertEquals(
            PushPayload(
                reason = PushPayload.Reason.Like,
                uri = "at://did:plc:alice/app.bsky.feed.like/3kabc",
                subject = "at://did:plc:bob/app.bsky.feed.post/3kxyz",
                actorDid = "did:plc:alice",
                actorHandle = "alice.bsky.social",
                actorDisplayName = "Alice",
                recipientDid = "did:plc:bob",
            ),
            payload,
        )
    }

    @ParameterizedTest(name = "maps wire string \"{0}\" to {1}")
    @MethodSource("reasonWireStrings")
    fun `each reason wire string parses to the matching Reason variant`(
        wire: String,
        expected: PushPayload.Reason,
    ) {
        val data =
            baseRequiredFields() +
                mapOf("reason" to wire)

        val payload = PushPayload.parse(data)

        assertEquals(expected, payload?.reason)
    }

    @Test
    fun `drops payload when reason key is missing`() {
        val data = baseRequiredFields() // no "reason"

        assertNull(PushPayload.parse(data))
    }

    @Test
    fun `drops payload when uri key is missing`() {
        val data =
            mapOf(
                "reason" to "like",
                "actorDid" to "did:plc:alice",
                "recipientDid" to "did:plc:bob",
            )

        assertNull(PushPayload.parse(data))
    }

    @Test
    fun `drops payload when actorDid key is missing`() {
        val data =
            mapOf(
                "reason" to "like",
                "uri" to "at://did:plc:alice/app.bsky.feed.like/3kabc",
                "recipientDid" to "did:plc:bob",
            )

        assertNull(PushPayload.parse(data))
    }

    @Test
    fun `drops payload when recipientDid key is missing`() {
        val data =
            mapOf(
                "reason" to "like",
                "uri" to "at://did:plc:alice/app.bsky.feed.like/3kabc",
                "actorDid" to "did:plc:alice",
            )

        assertNull(PushPayload.parse(data))
    }

    @Test
    fun `drops payload when reason is an unknown wire string`() {
        val data =
            baseRequiredFields() +
                mapOf("reason" to "some-future-reason")

        assertNull(PushPayload.parse(data))
    }

    companion object {
        @JvmStatic
        fun reasonWireStrings(): List<Arguments> =
            listOf(
                Arguments.of("like", PushPayload.Reason.Like),
                Arguments.of("like-via-repost", PushPayload.Reason.LikeViaRepost),
                Arguments.of("repost", PushPayload.Reason.Repost),
                Arguments.of("repost-via-repost", PushPayload.Reason.RepostViaRepost),
                Arguments.of("reply", PushPayload.Reason.Reply),
                Arguments.of("mention", PushPayload.Reason.Mention),
                Arguments.of("quote", PushPayload.Reason.Quote),
                Arguments.of("follow", PushPayload.Reason.Follow),
                Arguments.of("verified", PushPayload.Reason.Verified),
                Arguments.of("unverified", PushPayload.Reason.Unverified),
            )

        private fun baseRequiredFields(): Map<String, String> =
            mapOf(
                "uri" to "at://did:plc:alice/app.bsky.feed.like/3kabc",
                "actorDid" to "did:plc:alice",
                "recipientDid" to "did:plc:bob",
            )
    }
}
