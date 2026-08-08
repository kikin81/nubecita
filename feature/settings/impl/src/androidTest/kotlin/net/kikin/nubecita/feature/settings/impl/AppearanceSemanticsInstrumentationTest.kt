package net.kikin.nubecita.feature.settings.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import net.kikin.nubecita.core.preferences.ThemePreference
import net.kikin.nubecita.core.testing.android.HiltTestActivity
import net.kikin.nubecita.designsystem.NubecitaTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Pins the accessibility contract and the selection behaviour of the theme
 * picker.
 *
 * Like the Content filters picker, this is a one-of-N choice built from rows
 * that individually report as checkable controls. `Modifier.selectableGroup()`
 * plus a per-row `Role.RadioButton` / `selected` override is what makes a screen
 * reader announce "1 of 3" instead of three unrelated controls — a purely
 * semantic property that changes no pixels and no behaviour, so no screenshot
 * baseline or unit test can tell it apart from having done nothing.
 *
 * The re-tap case is here rather than only in the ViewModel test because it is
 * a contract between two layers: `NubecitaListItem.onSelect` deliberately fires
 * on every tap, and the guard lives in the ViewModel. This asserts the event
 * still reaches the host on a re-tap, which is what makes that split safe — if
 * the row silently swallowed it, the ViewModel-side guard would be untested
 * dead code.
 */
@HiltAndroidTest
class AppearanceSemanticsInstrumentationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    private val events = mutableListOf<ThemePreference>()

    @Before
    fun setUp() {
        hiltRule.inject()
        composeTestRule.setContent {
            // Driven by local state rather than a fixed value so a tap actually
            // moves the selection, the way the ViewModel-backed screen behaves.
            var selected by remember { mutableStateOf(ThemePreference.DYNAMIC) }
            NubecitaTheme(dynamicColor = false) {
                AppearanceContent(
                    state = AppearanceState(selected = selected),
                    onEvent = { event ->
                        val theme = (event as AppearanceEvent.ThemeSelected).theme
                        events += theme
                        selected = theme
                    },
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun theme_rows_report_the_radio_button_role() {
        composeTestRule
            .onNodeWithText(darkLabel)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    @Test
    fun exactly_one_theme_row_is_selected() {
        composeTestRule.onNodeWithText(dynamicLabel).assertIsSelected()
        composeTestRule.onNodeWithText(lightLabel).assertIsNotSelected()
        composeTestRule.onNodeWithText(darkLabel).assertIsNotSelected()
    }

    @Test
    fun the_rows_are_inside_one_selectable_group() {
        composeTestRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
            .assertIsDisplayed()
    }

    @Test
    fun tapping_another_theme_moves_the_selection() {
        composeTestRule.onNodeWithText(darkLabel).performClick()

        composeTestRule.onNodeWithText(darkLabel).assertIsSelected()
        composeTestRule.onNodeWithText(dynamicLabel).assertIsNotSelected()
        assertEquals(listOf(ThemePreference.DARK), events)
    }

    @Test
    fun re_tapping_the_selected_row_still_emits_so_the_viewmodel_can_guard() {
        // The row has no re-tap guard by design; NubecitaListItem's KDoc puts it
        // in the ViewModel. This pins the half of that contract the row owns.
        composeTestRule.onNodeWithText(dynamicLabel).performClick()

        composeTestRule.onNodeWithText(dynamicLabel).assertIsSelected()
        assertEquals(listOf(ThemePreference.DYNAMIC), events)
    }

    // Resolved from resources, not hardcoded: the app ships es-419 and pt-BR, so a
    // device in either locale renders translated labels and literal English would
    // fail for a reason unrelated to the semantics under test.
    private val dynamicLabel: String
        get() = composeTestRule.activity.getString(R.string.appearance_theme_dynamic)

    private val lightLabel: String
        get() = composeTestRule.activity.getString(R.string.appearance_theme_light)

    private val darkLabel: String
        get() = composeTestRule.activity.getString(R.string.appearance_theme_dark)
}
