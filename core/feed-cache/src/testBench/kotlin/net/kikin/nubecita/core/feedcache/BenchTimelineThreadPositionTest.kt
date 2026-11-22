package net.kikin.nubecita.core.feedcache

import kotlinx.serialization.json.Json
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.ThreadPositionUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The bench timeline has no network and no server numbering, so a fixture post
 * states its own place in a thread. This is what puts the thread pill on screen
 * in the offline build.
 */
internal class BenchTimelineThreadPositionTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun single(extraPostFields: String): FeedItemUi.Single {
        val dto =
            json.decodeFromString(
                BenchTimelineDto.serializer(),
                """
                { "items": [ { "type": "Single", "post": {
                  "id": "at://did:plc:carmen0000000000000000000/app.bsky.feed.post/benchthread2",
                  "cid": "bafyreibenchthread2",
                  "author": { "did": "did:plc:carmen0000000000000000000", "handle": "carmen.codes", "displayName": "Carmen Ortiz" },
                  "createdAt": "2026-05-26T09:00:00Z",
                  "text": "second post"
                  $extraPostFields
                } } ] }
                """.trimIndent(),
            )
        return assertInstanceOf(FeedItemUi.Single::class.java, BenchTimelineMapper.toFeedItems(dto).single())
    }

    @Test
    fun `a fixture post that states its thread place gets a thread position`() {
        assertEquals(
            ThreadPositionUi(index = 2, count = 5),
            single(""", "threadIndex": 2, "threadCount": 5""").post.threadPosition,
        )
    }

    @Test
    fun `a fixture post without a thread place has none`() {
        assertNull(single("").post.threadPosition)
    }
}
