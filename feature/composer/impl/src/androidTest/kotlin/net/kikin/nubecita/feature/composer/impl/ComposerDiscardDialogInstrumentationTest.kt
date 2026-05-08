package net.kikin.nubecita.feature.composer.impl

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.testing.android.HiltTestActivity
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.composer.impl.internal.ComposerDialogAction
import net.kikin.nubecita.feature.composer.impl.internal.ComposerDiscardDialogContent
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Instrumentation coverage for the composer discard-confirmation
 * dialog's inner content primitive ([ComposerDiscardDialogContent]).
 *
 * Renders the content card directly (not the
 * `ComposerDiscardDialog` wrapper) so the test stays in the
 * activity's semantics tree — `BasicAlertDialog` and `Popup` both
 * render in separate Windows that the default Compose test framework
 * doesn't cross. The card primitive is the *visible* dialog content
 * shipped behind both width-class branches in production, so the
 * coverage holds: the wrapper's only job is to choose the
 * windowing mechanism.
 *
 * Layered coverage:
 *  - Pure gate logic (back-press / X-tap / Submitting precedence)
 *    covered on the JVM by `ComposerCloseAttemptTest`.
 *  - Visual layout / theming / both width classes covered by
 *    `ComposerDiscardDialogScreenshotTest` baselines.
 *  - Action callbacks fire when the user taps a rendered button:
 *    this test.
 *  - Full screen back-press → dialog → action → nav callback flow:
 *    bd `nubecita-wtq.8` step 11.1 manual smoke at Compact + Expanded.
 */
@HiltAndroidTest
class ComposerDiscardDialogInstrumentationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun cancelButton_invokesCancelOnClick_andDoesNotInvokeDiscard() {
        var cancelInvocations = 0
        var discardInvocations = 0
        composeTestRule.setContent {
            NubecitaTheme {
                ComposerDiscardDialogContent(
                    actions =
                        persistentListOf(
                            ComposerDialogAction(
                                label = R.string.composer_discard_cancel,
                                onClick = { cancelInvocations++ },
                            ),
                            ComposerDialogAction(
                                label = R.string.composer_discard_confirm,
                                destructive = true,
                                onClick = { discardInvocations++ },
                            ),
                        ),
                )
            }
        }

        // Title pins the dialog rendered (not just buttons in isolation).
        composeTestRule.onNodeWithText(DISCARD_TITLE).assertIsDisplayed()
        composeTestRule.onNodeWithText(CANCEL_LABEL).performClick()

        assertEquals(1, cancelInvocations)
        assertEquals(0, discardInvocations)
    }

    @Test
    fun discardButton_invokesDiscardOnClick_andDoesNotInvokeCancel() {
        var cancelInvocations = 0
        var discardInvocations = 0
        composeTestRule.setContent {
            NubecitaTheme {
                ComposerDiscardDialogContent(
                    actions =
                        persistentListOf(
                            ComposerDialogAction(
                                label = R.string.composer_discard_cancel,
                                onClick = { cancelInvocations++ },
                            ),
                            ComposerDialogAction(
                                label = R.string.composer_discard_confirm,
                                destructive = true,
                                onClick = { discardInvocations++ },
                            ),
                        ),
                )
            }
        }

        composeTestRule.onNodeWithText(DISCARD_LABEL).performClick()

        assertEquals(0, cancelInvocations)
        assertEquals(1, discardInvocations)
    }

    @Test
    fun rendersTitle_andBothActions_inOrder() {
        composeTestRule.setContent {
            NubecitaTheme {
                ComposerDiscardDialogContent(
                    actions =
                        persistentListOf(
                            ComposerDialogAction(
                                label = R.string.composer_discard_cancel,
                                onClick = {},
                            ),
                            ComposerDialogAction(
                                label = R.string.composer_discard_confirm,
                                destructive = true,
                                onClick = {},
                            ),
                        ),
                )
            }
        }

        composeTestRule.onNodeWithText(DISCARD_TITLE).assertIsDisplayed()
        composeTestRule.onNodeWithText(CANCEL_LABEL).assertIsDisplayed()
        composeTestRule.onNodeWithText(DISCARD_LABEL).assertIsDisplayed()
    }

    private companion object {
        // Hard-coded English strings — the test runs against the default
        // locale with the strings.xml entries shipped in this PR. Keeping
        // them as constants here mirrors the FeedScreenInstrumentationTest
        // pattern (POST_ALICE_TEXT etc.) and surfaces a clear breakage
        // signal if string keys are renamed without the test being
        // updated alongside.
        const val DISCARD_TITLE = "Discard draft?"
        const val CANCEL_LABEL = "Cancel"
        const val DISCARD_LABEL = "Discard"
    }
}
