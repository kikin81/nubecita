package net.kikin.nubecita.core.feeds

import io.github.kikin81.atproto.app.bsky.actor.GetPreferencesResponse
import io.github.kikin81.atproto.app.bsky.actor.GetPreferencesResponsePreferencesUnion
import io.github.kikin81.atproto.app.bsky.actor.SavedFeed
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies the pure typed `List<preferences union>` → ordered `List<SavedFeed>`
 * selection step in isolation, before any network orchestration. The
 * `app.bsky.actor.getPreferences` response is an array of `$type`-tagged
 * preference objects (decoded here through the SDK serializer); we locate the
 * single [SavedFeedsPrefV2] entry and read its `items`, ignoring every other
 * preference kind.
 */
internal class SavedFeedsDecodingTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(raw: String): List<GetPreferencesResponsePreferencesUnion> = json.decodeFromString(GetPreferencesResponse.serializer(), raw).preferences

    @Test
    fun `extracts savedFeedsPrefV2 items in stored order`() {
        val prefs =
            decode(
                """
                {
                  "preferences": [
                    { "${'$'}type": "app.bsky.actor.defs#contentLabelPref", "label": "nsfw", "visibility": "hide" },
                    {
                      "${'$'}type": "app.bsky.actor.defs#savedFeedsPrefV2",
                      "items": [
                        { "id": "1", "type": "timeline", "value": "following", "pinned": true },
                        { "id": "2", "type": "feed", "value": "at://a", "pinned": true },
                        { "id": "3", "type": "feed", "value": "at://b", "pinned": false },
                        { "id": "4", "type": "list", "value": "at://l", "pinned": true }
                      ]
                    }
                  ]
                }
                """.trimIndent(),
            )

        val items = extractSavedFeedItems(prefs)!!

        assertEquals(listOf("1", "2", "3", "4"), items.map(SavedFeed::id))
    }

    @Test
    fun `returns null when no savedFeedsPrefV2 entry is present`() {
        val prefs =
            decode(
                """
                {
                  "preferences": [
                    { "${'$'}type": "app.bsky.actor.defs#contentLabelPref", "label": "nsfw", "visibility": "hide" }
                  ]
                }
                """.trimIndent(),
            )

        assertNull(extractSavedFeedItems(prefs))
    }

    @Test
    fun `returns empty list when savedFeedsPrefV2 has no items`() {
        val prefs =
            decode(
                """
                { "preferences": [ { "${'$'}type": "app.bsky.actor.defs#savedFeedsPrefV2", "items": [] } ] }
                """.trimIndent(),
            )

        assertTrue(extractSavedFeedItems(prefs)!!.isEmpty())
    }
}
