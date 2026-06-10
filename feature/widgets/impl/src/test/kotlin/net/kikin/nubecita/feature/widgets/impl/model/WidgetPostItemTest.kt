package net.kikin.nubecita.feature.widgets.impl.model

import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.TimeZone
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

internal class WidgetPostItemTest {
    @Test
    fun `maps author display, handle, uri and relative time`() {
        val item = post(createdAt = NOW - 2.hours).toWidgetItem(NOW, timeZone = TimeZone.UTC)

        assertEquals("at://post/1", item.postUri)
        assertEquals("Alice", item.authorDisplay)
        assertEquals("alice.bsky.social", item.handle)
        assertEquals("2h", item.relativeTime)
    }

    @Test
    fun `author display falls back to handle when display name is blank`() {
        val item = post(displayName = "   ").toWidgetItem(NOW, timeZone = TimeZone.UTC)

        assertEquals("alice.bsky.social", item.authorDisplay)
    }

    @Test
    fun `text is whitespace-collapsed and length-capped`() {
        val long = "word ".repeat(80) // 400 chars, multi-space
        val item = post(text = "a\n\n  b\t c").toWidgetItem(NOW, timeZone = TimeZone.UTC)
        val capped = post(text = long).toWidgetItem(NOW, timeZone = TimeZone.UTC)

        assertEquals("a b c", item.text)
        assertTrue(capped.text.length <= 200)
        assertTrue(capped.text.endsWith("…"))

        // The cap must not slice a surrogate pair (🌟 spans indices 198-199): drop
        // the whole emoji rather than leave a dangling high surrogate.
        val withEmoji = "a".repeat(198) + "🌟" + "b"
        val truncated = post(text = withEmoji).toWidgetItem(NOW, timeZone = TimeZone.UTC)
        assertEquals("a".repeat(198) + "…", truncated.text)
    }

    @Test
    fun `gallery post exposes media, overflow count, and description`() {
        val item =
            post(
                embed =
                    EmbedUi.Images(
                        items =
                            persistentListOf(
                                image("t1"),
                                image("t2"),
                                image("t3"),
                                image("t4"),
                            ),
                    ),
            ).toWidgetItem(NOW, timeZone = TimeZone.UTC)

        assertTrue(item.hasMedia)
        assertEquals(3, item.extraImageCount) // 4 images → "+3"
        assertEquals("Image, 4 total", item.mediaContentDescription)
    }

    @Test
    fun `single image has media but no overflow badge`() {
        val item = post(embed = EmbedUi.Images(items = persistentListOf(image("t1")))).toWidgetItem(NOW, timeZone = TimeZone.UTC)

        assertTrue(item.hasMedia)
        assertEquals(0, item.extraImageCount)
        assertEquals("Image", item.mediaContentDescription)
    }

    @Test
    fun `text-only post has no media`() {
        val item = post(embed = EmbedUi.Empty).toWidgetItem(NOW, timeZone = TimeZone.UTC)

        assertFalse(item.hasMedia)
        assertEquals(0, item.extraImageCount)
        assertNull(item.mediaContentDescription)
    }

    private companion object {
        val NOW = Instant.parse("2026-06-10T12:00:00Z")

        fun image(thumb: String): ImageUi = ImageUi(fullsizeUrl = "full", thumbUrl = thumb, altText = null, aspectRatio = null)

        fun post(
            text: String = "hello",
            displayName: String = "Alice",
            createdAt: Instant = NOW,
            embed: EmbedUi = EmbedUi.Empty,
        ): PostUi =
            PostUi(
                id = "at://post/1",
                cid = "cid",
                author = AuthorUi(did = "did:author", handle = "alice.bsky.social", displayName = displayName, avatarUrl = null),
                createdAt = createdAt,
                text = text,
                facets = persistentListOf(),
                embed = embed,
                stats = PostStatsUi(),
                viewer = ViewerStateUi(),
                repostedBy = null,
            )
    }
}
