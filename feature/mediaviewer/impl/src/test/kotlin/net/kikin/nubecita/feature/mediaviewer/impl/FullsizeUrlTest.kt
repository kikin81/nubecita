package net.kikin.nubecita.feature.mediaviewer.impl

import net.kikin.nubecita.data.models.ImageUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class FullsizeUrlTest {
    @Test
    fun `feed_thumbnail URL transforms to fullsize`() {
        val image =
            imageUi(
                "https://cdn.bsky.app/img/feed_thumbnail/plain/did:plc:abc/bafkreidef@feed_thumbnail",
            )
        assertEquals(
            "https://cdn.bsky.app/img/feed_thumbnail/plain/did:plc:abc/bafkreidef@fullsize",
            image.fullsizeUrl(),
        )
    }

    @Test
    fun `URL without feed_thumbnail token returns unchanged`() {
        val image = imageUi("https://example.com/some/other/host/path.jpg")
        assertEquals("https://example.com/some/other/host/path.jpg", image.fullsizeUrl())
    }

    @Test
    fun `empty URL returns empty string`() {
        val image = imageUi("")
        assertEquals("", image.fullsizeUrl())
    }

    @Test
    fun `URL with fullsize token already returns unchanged`() {
        // Defensive: if a future caller passes through a fullsize URL,
        // the helper doesn't apply a redundant transform.
        val image = imageUi("https://cdn.bsky.app/img/feed_fullsize/plain/did/cid@fullsize")
        assertEquals("https://cdn.bsky.app/img/feed_fullsize/plain/did/cid@fullsize", image.fullsizeUrl())
    }

    private fun imageUi(url: String): ImageUi = ImageUi(url = url, altText = null, aspectRatio = null)
}
