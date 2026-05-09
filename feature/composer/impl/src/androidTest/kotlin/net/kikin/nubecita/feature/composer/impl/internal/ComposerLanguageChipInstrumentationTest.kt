package net.kikin.nubecita.feature.composer.impl.internal

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI coverage for [ComposerLanguageChip]'s three label states.
 *
 * Pinning `deviceLocaleTag = "en-US"` keeps the rendered display name
 * deterministic regardless of the device's `Locale.getDefault()`. The
 * chip itself still calls `getDisplayName(Locale.getDefault())` for
 * localization, so these assertions assume an English-locale runner —
 * the same assumption `:feature:composer:impl`'s pre-existing tests
 * make. If a non-English CI runner ever surfaces, the remediation is
 * a `displayLocale: Locale = Locale.getDefault()` parameter on
 * `ComposerLanguageChip` pinned to `Locale.US` here.
 */
class ComposerLanguageChipInstrumentationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun nullSelection_showsDeviceLocaleDisplayName() {
        composeTestRule.setContent {
            ComposerLanguageChip(
                selectedLangs = null,
                deviceLocaleTag = "en-US",
                onClick = {},
            )
        }
        composeTestRule.onNodeWithText("English").assertIsDisplayed()
    }

    @Test
    fun singleSelection_showsThatLangsDisplayName() {
        composeTestRule.setContent {
            ComposerLanguageChip(
                selectedLangs = listOf("ja-JP"),
                deviceLocaleTag = "en-US",
                onClick = {},
            )
        }
        composeTestRule.onNodeWithText("Japanese").assertIsDisplayed()
    }

    @Test
    fun emptySelection_showsExplicitNoLanguageLabel() {
        // Distinct from null: an explicitly-committed empty list means
        // the user said "no language" (createPost will omit the langs
        // field entirely per wtq.12). The chip must surface that as
        // a different label than the null-state device-locale fallback,
        // otherwise it lies about what's about to be sent.
        composeTestRule.setContent {
            ComposerLanguageChip(
                selectedLangs = emptyList(),
                deviceLocaleTag = "en-US",
                onClick = {},
            )
        }
        composeTestRule.onNodeWithText("No language").assertIsDisplayed()
    }

    @Test
    fun multiSelection_showsFirstNamePlusOverflowCount() {
        composeTestRule.setContent {
            ComposerLanguageChip(
                selectedLangs = listOf("en-US", "ja-JP", "es-MX"),
                deviceLocaleTag = "en-US",
                onClick = {},
            )
        }
        composeTestRule.onNodeWithText("English +2").assertIsDisplayed()
    }

    @Test
    fun tap_invokesOnClick() {
        var clickCount = 0
        composeTestRule.setContent {
            ComposerLanguageChip(
                selectedLangs = null,
                deviceLocaleTag = "en-US",
                onClick = { clickCount++ },
            )
        }
        composeTestRule.onNodeWithText("English").performClick()
        assertEquals(1, clickCount)
    }
}
