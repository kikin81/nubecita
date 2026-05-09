package net.kikin.nubecita.core.posting

import kotlinx.collections.immutable.ImmutableList
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

class BlueskyLanguageTagsTest {
    @Test
    fun list_isNonEmptyAndContainsCommonTags() {
        assertTrue(BLUESKY_LANGUAGE_TAGS.isNotEmpty(), "BLUESKY_LANGUAGE_TAGS must not be empty")
        // Smoke-check a handful of common tags. Don't enumerate the full
        // list — that's brittle and the upstream source is authoritative.
        listOf("en", "es", "ja", "fr", "de", "pt").forEach { tag ->
            assertTrue(BLUESKY_LANGUAGE_TAGS.contains(tag), "Expected $tag in BLUESKY_LANGUAGE_TAGS")
        }
    }

    @Test
    fun every_tag_roundTripsThroughLocale() {
        // Every entry must be a syntactically valid BCP-47 tag — same
        // validity check the PostingRepository uses on caller-supplied
        // langs. Anything that resolves to "und" would silently drop at
        // submit time and is a porting error.
        BLUESKY_LANGUAGE_TAGS.forEach { tag ->
            val roundTripped = Locale.forLanguageTag(tag).toLanguageTag()
            assertNotEquals("und", roundTripped, "Tag '$tag' does not parse as BCP-47")
        }
    }

    @Test
    fun list_isImmutable() {
        @Suppress("USELESS_IS_CHECK")
        assertTrue(
            BLUESKY_LANGUAGE_TAGS is ImmutableList<*>,
            "BLUESKY_LANGUAGE_TAGS must be an ImmutableList for Compose stability",
        )
    }
}
