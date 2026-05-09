package net.kikin.nubecita.feature.composer.impl.internal

import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LanguagePickerLogicTest {
    // A small, predictable subset of BLUESKY_LANGUAGE_TAGS. The full
    // 184-tag list is exercised by the screenshot fixtures + the
    // BlueskyLanguageTagsTest in :core:posting; here we only need
    // enough variety to pin the sort + filter contracts.
    private val tags = persistentListOf("en", "ja", "es", "fr", "de", "ar")

    // ---------- sortPickerTags ----------

    @Test
    fun sort_putsSelectedTagsFirst() {
        val ordered =
            sortPickerTags(
                allTags = tags,
                draftSelection = persistentListOf("ja"),
                deviceLocaleTag = "en-US",
            )
        // Japanese is selected and pinned to the top; English (device
        // locale) follows next; the rest are alphabetical by display
        // name in the JVM default locale.
        assertEquals("ja", ordered.first())
    }

    @Test
    fun sort_promotesDeviceLocaleAfterSelected() {
        val ordered =
            sortPickerTags(
                allTags = tags,
                draftSelection = persistentListOf(),
                deviceLocaleTag = "en-US",
            )
        // No selections yet: device locale is the first row.
        assertEquals("en", ordered.first())
    }

    @Test
    fun sort_alphabetizesUnselectedNonDeviceTags() {
        val ordered =
            sortPickerTags(
                allTags = tags,
                draftSelection = persistentListOf(),
                deviceLocaleTag = "en-US",
            )
        // After "en" (device locale), the rest sort alphabetically by
        // localized display name on an English JVM:
        // Arabic, French, German, Japanese, Spanish.
        assertEquals(listOf("en", "ar", "fr", "de", "ja", "es"), ordered)
    }

    @Test
    fun sort_normalizesRegionQualifiedDeviceLocale() {
        // The device locale is a region-qualified tag; sortPickerTags
        // collapses it to the bare language portion so the bare "ja"
        // row in `allTags` is recognized as the device locale and
        // promoted.
        val ordered =
            sortPickerTags(
                allTags = tags,
                draftSelection = persistentListOf(),
                deviceLocaleTag = "ja-JP",
            )
        assertEquals("ja", ordered.first())
    }

    @Test
    fun sort_pinsMultipleSelectedTagsTogether() {
        val ordered =
            sortPickerTags(
                allTags = tags,
                draftSelection = persistentListOf("es", "ja"),
                deviceLocaleTag = "en-US",
            )
        // Both selected tags appear before everything else, in
        // alphabetical-by-display-name order ("Japanese" < "Spanish").
        assertEquals(listOf("ja", "es"), ordered.take(2))
    }

    // ---------- matchesPickerQuery ----------

    @Test
    fun matchesQuery_byBareTag() {
        assertTrue(matchesPickerQuery(tag = "en", query = "en"))
        assertTrue(matchesPickerQuery(tag = "es", query = "es"))
    }

    @Test
    fun matchesQuery_byDisplayNamePrefix() {
        // English user types "Span" → Spanish row remains visible.
        assertTrue(matchesPickerQuery(tag = "es", query = "Span"))
        assertTrue(matchesPickerQuery(tag = "ja", query = "Japan"))
    }

    @Test
    fun matchesQuery_isCaseInsensitive() {
        assertTrue(matchesPickerQuery(tag = "ja", query = "japanese"))
        assertTrue(matchesPickerQuery(tag = "ja", query = "JAPANESE"))
    }

    @Test
    fun matchesQuery_trimsWhitespace() {
        // The picker's OutlinedTextField doesn't sanitize, so leading/
        // trailing spaces would otherwise drop matches the user
        // expects.
        assertTrue(matchesPickerQuery(tag = "es", query = "  span  "))
    }

    @Test
    fun matchesQuery_blankQueryMatchesEverything() {
        assertTrue(matchesPickerQuery(tag = "en", query = ""))
        assertTrue(matchesPickerQuery(tag = "en", query = "   "))
    }

    @Test
    fun matchesQuery_nonMatchingTextRejects() {
        assertFalse(matchesPickerQuery(tag = "ja", query = "Span"))
        assertFalse(matchesPickerQuery(tag = "en", query = "zzzzzz"))
    }
}
