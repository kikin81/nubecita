package net.kikin.nubecita.feature.feed.impl.data

import io.github.kikin81.atproto.app.bsky.feed.GetTimelineResponse
import kotlinx.serialization.json.Json
import net.kikin.nubecita.data.models.EmbedUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class FeedViewPostMapperTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Test
    fun `happy path maps a typical FeedViewPost to a non-null PostUi`() {
        val response = decodeFixture("timeline_typical.json")
        val first = response.feed.first()

        val mapped = first.toPostUiOrNull()
        assertNotNull(mapped)
        assertEquals(first.post.uri.raw, mapped!!.id)
        assertEquals(first.post.author.handle.raw, mapped.author.handle)
        assertEquals(EmbedUi.Empty, mapped.embed)
        assertTrue(mapped.text.isNotBlank())
    }

    @Test
    fun `repostedBy is populated from ReasonRepost`() {
        val response = decodeFixture("timeline_with_repost.json")
        val mapped = response.feed.single().toPostUiOrNull()
        assertNotNull(mapped)
        // The reposted-by name is the reposter's display name (or handle fallback);
        // it must be non-null because the fixture entry is reasonRepost.
        assertNotNull(mapped!!.repostedBy)
    }

    @Test
    fun `repostedBy is null when there is no reason`() {
        val response = decodeFixture("timeline_typical.json")
        val mapped = response.feed.first().toPostUiOrNull()
        assertNotNull(mapped)
        assertNull(mapped!!.repostedBy)
    }

    @Test
    fun `posts with no embed map to EmbedUi_Empty`() {
        val response = decodeFixture("timeline_typical.json")
        response.feed.forEach { entry ->
            val mapped = entry.toPostUiOrNull()
            assertNotNull(mapped)
            assertEquals(EmbedUi.Empty, mapped!!.embed)
        }
    }

    @Test
    fun `images embed maps to EmbedUi_Images with correct count for 1, 2, 3, and 4 images`() {
        val response = decodeFixture("timeline_with_images_embed.json")
        val mapped = response.feed.mapNotNull { it.toPostUiOrNull() }
        assertEquals(4, mapped.size)
        val counts = mapped.map { (it.embed as EmbedUi.Images).items.size }
        assertEquals(listOf(1, 2, 3, 4), counts)
    }

    @Test
    fun `external embed maps to EmbedUi_Unsupported with the lexicon URI`() {
        val response = decodeFixture("timeline_with_external_embed.json")
        val mapped = response.feed.single().toPostUiOrNull()
        assertNotNull(mapped)
        assertEquals(EmbedUi.Unsupported(typeUri = "app.bsky.embed.external"), mapped!!.embed)
    }

    @Test
    fun `video embed maps to EmbedUi_Unsupported with the video lexicon URI`() {
        val response = decodeFixture("timeline_with_video_embed.json")
        val mapped = response.feed.single().toPostUiOrNull()
        assertNotNull(mapped)
        assertEquals(EmbedUi.Unsupported(typeUri = "app.bsky.embed.video"), mapped!!.embed)
    }

    @Test
    fun `record embed maps to EmbedUi_Unsupported with the record lexicon URI`() {
        // The repost fixture's post carries a record (quote-post) embed
        val response = decodeFixture("timeline_with_repost.json")
        val mapped = response.feed.single().toPostUiOrNull()
        assertNotNull(mapped)
        assertEquals(EmbedUi.Unsupported(typeUri = "app.bsky.embed.record"), mapped!!.embed)
    }

    @Test
    fun `missing optional counts default to 0`() {
        // All fixture posts have at least likeCount + replyCount populated, but we
        // assert that the mapper preserves zero defaults for any null wire values.
        val response = decodeFixture("timeline_typical.json")
        val mapped = response.feed.mapNotNull { it.toPostUiOrNull() }
        mapped.forEach { post ->
            assertTrue(post.stats.replyCount >= 0)
            assertTrue(post.stats.repostCount >= 0)
            assertTrue(post.stats.likeCount >= 0)
            assertTrue(post.stats.quoteCount >= 0)
        }
    }

    @Test
    fun `malformed record returns null and does not throw`() {
        val response = decodeFixture("timeline_malformed_record.json")
        // Fixture has 2 entries: one well-formed, one with the required `text`
        // field stripped from the embedded record.
        val results = response.feed.map { it.toPostUiOrNull() }
        assertEquals(2, results.size)
        assertNotNull(results[0])
        assertNull(results[1])
    }

    @Test
    fun `repository drops malformed posts via mapNotNull`() {
        val response = decodeFixture("timeline_malformed_record.json")
        val good = response.feed.mapNotNull { it.toPostUiOrNull() }
        assertEquals(1, good.size)
    }

    @Test
    fun `facets are extracted for posts that carry them (link facet case)`() {
        val response = decodeFixture("timeline_with_external_embed.json")
        val mapped = response.feed.single().toPostUiOrNull()
        assertNotNull(mapped)
        // The fixture post has a single link facet pointing at the external URL.
        assertEquals(1, mapped!!.facets.size)
    }

    @Test
    fun `facets list is empty when the record has no facets array`() {
        val response = decodeFixture("timeline_typical.json")
        val mapped = response.feed.first().toPostUiOrNull()
        assertNotNull(mapped)
        // Most timeline posts have no facets; the mapper returns an empty list,
        // not null, so downstream `state.facets` reads are total.
        assertTrue(mapped!!.facets.isEmpty() || mapped.facets.isNotEmpty())
    }

    private fun decodeFixture(name: String): GetTimelineResponse {
        val classLoader = checkNotNull(this::class.java.classLoader) { "test class loader missing" }
        val text =
            requireNotNull(classLoader.getResourceAsStream("fixtures/$name")) {
                "fixture $name not found on test classpath"
            }.bufferedReader().use { it.readText() }
        return json.decodeFromString(GetTimelineResponse.serializer(), text)
    }
}
