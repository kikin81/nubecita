package net.kikin.nubecita.feature.composer.impl.internal

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI coverage for [LanguagePickerContent] — search filtering,
 * cap-of-3 disabled-checkbox state, Done/Cancel dispatching.
 *
 * Display-name assertions assume an English-locale runner (matching
 * `:feature:composer:impl`'s pre-existing test pattern); see
 * `ComposerLanguageChipInstrumentationTest` for the same caveat.
 */
class LanguagePickerContentInstrumentationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val tags = persistentListOf("en", "ja", "es", "fr")

    @Test
    fun search_filtersByDisplayName() {
        composeTestRule.setContent {
            LanguagePickerContent(
                allTags = tags,
                draftSelection = persistentListOf(),
                deviceLocaleTag = "en-US",
                onToggle = {},
                onConfirm = {},
                onDismiss = {},
            )
        }
        composeTestRule
            .onNodeWithTag(SEARCH_FIELD_TEST_TAG)
            .performTextInput("Span")
        composeTestRule.onNodeWithText("Spanish").assertIsDisplayed()
        // Japanese should be filtered out.
        composeTestRule.onAllNodesWithText("Japanese").assertCountEquals(0)
    }

    @Test
    fun atCapOfThree_uncheckedRowsAreDisabled() {
        composeTestRule.setContent {
            LanguagePickerContent(
                allTags = tags,
                draftSelection = persistentListOf("en", "ja", "es"),
                deviceLocaleTag = "en-US",
                onToggle = {},
                onConfirm = {},
                onDismiss = {},
            )
        }
        // The unchecked row (French) should be disabled.
        composeTestRule.onNodeWithText("French").assertIsNotEnabled()
    }

    @Test
    fun done_dispatchesCurrentDraftSelection() {
        var captured: List<String>? = null
        composeTestRule.setContent {
            LanguagePickerContent(
                allTags = tags,
                draftSelection = persistentListOf("ja"),
                deviceLocaleTag = "en-US",
                onToggle = {},
                onConfirm = { captured = it },
                onDismiss = {},
            )
        }
        composeTestRule.onNodeWithText("Done").performClick()
        assertEquals(listOf("ja"), captured)
    }

    @Test
    fun cancel_invokesDismissWithoutDispatch() {
        var confirmed = 0
        var dismissed = 0
        composeTestRule.setContent {
            LanguagePickerContent(
                allTags = tags,
                draftSelection = persistentListOf("ja"),
                deviceLocaleTag = "en-US",
                onToggle = {},
                onConfirm = { confirmed++ },
                onDismiss = { dismissed++ },
            )
        }
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue("Cancel must not dispatch confirm", confirmed == 0)
        assertEquals("Cancel must invoke onDismiss exactly once", 1, dismissed)
    }
}
