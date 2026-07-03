package net.kikin.nubecita.core.feedmapping

import net.kikin.nubecita.data.models.KlipyMediaUiFixtures
import net.kikin.nubecita.data.models.toExternalEmbedUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Produce↔consume contract: the `app.bsky.embed.external` URI the composer
 * builds from a picked KLIPY item ([toExternalEmbedUri]) must be recognized by
 * this module's render-side detector ([isGifExternalUri]) and its dimensions
 * parsed back by [gifAspectRatioOrNull]. Both sides live behind the same fixture
 * here, so a change to either that breaks inline GIF rendering fails this test.
 */
class KlipyEmbedContractTest {
    @Test
    fun `a posted KLIPY gif uri is recognized as an inline GIF by the render side`() {
        val uri = KlipyMediaUiFixtures.media().toExternalEmbedUri()

        assertTrue(isGifExternalUri(uri), "render side must accept the posted KLIPY uri: $uri")
    }

    @Test
    fun `the embed uri round-trips ww and hh to the render aspect ratio`() {
        val uri = KlipyMediaUiFixtures.media(embedWidth = 480, embedHeight = 360).toExternalEmbedUri()

        assertEquals(480f / 360f, gifAspectRatioOrNull(uri))
    }
}
