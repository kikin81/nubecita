package net.kikin.nubecita.feature.settings.impl

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import net.kikin.nubecita.core.testing.android.HiltTestActivity
import net.kikin.nubecita.designsystem.NubecitaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Pins the accessibility contract of the Show / Warn / Hide picker.
 *
 * The picker is a connected button group of [androidx.compose.material3.ToggleButton]s,
 * which report themselves as independent checkable controls — so a screen reader
 * announced three unrelated toggle buttons for what is one single-select choice
 * (`nubecita-1ow5.10`). The fix is purely semantic: `Modifier.selectableGroup()`
 * on the row plus a per-segment `Role.RadioButton` / `selected` override.
 *
 * That kind of fix is invisible to every other gate in the repo — it changes no
 * pixels, so the screenshot baselines are unchanged by design, and it changes no
 * behaviour, so the unit tests pass either way. Without these assertions the
 * change would be indistinguishable from having done nothing, which is exactly
 * how an accessibility "fix" rots.
 */
@HiltAndroidTest
class ContentFiltersSemanticsInstrumentationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        composeTestRule.setContent {
            NubecitaTheme(dynamicColor = false) {
                ContentFiltersContent(
                    // adultEnabled = true so every category picker is interactive;
                    // a disabled segment would not exercise the selected state.
                    state = contentFiltersPreviewState(adultEnabled = true),
                    onEvent = {},
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun visibility_segments_report_the_radio_button_role() {
        // Not Role.Switch / an unqualified checkable: the role is what makes a
        // screen reader describe this as a one-of-N choice.
        composeTestRule
            .onAllNodesWithText(SHOW_LABEL)
            .onFirst()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    @Test
    fun exactly_one_visibility_segment_is_selected() {
        // `selected` (not `toggleableState`) is the property a radio group is read
        // through. Four categories share these labels, so this asserts the FIRST
        // picker — Adult Content — whose ModerationPrefs default is HIDE, not the
        // WARN that the other categories default to.
        composeTestRule.onAllNodesWithText(HIDE_LABEL).onFirst().assertIsSelected()
        composeTestRule.onAllNodesWithText(SHOW_LABEL).onFirst().assertIsNotSelected()
        composeTestRule.onAllNodesWithText(WARN_LABEL).onFirst().assertIsNotSelected()
    }

    @Test
    fun the_segments_are_inside_one_selectable_group() {
        // selectableGroup() is what supplies the collection context — without it a
        // screen reader has three loose controls rather than "2 of 3".
        composeTestRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
            .onFirst()
            .assertIsDisplayed()
    }

    private companion object {
        const val SHOW_LABEL = "Show"
        const val WARN_LABEL = "Warn"
        const val HIDE_LABEL = "Hide"
    }
}
