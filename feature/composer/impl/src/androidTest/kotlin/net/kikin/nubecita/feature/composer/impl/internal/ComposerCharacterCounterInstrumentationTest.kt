package net.kikin.nubecita.feature.composer.impl.internal

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test

/**
 * Instrumentation coverage for [ComposerCharacterCounter]'s a11y
 * contract: the counter SHALL expose itself as a single accessible
 * label (the localized phrase passed in via [contentDescription])
 * with NO descendant numeric text contributing to the announcement.
 *
 * Without this guard, TalkBack reads both the localized phrase
 * ("293 characters remaining") and the bare numeric label ("293"),
 * producing a duplicate announcement. The fix is to use
 * `Modifier.clearAndSetSemantics` on the parent Box so the numeric
 * Text descendant is removed from the semantic tree entirely.
 */
class ComposerCharacterCounterInstrumentationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun counter_exposesLocalizedContentDescription() {
        composeTestRule.setContent {
            ComposerCharacterCounter(
                graphemeCount = 7,
                isOverLimit = false,
                contentDescription = "293 characters remaining",
            )
        }
        composeTestRule
            .onNodeWithContentDescription("293 characters remaining")
            .assertIsDisplayed()
    }

    @Test
    fun counter_doesNotExposeBareNumericLabel_inAccessibilityTree() {
        // The visible "293" inside the arc is purely visual — it
        // duplicates the localized contentDescription. We assert the
        // *merged* a11y tree (what TalkBack actually walks) for two
        // things:
        //
        // 1. `onAllNodesWithText("293")` returns 0 — no node merges
        //    or surfaces the bare numeric Text.
        // 2. The parent counter node has zero merged children — its
        //    semantics are the canonical, complete announcement.
        //
        // With a plain `Modifier.semantics { … }` block on the Box,
        // (1) would return 1 (the Text contributes its own node) and
        // (2) would return 1 (the Text descendant is visible under
        // the parent in the merged tree). `clearAndSetSemantics`
        // replaces the parent's semantics entirely and tells a11y
        // services "stop here" — descendants drop out of the merged
        // tree.
        composeTestRule.setContent {
            ComposerCharacterCounter(
                graphemeCount = 7,
                isOverLimit = false,
                contentDescription = "293 characters remaining",
            )
        }
        composeTestRule.onAllNodesWithText("293").assertCountEquals(0)
        composeTestRule
            .onNodeWithContentDescription("293 characters remaining")
            .onChildren()
            .assertCountEquals(0)
    }

    @Test
    fun counter_doesNotExposeNegativeOffset_whenOverLimit() {
        // Same contract on the over-limit branch: the visible "-5"
        // duplicates the localized "Over the 300 character limit by 5"
        // phrase, and must not surface in the merged a11y tree.
        composeTestRule.setContent {
            ComposerCharacterCounter(
                graphemeCount = 305,
                isOverLimit = true,
                contentDescription = "Over the 300 character limit by 5",
            )
        }
        composeTestRule
            .onNodeWithContentDescription("Over the 300 character limit by 5")
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithText("-5").assertCountEquals(0)
        composeTestRule
            .onNodeWithContentDescription("Over the 300 character limit by 5")
            .onChildren()
            .assertCountEquals(0)
    }
}
