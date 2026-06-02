package net.kikin.nubecita.core.feedmapping

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class GifEmbedTest {
    @Test
    fun `klipy gif url is detected`() {
        assertTrue(isGifExternalUri("https://static.klipy.com/ii/h/28/65/X.gif?hh=498&ww=463&mp4=Y"))
    }

    @Test
    fun `tenor giphy and bare dot-gif are detected`() {
        assertTrue(isGifExternalUri("https://media.tenor.com/abc/clip.gif"))
        assertTrue(isGifExternalUri("https://media1.giphy.com/media/abc/giphy.gif"))
        assertTrue(isGifExternalUri("https://example.com/a/b.GIF?x=1"))
    }

    @Test
    fun `plain article link is not a gif`() {
        assertFalse(isGifExternalUri("https://example.com/articles/embedded-db"))
    }

    @Test
    fun `aspect ratio parsed from ww and hh`() {
        assertEquals(
            463f / 498f,
            gifAspectRatioOrNull("https://static.klipy.com/x.gif?hh=498&ww=463")!!,
            0.0001f,
        )
    }

    @Test
    fun `aspect ratio null when params absent`() {
        assertNull(gifAspectRatioOrNull("https://media.tenor.com/abc/clip.gif"))
    }
}
