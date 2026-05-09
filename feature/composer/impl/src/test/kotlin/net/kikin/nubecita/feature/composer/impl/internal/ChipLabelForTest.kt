package net.kikin.nubecita.feature.composer.impl.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pin-down tests for [chipLabelFor]. The composable is a 4-line
 * wrapper around this helper plus a `remember(...)`, so anchoring the
 * label rule here gives the unit-test coverage the instrumentation
 * suite can't (Jacoco doesn't see connectedAndroidTest results).
 */
class ChipLabelForTest {
    private val emptyLabel = "No language"

    @Test
    fun nullSelection_returnsDeviceLocaleDisplayName() {
        // null is the "user hasn't touched the picker" state — the
        // chip mirrors the device-locale display name so it tells the
        // truth about what wtq.12's repo-side default would send.
        assertEquals("English", chipLabelFor(null, "en-US", emptyLabel))
    }

    @Test
    fun nullSelection_collapsesRegionQualifiedDeviceLocale() {
        // Confirms the chip's null-state label uses the bare-language
        // form, matching what the picker rows render.
        assertEquals("Japanese", chipLabelFor(null, "ja-JP", emptyLabel))
    }

    @Test
    fun emptySelection_returnsEmptyLabel() {
        // emptyList() != null. Distinct override semantics: explicit
        // empty means "createPost will omit the langs field"; the
        // chip surfaces the localized empty label rather than lying
        // with the device-locale fallback.
        assertEquals(emptyLabel, chipLabelFor(emptyList(), "en-US", emptyLabel))
    }

    @Test
    fun singleTag_returnsItsDisplayName() {
        assertEquals("Japanese", chipLabelFor(listOf("ja-JP"), "en-US", emptyLabel))
    }

    @Test
    fun twoTags_appendOverflowOne() {
        assertEquals(
            "English +1",
            chipLabelFor(listOf("en-US", "ja-JP"), "en-US", emptyLabel),
        )
    }

    @Test
    fun threeTags_appendOverflowTwo() {
        // Three is the cap (ComposerViewModel.MAX_LANGS); the chip
        // surfaces the first tag plus "+2".
        assertEquals(
            "English +2",
            chipLabelFor(listOf("en-US", "ja-JP", "es-MX"), "en-US", emptyLabel),
        )
    }

    @Test
    fun firstTagWins_independentOfOtherTags() {
        // Order matters: the first selected tag is the visible one.
        assertEquals(
            "Japanese +2",
            chipLabelFor(listOf("ja-JP", "en-US", "es-MX"), "en-US", emptyLabel),
        )
    }
}
