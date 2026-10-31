package net.kikin.nubecita.feature.composer.impl.internal

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Accessibility and gating contract for [ComposerFloatingToolbar]: each
 * action is announced once by name, and the Post FAB — which has no
 * `enabled` parameter — is exposed as disabled and ignores taps when the
 * draft can't be posted, and announces "Submitting your post" while sending.
 *
 * English-locale runner (labels are the default-locale strings).
 */
class ComposerFloatingToolbarInstrumentationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun eachActionIsLabelledOnce() {
        setToolbar()
        listOf("Add image", "Post language", "Who can interact?", "Post").forEach { label ->
            composeTestRule.onAllNodesWithContentDescription(label).assertCountEquals(1)
        }
    }

    @Test
    fun postFab_disabledWhenTheDraftCantBePosted_andIgnoresTaps() {
        var posts = 0
        setToolbar(canPost = false, onPostClick = { posts++ })

        composeTestRule
            .onNodeWithContentDescription("Post", useUnmergedTree = false)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
            .performClick()

        assertEquals(0, posts)
    }

    @Test
    fun postFab_postsWhenEnabled() {
        var posts = 0
        setToolbar(canPost = true, onPostClick = { posts++ })

        composeTestRule.onNodeWithContentDescription("Post").performClick()

        assertEquals(1, posts)
    }

    @Test
    fun postFab_announcesSubmittingWhileSending() {
        setToolbar(canPost = true, isSubmitting = true)

        composeTestRule.onNodeWithContentDescription("Submitting your post").assertIsDisplayed()
    }

    @Test
    fun audienceActionHiddenInReplies() {
        setToolbar(showAudience = false)

        composeTestRule.onAllNodesWithContentDescription("Who can interact?").assertCountEquals(0)
    }

    private fun setToolbar(
        canPost: Boolean = true,
        isSubmitting: Boolean = false,
        showAudience: Boolean = true,
        onPostClick: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            ComposerFloatingToolbar(
                canPost = canPost,
                isSubmitting = isSubmitting,
                canAddImage = true,
                canAddGif = true,
                showAudience = showAudience,
                onPostClick = onPostClick,
                onAddImageClick = {},
                onGifClick = {},
                onLanguageClick = {},
                onAudienceClick = {},
            )
        }
    }
}
