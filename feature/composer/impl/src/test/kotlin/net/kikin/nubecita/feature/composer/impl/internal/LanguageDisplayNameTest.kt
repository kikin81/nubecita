package net.kikin.nubecita.feature.composer.impl.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pin-down tests for [languageDisplayName].
 *
 * Display-name lookups go through `Locale.forLanguageTag(tag)
 * .getDisplayLanguage(Locale.getDefault())` — these assertions assume
 * an English JVM (the project's pre-existing test pattern, matching
 * `ComposerLanguageChipInstrumentationTest`'s convention). On a non-
 * English CI runner the strings would localize, but the behavioral
 * contract (region-qualified tags collapse to bare language names)
 * holds regardless.
 */
class LanguageDisplayNameTest {
    @Test
    fun bareLanguageTag_returnsLanguageName() {
        assertEquals("English", languageDisplayName("en"))
        assertEquals("Japanese", languageDisplayName("ja"))
        assertEquals("Spanish", languageDisplayName("es"))
    }

    @Test
    fun regionQualifiedTag_collapsesToBareLanguageName() {
        // The chip + picker spec relies on this: en-US renders as
        // "English", not "English (United States)". Without this, the
        // chip's null-state label (built from a region-qualified
        // deviceLocaleTag) wouldn't match the picker's row labels
        // (built from bare BLUESKY_LANGUAGE_TAGS entries).
        assertEquals("English", languageDisplayName("en-US"))
        assertEquals("Japanese", languageDisplayName("ja-JP"))
        assertEquals("Spanish", languageDisplayName("es-MX"))
    }

    @Test
    fun firstCharacter_isTitleCased() {
        // Defensive against locales whose getDisplayLanguage returns
        // the lowercased name (some Java locales for German / French
        // historically did this). The assertion holds trivially for
        // English but documents the intent.
        val name = languageDisplayName("en")
        assertEquals(name.first().uppercaseChar(), name.first())
    }
}
