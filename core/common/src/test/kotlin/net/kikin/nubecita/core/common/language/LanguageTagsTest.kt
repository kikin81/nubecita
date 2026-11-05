package net.kikin.nubecita.core.common.language

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

internal class LanguageTagsTest {
    @Test
    fun `a bare tag normalizes to itself`() {
        assertEquals("ja", LanguageTags.normalize("ja"))
    }

    @Test
    fun `region and script subtags are stripped`() {
        assertEquals("es", LanguageTags.normalize("es-419"))
        assertEquals("pt", LanguageTags.normalize("pt-BR"))
        assertEquals("zh", LanguageTags.normalize("zh-Hant-TW"))
    }

    @Test
    fun `underscore separators are handled`() {
        // Locale#toString produces these, and they reach us from older APIs.
        assertEquals("en", LanguageTags.normalize("en_US"))
    }

    @Test
    fun `legacy ISO codes are corrected`() {
        assertEquals("he", LanguageTags.normalize("iw"))
        assertEquals("id", LanguageTags.normalize("in"))
        assertEquals("yi", LanguageTags.normalize("ji"))
        assertEquals("he", LanguageTags.normalize("iw-IL"))
    }

    @Test
    fun `blank and separator-only tags are unusable`() {
        assertNull(LanguageTags.normalize(""))
        assertNull(LanguageTags.normalize("   "))
        assertNull(LanguageTags.normalize("-US"))
    }

    @Test
    fun `lower-casing does not fold through the default locale`() {
        // On a Turkish default locale, "I".lowercase() is "ı" — which is not a
        // language tag anyone recognises. Normalizing must go through ROOT.
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("id", LanguageTags.normalize("IN"))
            assertEquals("it", LanguageTags.normalize("IT"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `matching ignores region`() {
        assertTrue(LanguageTags.matchesAny("es-419", listOf("es")))
        assertTrue(LanguageTags.matchesAny("es", listOf("es-MX")))
        assertTrue(LanguageTags.matchesAny("en-GB", listOf("fr", "en")))
    }

    @Test
    fun `matching fails on a different language`() {
        assertFalse(LanguageTags.matchesAny("ja", listOf("en", "es")))
        assertFalse(LanguageTags.matchesAny("ja", emptyList()))
        assertFalse(LanguageTags.matchesAny("", listOf("en")))
    }

    @Test
    fun `matching sees through a legacy code on either side`() {
        assertTrue(LanguageTags.matchesAny("iw", listOf("he")))
        assertTrue(LanguageTags.matchesAny("he", listOf("iw")))
    }

    @Test
    fun `normalizeAll de-duplicates and keeps first-seen order`() {
        // Order carries meaning: the first entry is the reader's primary
        // language and therefore the translation target.
        assertEquals(
            listOf("es", "en", "he"),
            LanguageTags.normalizeAll(listOf("es-419", "en-US", "es-MX", "iw", "", "en")),
        )
    }

    @Test
    fun `normalizeAll on nothing usable is empty`() {
        assertEquals(emptyList<String>(), LanguageTags.normalizeAll(listOf("", "  ", "-")))
    }
}
