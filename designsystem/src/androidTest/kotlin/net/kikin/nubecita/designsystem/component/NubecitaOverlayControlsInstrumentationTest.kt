package net.kikin.nubecita.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Semantics tree coverage for [NubecitaOverlayIconButton] and [NubecitaOverlayStatButton].
 *
 * Pins the accessibility contracts:
 * 1. Non-toggleable [NubecitaOverlayIconButton] exposes its content description,
 *    Role.Button, click action, and optional onClickLabel.
 * 2. Toggleable [NubecitaOverlayIconButton] exposes Role.Switch, its toggled state,
 *    and toggles on click.
 * 3. [NubecitaOverlayStatButton] pins the verbatim rail semantics:
 *    - Toggleable (like, mute) exposes Role.Switch with label on icon and count.
 *    - Non-toggleable (reply, share) exposes Role.Button with onClickLabel.
 */
class NubecitaOverlayControlsInstrumentationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun overlayIconButton_buttonRole_contentDescription_andOptionalOnClickLabel() {
        var clicked = false
        composeTestRule.setContent {
            NubecitaOverlayIconButton(
                icon = NubecitaIconName.ArrowBack,
                accessibilityLabel = "Back",
                onClick = { clicked = true },
                onClickLabel = "navigate back",
                testTag = "back_btn",
            )
        }

        val node = composeTestRule.onNodeWithTag("back_btn")
        node.assert(hasContentDescription("Back"))
        node.assert(hasClickAction())
        node.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.Role,
                Role.Button,
            ),
        )
        node.assert(
            SemanticsMatcher("has onClickLabel 'navigate back'") { semanticsNode ->
                val action = semanticsNode.config.getOrNull(SemanticsActions.OnClick)
                action?.label == "navigate back"
            },
        )

        node.performClick()
        assertTrue("Expected onClick to be invoked", clicked)
    }

    @Test
    fun overlayIconButton_switchRole_togglesState() {
        var isToggled by mutableStateOf(false)
        composeTestRule.setContent {
            NubecitaOverlayIconButton(
                icon = NubecitaIconName.VolumeUp,
                accessibilityLabel = "Mute",
                active = isToggled,
                toggleable = true,
                onClick = { isToggled = !isToggled },
                testTag = "mute_btn",
            )
        }

        val node = composeTestRule.onNodeWithTag("mute_btn")
        node.assert(hasContentDescription("Mute"))
        node.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.Role,
                Role.Switch,
            ),
        )
        node.assertIsOff()

        node.performClick()
        composeTestRule.waitForIdle()
        assertTrue(isToggled)
        node.assertIsOn()
    }

    @Test
    fun overlayStatButton_toggleableAndCount_exposesSwitchAndCount() {
        var isToggled by mutableStateOf(false)
        composeTestRule.setContent {
            NubecitaOverlayStatButton(
                icon = NubecitaIconName.Favorite,
                accessibilityLabel = "Like",
                active = isToggled,
                toggleable = true,
                count = 1420L,
                onClick = { isToggled = !isToggled },
                testTag = "like_stat_btn",
            )
        }

        val node = composeTestRule.onNodeWithTag("like_stat_btn")
        node.assert(hasContentDescription("Like"))
        node.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.Role,
                Role.Switch,
            ),
        )
        node.assertIsOff()
        composeTestRule.onNodeWithText("1.4K").assertExists()

        node.performClick()
        composeTestRule.waitForIdle()
        assertTrue(isToggled)
        node.assertIsOn()
    }

    @Test
    fun overlayStatButton_buttonRole_onClickLabel() {
        var clicked = false
        composeTestRule.setContent {
            NubecitaOverlayStatButton(
                icon = NubecitaIconName.ChatBubble,
                accessibilityLabel = "Reply",
                onClick = { clicked = true },
                toggleable = false,
                testTag = "reply_stat_btn",
            )
        }

        val node = composeTestRule.onNodeWithTag("reply_stat_btn")
        node.assert(hasClickAction())
        node.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.Role,
                Role.Button,
            ),
        )
        node.assert(
            SemanticsMatcher("has onClickLabel 'Reply'") { semanticsNode ->
                val action = semanticsNode.config.getOrNull(SemanticsActions.OnClick)
                action?.label == "Reply"
            },
        )

        node.performClick()
        assertTrue(clicked)
    }
}
