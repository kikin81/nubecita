package net.kikin.nubecita.core.wearcontract

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WearActionCodecTest {
    private val post = "at://did:plc:bob/app.bsky.feed.post/3k"

    private fun request(action: WearAction) = WearActionRequest(actionId = "a1", accountDid = "did:plc:alice", createdAtMs = 1_792_966_736_170, action = action)

    @Test
    fun everyActionKind_roundTrips() {
        listOf(
            WearAction.Like(post, "cid"),
            WearAction.Unlike(post, "at://did:plc:alice/app.bsky.feed.like/1"),
            WearAction.Repost(post, "cid"),
            WearAction.Unrepost(post, "at://did:plc:alice/app.bsky.feed.repost/1"),
            WearAction.Bookmark(post, "cid"),
            WearAction.Unbookmark(post, "cid"),
            WearAction.DmReply("convo1", "Running late, see you at 8"),
            WearAction.DmReact("convo1", "msg1", "👍"),
            WearAction.CreatePost("Finished my first 10k 🏃"),
        ).forEach { action ->
            val json = WearCodec.encode(WearActionRequest.serializer(), request(action))

            assertEquals(WearDecodeResult.Decoded(request(action)), WearCodec.decode(WearActionRequest.serializer(), json))
        }
    }

    @Test
    fun actionKind_isEncodedAsAStableTypeName() {
        val json = WearCodec.encode(WearActionRequest.serializer(), request(WearAction.Unbookmark(post, "cid")))

        assertTrue("\"type\":\"unbookmark\"" in json) { json }
    }

    @Test
    fun createPost_isEncodedAsCreatePost_andIsNotAPostOrDmAction() {
        val action: WearAction = WearAction.CreatePost("hello")
        val json = WearCodec.encode(WearActionRequest.serializer(), request(action))

        assertTrue("\"type\":\"create-post\"" in json) { json }
        // Neither the post handler nor the chats handler may claim it.
        assertFalse(action is WearPostAction)
        assertFalse(action is WearDmAction)
    }

    @Test
    fun unknownActionKind_fromANewerWatch_isMalformed_notGuessed() {
        val future = """{"schemaVersion":1,"actionId":"a1","accountDid":"did:plc:alice","createdAtMs":1,"action":{"type":"quote","postUri":"$post"}}"""

        assertTrue(WearCodec.decode(WearActionRequest.serializer(), future) is WearDecodeResult.Malformed)
    }

    @Test
    fun results_roundTrip_andReportOk() {
        val ok = WearActionResult(actionId = "a1", accountDid = "did:plc:alice", completedAtMs = 2, recordUri = "at://did:plc:alice/app.bsky.feed.like/1")
        val failed = WearActionResult(actionId = "a2", accountDid = "did:plc:alice", completedAtMs = 2, failure = WearActionFailure.ACCOUNT)

        listOf(ok, failed).forEach { result ->
            val json = WearCodec.encode(WearActionResult.serializer(), result)
            assertEquals(WearDecodeResult.Decoded(result), WearCodec.decode(WearActionResult.serializer(), json))
        }
        assertTrue(ok.isOk)
        assertFalse(failed.isOk)
        assertTrue("\"failure\":\"account\"" in WearCodec.encode(WearActionResult.serializer(), failed))
    }
}
