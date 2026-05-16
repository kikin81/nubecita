package net.kikin.nubecita.core.feedmapping

import io.github.kikin81.atproto.app.bsky.feed.PostView
import kotlinx.serialization.json.Json
import net.kikin.nubecita.data.models.FeedItemUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Unit tests for [PostView.toFlatFeedItemUiSingle].
 *
 * The helper is a thin wrapper over [toPostUiCore] that lifts a
 * well-formed projection into [FeedItemUi.Single] and passes a `null`
 * core result through unchanged. Tests use the same inline-JSON fixture
 * pattern as [FeedMappingTest] — no shared builder exists in the module
 * and the contract is small enough that the local helpers below stay
 * cheap.
 */
internal class FlatFeedItemUiSingleTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Test
    fun `wellFormed post returns Single wrapping PostUi`() {
        val view = decodePostView(POST_WELL_FORMED)

        val item = view.toFlatFeedItemUiSingle()

        assertNotNull(item)
        assertEquals("at://did:plc:fake/app.bsky.feed.post/p1", (item as FeedItemUi.Single).post.id)
        assertEquals("hello world", item.post.text)
    }

    @Test
    fun `malformed record returns null`() {
        // toPostUiCore returns null when the embedded record can't decode as
        // a well-formed app.bsky.feed.post. The flat helper passes the null
        // through unchanged so callers can filter at the collection boundary.
        val view = decodePostView(POST_WITH_MALFORMED_RECORD)

        val item = view.toFlatFeedItemUiSingle()

        assertNull(item)
    }

    private fun decodePostView(jsonString: String): PostView = json.decodeFromString(PostView.serializer(), jsonString)

    private companion object {
        const val POST_WELL_FORMED = """
            {
              "uri": "at://did:plc:fake/app.bsky.feed.post/p1",
              "cid": "bafyreifakecid000000000000000000000000000000000",
              "author": {
                "did": "did:plc:fake",
                "handle": "fake.bsky.social",
                "displayName": "Fake User"
              },
              "indexedAt": "2026-04-26T12:00:00Z",
              "record": {
                "${'$'}type": "app.bsky.feed.post",
                "text": "hello world",
                "createdAt": "2026-04-26T12:00:00Z"
              }
            }
        """

        const val POST_WITH_MALFORMED_RECORD = """
            {
              "uri": "at://did:plc:fake/app.bsky.feed.post/bad",
              "cid": "bafyreifakecid000000000000000000000000000000000",
              "author": {
                "did": "did:plc:fake",
                "handle": "fake.bsky.social"
              },
              "indexedAt": "2026-04-26T12:00:00Z",
              "record": {
                "${'$'}type": "app.bsky.feed.post"
              }
            }
        """
    }
}
