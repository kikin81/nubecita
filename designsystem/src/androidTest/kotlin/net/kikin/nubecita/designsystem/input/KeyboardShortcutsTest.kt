package net.kikin.nubecita.designsystem.input

import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The dispatch rules the shell's and the cards' shortcuts rely on: a plain letter handled in an
 * ancestor's `onKeyEvent` fires from a focused non-text element but never from a focused text
 * field (the field consumes it first), and Ctrl or a modifier keeps a plain-letter shortcut
 * from matching.
 */
@OptIn(ExperimentalTestApi::class)
class KeyboardShortcutsTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var plainL = 0
    private var ctrlN = 0

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        composeTestRule.setContent {
            Column(
                Modifier.onKeyEvent { event ->
                    when {
                        event.isPlainPress(Key.L) -> plainL++.let { true }
                        event.isCtrlPress(Key.N) -> ctrlN++.let { true }
                        else -> false
                    }
                },
            ) {
                Box(Modifier.size(48.dp).testTag(TARGET).focusable())
                BasicTextField(rememberTextFieldState(), Modifier.testTag(FIELD))
            }
        }
    }

    // setInTouchMode is device-wide: put it back so later tests (and the device) see touch mode.
    @After
    fun restoreTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
    }

    @Test
    fun plainLetter_firesFromAFocusedElement() {
        target().requestFocus()
        target().performKeyInput { pressKey(Key.L) }
        assertEquals(1, plainL)
    }

    @Test
    fun plainLetter_isConsumedByAFocusedTextField() {
        field().requestFocus()
        field().performKeyInput { pressKey(Key.L) }
        assertEquals(0, plainL)
    }

    @Test
    fun ctrlLetter_doesNotMatchThePlainShortcut_butMatchesCtrl() {
        target().requestFocus()
        target().performKeyInput {
            withKeyDown(Key.CtrlLeft) { pressKey(Key.L) }
            withKeyDown(Key.CtrlLeft) { pressKey(Key.N) }
        }
        assertEquals(0, plainL)
        assertEquals(1, ctrlN)
    }

    private fun target() = composeTestRule.onNodeWithTag(TARGET)

    private fun field() = composeTestRule.onNodeWithTag(FIELD)

    private companion object {
        const val TARGET = "target"
        const val FIELD = "field"
    }
}
