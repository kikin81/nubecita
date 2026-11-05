package net.kikin.nubecita.core.translation

import io.github.kikin81.atproto.app.bsky.richtext.Facet
import io.github.kikin81.atproto.app.bsky.richtext.FacetByteSlice
import io.github.kikin81.atproto.app.bsky.richtext.FacetLink
import io.github.kikin81.atproto.app.bsky.richtext.FacetMention
import io.github.kikin81.atproto.app.bsky.richtext.FacetTag
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Uri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The contract: a tap in translated text goes where the author pointed, or
 * nowhere.
 *
 * Several of these pin *real* ML Kit output captured on device, so a change to
 * the matching patterns that breaks a case we actually observed fails here
 * rather than in someone's timeline.
 */
internal class FacetRestorationTest {
    // ---- the no-facet fast path ----------------------------------------

    @Test
    fun `a post with no facets keeps the translation and gains nothing`() {
        val result = restoreFacets("おはよう", "Good morning", emptyList())

        assertEquals("Good morning", result.text)
        assertTrue(result.facets.isEmpty())
    }

    // ---- the case that motivated the whole design -----------------------

    @Test
    fun `a mangled hashtag is restored to what the author wrote`() {
        // Captured from ML Kit on device: the tag TEXT changed, not just case.
        val original = "Guten Morgen #PalacesandGardens"
        val translated = "Good morning #palaceandGarden"

        val result = restoreFacets(original, translated, listOf(tagFacet(original, "#PalacesandGardens", "PalacesandGardens")))

        assertEquals("Good morning #PalacesandGardens", result.text)
        assertEquals(1, result.facets.size)
        assertEquals("PalacesandGardens", (result.facets[0].features[0] as FacetTag).tag)
    }

    @Test
    fun `the restored facet indexes the translated bytes, not the original`() {
        val original = "Guten Morgen #PalacesandGardens"
        val translated = "Good morning #palaceandGarden"

        val result = restoreFacets(original, translated, listOf(tagFacet(original, "#PalacesandGardens", "PalacesandGardens")))

        val slice = result.facets[0].index
        val bytes = result.text.toByteArray(Charsets.UTF_8)
        val spanned = bytes.copyOfRange(slice.byteStart.toInt(), slice.byteEnd.toInt()).toString(Charsets.UTF_8)
        assertEquals("#PalacesandGardens", spanned)
    }

    @Test
    fun `all nine hashtags of the real post survive`() {
        // The exact pair captured on device, German -> English.
        val original =
            "Guten Morgen, happy day 🌞\n" +
                "#ColorADay #YellowThu #PalacesandGardens #Autumn 🍂💛 " +
                "#woodland #leaves #scape #nature #photography"
        val translated =
            "Good Morning, Happy Day 🌞 #coloraday #Yellowthu #palaceandGarden " +
                "#Autumn 🍂💛 #woodland #Leaves #scape #Nature #Photography"
        val tags =
            listOf(
                "ColorADay",
                "YellowThu",
                "PalacesandGardens",
                "Autumn",
                "woodland",
                "leaves",
                "scape",
                "nature",
                "photography",
            )

        val result = restoreFacets(original, translated, tags.map { tagFacet(original, "#$it", it) })

        assertEquals(9, result.facets.size)
        tags.forEach { tag -> assertTrue(result.text.contains("#$tag"), "lost #$tag from: ${result.text}") }
        // And the prose really was translated.
        assertTrue(result.text.startsWith("Good Morning, Happy Day"))
    }

    @Test
    fun `emoji are untouched`() {
        val original = "Guten Morgen 🌞 #Autumn"
        val translated = "Good morning 🌞 #autumn"

        val result = restoreFacets(original, translated, listOf(tagFacet(original, "#Autumn", "Autumn")))

        assertTrue(result.text.contains("🌞"))
    }

    // ---- the artifacts translation introduces ---------------------------

    @Test
    fun `a mention that gained a space is still matched`() {
        // ML Kit inserts a space after @ in both German and Japanese.
        val original = "Hallo @alice.bsky.social"
        val translated = "Hello @ alice.bsky.social"

        val result = restoreFacets(original, translated, listOf(mentionFacet(original, "@alice.bsky.social")))

        assertEquals("Hello @alice.bsky.social", result.text)
        assertEquals(1, result.facets.size)
    }

    @Test
    fun `a URL broken by Japanese is still matched`() {
        val original = "リンク https://example.com/a/b"
        val translated = "Link https: //example.com/a/b"

        val result = restoreFacets(original, translated, listOf(linkFacet(original, "https://example.com/a/b")))

        assertEquals("Link https://example.com/a/b", result.text)
        assertEquals(1, result.facets.size)
    }

    @Test
    fun `a hashtag translated into words keeps the authored tag as the tap target`() {
        // `#おはよう` comes back as `# Good morning`. Only `# Good` is consumed,
        // so a stray word is left behind — deliberately preferred to swallowing
        // sentence text into a hashtag. What matters is where the tap goes.
        val original = "#おはよう"
        val translated = "# Good morning"

        val result = restoreFacets(original, translated, listOf(tagFacet(original, "#おはよう", "おはよう")))

        assertTrue(result.text.startsWith("#おはよう"), "got: ${result.text}")
        assertEquals("おはよう", (result.facets[0].features[0] as FacetTag).tag)
    }

    // ---- the count gate -------------------------------------------------

    @Test
    fun `a kind the engine dropped renders plain rather than mislinking`() {
        val original = "see #alpha and #beta"
        val translated = "see #alpha"

        val result =
            restoreFacets(
                original,
                translated,
                listOf(tagFacet(original, "#alpha", "alpha"), tagFacet(original, "#beta", "beta")),
            )

        // Two originals, one candidate: we cannot say which is which.
        assertEquals("see #alpha", result.text)
        assertTrue(result.facets.isEmpty())
    }

    @Test
    fun `a kind the engine invented renders plain rather than mislinking`() {
        val original = "see #alpha"
        val translated = "see #alpha and #beta"

        val result = restoreFacets(original, translated, listOf(tagFacet(original, "#alpha", "alpha")))

        assertTrue(result.facets.isEmpty())
    }

    @Test
    fun `one kind failing does not take the others down with it`() {
        // The mention survives even though the tags cannot be reconciled —
        // kinds are gated independently.
        val original = "Hallo @alice.bsky.social #alpha #beta"
        val translated = "Hello @ alice.bsky.social #alpha"

        val result =
            restoreFacets(
                original,
                translated,
                listOf(
                    mentionFacet(original, "@alice.bsky.social"),
                    tagFacet(original, "#alpha", "alpha"),
                    tagFacet(original, "#beta", "beta"),
                ),
            )

        assertEquals(1, result.facets.size)
        assertTrue(result.text.contains("@alice.bsky.social"))
    }

    @Test
    fun `order is preserved when several of one kind match`() {
        val original = "#alpha then #beta"
        val translated = "#Alpha then #Beta"

        val result =
            restoreFacets(
                original,
                translated,
                listOf(tagFacet(original, "#alpha", "alpha"), tagFacet(original, "#beta", "beta")),
            )

        assertEquals("#alpha then #beta", result.text)
        assertEquals(listOf("alpha", "beta"), result.facets.map { (it.features[0] as FacetTag).tag })
    }

    // ---- defensive cases --------------------------------------------------

    @Test
    fun `a facet with out-of-bounds offsets is dropped`() {
        val facet = Facet(features = listOf(FacetTag("alpha")), index = FacetByteSlice(byteStart = 0, byteEnd = 9_999))

        val result = restoreFacets("#alpha", "#alpha", listOf(facet))

        assertTrue(result.facets.isEmpty())
    }

    @Test
    fun `a facet whose offsets split a character is dropped`() {
        // Half of a 3-byte CJK character. It was already rendering wrong
        // before translation; inventing a span would mark text the author
        // never marked.
        val original = "おはよう"
        val facet = Facet(features = listOf(FacetTag("x")), index = FacetByteSlice(byteStart = 0, byteEnd = 2))

        val result = restoreFacets(original, "Good morning", listOf(facet))

        assertTrue(result.facets.isEmpty())
    }

    @Test
    fun `punctuation after a mention is not swallowed`() {
        val original = "Hallo @alice.bsky.social, schau"
        val translated = "Hello @ alice.bsky.social, look"

        val result = restoreFacets(original, translated, listOf(mentionFacet(original, "@alice.bsky.social")))

        assertEquals("Hello @alice.bsky.social, look", result.text)
    }

    @Test
    fun `a post with facets but an unchanged body round-trips exactly`() {
        val original = "see #alpha"

        val result = restoreFacets(original, original, listOf(tagFacet(original, "#alpha", "alpha")))

        assertEquals(original, result.text)
        assertEquals(1, result.facets.size)
    }

    // ---- fixtures ---------------------------------------------------------

    private fun tagFacet(
        text: String,
        span: String,
        tag: String,
    ): Facet = Facet(features = listOf(FacetTag(tag)), index = sliceOf(text, span))

    private fun mentionFacet(
        text: String,
        span: String,
    ): Facet =
        Facet(
            features = listOf(FacetMention(did = Did("did:plc:alice00000000000000000"))),
            index = sliceOf(text, span),
        )

    private fun linkFacet(
        text: String,
        span: String,
    ): Facet =
        Facet(
            features = listOf(FacetLink(uri = Uri("https://example.com/a/b"))),
            index = sliceOf(text, span),
        )

    /** Byte slice of [span] inside [text], the way the wire expresses it. */
    private fun sliceOf(
        text: String,
        span: String,
    ): FacetByteSlice {
        val charIndex = text.indexOf(span)
        require(charIndex >= 0) { "fixture span '$span' not present in '$text'" }
        val start =
            text
                .substring(0, charIndex)
                .toByteArray(Charsets.UTF_8)
                .size
                .toLong()
        val end = start + span.toByteArray(Charsets.UTF_8).size
        return FacetByteSlice(byteStart = start, byteEnd = end)
    }
}
