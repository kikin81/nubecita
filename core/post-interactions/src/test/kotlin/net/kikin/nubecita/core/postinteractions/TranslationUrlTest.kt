package net.kikin.nubecita.core.postinteractions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class TranslationUrlTest {
    @Test
    fun `builds a translate url with auto source and the requested target`() {
        val url = googleTranslateUrl("hola", targetLanguage = "en")
        assertEquals("https://translate.google.com/?sl=auto&tl=en&op=translate&text=hola", url)
    }

    @Test
    fun `escapes spaces as percent-20 rather than plus`() {
        val url = googleTranslateUrl("hola que tal", targetLanguage = "en")
        assertTrue(url.endsWith("text=hola%20que%20tal"), url)
        assertFalse(url.contains("+"), "a + would read as a literal plus to a human")
    }

    @Test
    fun `escapes characters that would otherwise break the query string`() {
        // & and # would truncate or fragment the URL; ? and = would be misread.
        val url = googleTranslateUrl("a&b#c?d=e", targetLanguage = "en")
        assertTrue(url.endsWith("text=a%26b%23c%3Fd%3De"), url)
    }

    @Test
    fun `carries non-latin text through intact`() {
        val url = googleTranslateUrl("任天堂", targetLanguage = "es")
        assertTrue(url.contains("tl=es"))
        assertTrue(url.endsWith("text=%E4%BB%BB%E5%A4%A9%E5%A0%82"), url)
    }

    @Test
    fun `falls back to english when the device reports no language`() {
        // Locale.getDefault().language is "" on a device with an undetermined
        // locale; an empty tl would make the translator pick for us.
        assertTrue(googleTranslateUrl("hola", targetLanguage = "").contains("tl=en"))
    }

    @Test
    fun `a post at the composer's grapheme limit still produces a usable url`() {
        // 300 graphemes is the ceiling the composer enforces, so this is the
        // longest text that can reach here. Well under any practical URL limit.
        val url = googleTranslateUrl("a".repeat(300), targetLanguage = "en")
        assertTrue(url.length < 2000, "url was ${url.length} chars")
    }

    @Test
    fun `an uppercase language code is folded to lowercase`() {
        assertTrue(googleTranslateUrl("hola", targetLanguage = "EN").contains("tl=en"))
    }

    @Test
    fun `a region-qualified tag is reduced to its language`() {
        // Locale.getDefault().language never carries a region, but a caller
        // passing a full BCP-47 tag should not produce tl=es-419.
        assertTrue(googleTranslateUrl("hi", targetLanguage = "es-419").contains("tl=es"))
    }

    @Test
    fun `superseded ISO codes map to what the translator expects`() {
        // Android's Locale reports iw / in / ji for these; Google Translate
        // wants he / id / yi. Passing the legacy tag straight through would
        // translate into the wrong language, or none at all.
        assertTrue(googleTranslateUrl("hi", targetLanguage = "iw").contains("tl=he"))
        assertTrue(googleTranslateUrl("hi", targetLanguage = "in").contains("tl=id"))
        assertTrue(googleTranslateUrl("hi", targetLanguage = "ji").contains("tl=yi"))
    }

    @Test
    fun `a language that merely looks legacy is left alone`() {
        // "is" (Icelandic) and "it" (Italian) are real codes, not aliases —
        // a too-eager map would break them.
        assertTrue(googleTranslateUrl("hi", targetLanguage = "is").contains("tl=is"))
        assertTrue(googleTranslateUrl("hi", targetLanguage = "it").contains("tl=it"))
    }

    @Test
    fun `whitespace around the tag does not reach the url`() {
        assertTrue(googleTranslateUrl("hi", targetLanguage = "  en  ").contains("tl=en&"))
    }
}
