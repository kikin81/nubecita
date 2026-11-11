package net.kikin.nubecita.core.posttranslation

import io.github.kikin81.atproto.app.bsky.richtext.Facet
import io.github.kikin81.atproto.app.bsky.richtext.FacetByteSlice
import io.github.kikin81.atproto.app.bsky.richtext.FacetLink
import io.github.kikin81.atproto.app.bsky.richtext.FacetMention
import io.github.kikin81.atproto.app.bsky.richtext.FacetTag
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Uri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Cached facets must come back as the facets that were shown.
 *
 * A link that round-trips to a different target, or a mention to a different
 * account, would send a tap somewhere the author never pointed, which is the
 * one thing facet reconciliation exists to prevent.
 */
internal class FacetsCodecTest {
    @Test
    fun `every facet kind survives the cache`() {
        val facets =
            listOf(
                Facet(
                    features = listOf(FacetMention(did = Did("did:plc:alice00000000000000000"))),
                    index = FacetByteSlice(byteStart = 0, byteEnd = 6),
                ),
                Facet(
                    features = listOf(FacetLink(uri = Uri("https://example.com/a/b"))),
                    index = FacetByteSlice(byteStart = 7, byteEnd = 30),
                ),
                Facet(
                    features = listOf(FacetTag(tag = "PalacesandGardens")),
                    index = FacetByteSlice(byteStart = 31, byteEnd = 49),
                ),
            )

        assertEquals(facets, FacetsCodec.decode(FacetsCodec.encode(facets)))
    }

    @Test
    fun `no facets is an empty list, not an error`() {
        assertEquals(emptyList<Facet>(), FacetsCodec.decode(FacetsCodec.encode(emptyList())))
    }
}
