package net.kikin.nubecita.core.posting.internal

import io.github.kikin81.atproto.app.bsky.richtext.FacetLink
import io.github.kikin81.atproto.app.bsky.richtext.FacetMention
import io.github.kikin81.atproto.runtime.Did
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DefaultFacetExtractor]. Exercises the seven scenarios
 * from `nubecita-wtq.11`'s description:
 *
 *  1. Plain text with no mentions/URLs → empty result.
 *  2. Single `@handle` that resolves → one mention facet with the
 *     correct DID and UTF-8 byte offsets.
 *  3. Single `@handle` whose resolution fails → no facet (docs-mandated
 *     "skip silently" — the handle renders as plain text on bsky).
 *  4. Single URL → one link facet with the correct byte offsets.
 *  5. Mixed mentions + URLs in one post → multiple facets, sorted in
 *     document order on the wire.
 *  6. Text containing emoji before / between / after a mention → byte
 *     offsets accurately reflect UTF-8 encoding (4 bytes per emoji
 *     codepoint above the BMP).
 *  7. Text with `@`-symbol that isn't a valid handle (e.g. an email
 *     address whose @ is preceded by a word char) → no false-positive
 *     facet.
 *
 * Uses a `Map<String, Did?>`-backed fake resolver so the test stays
 * pure JVM — no Ktor, no XRPC. The resolver's behavior is asserted by
 * verifying which mention strings produced facets.
 */
class DefaultFacetExtractorTest {
    private val testDids =
        mapOf(
            "alice.bsky.social" to Did("did:plc:alice"),
            "bob.bsky.social" to Did("did:plc:bob"),
            "carol.dev" to Did("did:plc:carol"),
        )

    private val resolver: HandleResolver =
        object : HandleResolver {
            override suspend fun resolve(handle: String): Did? = testDids[handle]
        }

    private val extractor = DefaultFacetExtractor(resolver)

    @Test
    fun plainText_noMentionsNoUrls_returnsEmpty() =
        runTest {
            val result = extractor.extract("hello world, no mentions or links here")
            assertTrue(result.isEmpty(), "expected empty facets, got $result")
        }

    @Test
    fun singleMention_resolved_emitsOneMentionFacetWithCorrectDid() =
        runTest {
            val text = "hello @alice.bsky.social"
            val result = extractor.extract(text)

            assertEquals(1, result.size)
            val facet = result[0]
            assertEquals(1, facet.features.size)
            val mention = facet.features[0] as FacetMention
            assertEquals(Did("did:plc:alice"), mention.did)

            // "@alice.bsky.social" is ASCII, so byte offsets equal char
            // offsets. Token starts at index 6, ends at index 24
            // (exclusive). Total text length = 24 chars / bytes.
            assertEquals(6L, facet.index.byteStart)
            assertEquals(24L, facet.index.byteEnd)
            // Sanity-check the literal slice.
            assertEquals(
                "@alice.bsky.social",
                text.substring(facet.index.byteStart.toInt(), facet.index.byteEnd.toInt()),
            )
        }

    @Test
    fun singleMention_resolutionFails_dropsFacet() =
        runTest {
            // ghost.bsky.social isn't in the resolver map → null →
            // facet silently omitted per the AT Protocol docs.
            val result = extractor.extract("hi @ghost.bsky.social are you there")
            assertTrue(result.isEmpty(), "expected empty facets, got $result")
        }

    @Test
    fun singleLink_emitsOneLinkFacet() =
        runTest {
            val text = "see https://example.com for details"
            val result = extractor.extract(text)

            assertEquals(1, result.size)
            val facet = result[0]
            val link = facet.features[0] as FacetLink
            assertEquals("https://example.com", link.uri.raw)

            // "https://example.com" starts at index 4, ends at 23.
            assertEquals(4L, facet.index.byteStart)
            assertEquals(23L, facet.index.byteEnd)
            assertEquals(
                "https://example.com",
                text.substring(facet.index.byteStart.toInt(), facet.index.byteEnd.toInt()),
            )
        }

    @Test
    fun mixedMentionsAndUrls_emitsMultipleFacetsInDocumentOrder() =
        runTest {
            val text = "hey @alice.bsky.social check https://example.com or ping @bob.bsky.social"
            val result = extractor.extract(text)

            assertEquals(3, result.size, "expected mention + link + mention; got $result")

            // Document order: alice (idx 4), https://example.com (idx 29), bob (idx 57)
            val first = result[0].features[0] as FacetMention
            assertEquals(Did("did:plc:alice"), first.did)
            assertEquals(4L, result[0].index.byteStart)

            val second = result[1].features[0] as FacetLink
            assertEquals("https://example.com", second.uri.raw)
            assertEquals(29L, result[1].index.byteStart)

            val third = result[2].features[0] as FacetMention
            assertEquals(Did("did:plc:bob"), third.did)
            assertEquals(57L, result[2].index.byteStart)
        }

    @Test
    fun mentionAfterEmoji_byteOffsetsReflectUtf8Encoding() =
        runTest {
            // U+1F44B (waving hand) is a 4-byte UTF-8 codepoint and
            // takes 2 UTF-16 surrogate halves in Kotlin's String.
            // After the emoji + space, the @handle's byte offset must
            // be 4 (emoji) + 1 (space) = 5, NOT the UTF-16 char index
            // of 3 (high surrogate, low surrogate, space).
            val text = "👋 @alice.bsky.social"
            val result = extractor.extract(text)

            assertEquals(1, result.size)
            val facet = result[0]
            assertEquals(5L, facet.index.byteStart, "byte offset must skip the 4-byte emoji + 1-byte space")
            // Token "@alice.bsky.social" is 18 ASCII bytes, so end = 5 + 18 = 23.
            assertEquals(23L, facet.index.byteEnd)

            // The byte offsets must round-trip correctly through the
            // UTF-8 byte array — extract the slice from the byte array
            // and assert it spells out the @handle.
            val bytes = text.toByteArray(Charsets.UTF_8)
            val slice = bytes.copyOfRange(facet.index.byteStart.toInt(), facet.index.byteEnd.toInt())
            assertEquals("@alice.bsky.social", String(slice, Charsets.UTF_8))
        }

    @Test
    fun emailAddress_atPrecededByWordChar_doesNotMatchAsMention() =
        runTest {
            // "user@example.com" — the @ is preceded by 'r' (a word
            // char), so the mention regex's leading "(?:^|[^\w@])"
            // anchor refuses to match. URLs are also not present.
            val result = extractor.extract("contact user@example.com for help")
            assertTrue(result.isEmpty(), "email address must not produce a mention facet, got $result")
        }

    @Test
    fun mentionWithSurroundingEmoji_emitsCorrectByteOffsets_smokeRoundTrip() =
        runTest {
            // Cross-check: an emoji BEFORE, BETWEEN, and AFTER doesn't
            // perturb the second mention's byte offset. Each emoji is
            // 4 bytes UTF-8.
            val text = "👋 @alice.bsky.social 🎉 @bob.bsky.social 🚀"
            val result = extractor.extract(text)

            assertEquals(2, result.size)
            val bytes = text.toByteArray(Charsets.UTF_8)
            for (facet in result) {
                val slice = bytes.copyOfRange(facet.index.byteStart.toInt(), facet.index.byteEnd.toInt())
                val text = String(slice, Charsets.UTF_8)
                assertTrue(text.startsWith("@"), "byte slice must start with @, got '$text'")
                assertNotNull(facet.features[0] as? FacetMention)
            }
        }
}
