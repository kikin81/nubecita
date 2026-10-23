package net.kikin.nubecita.feature.profile.impl

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.testing.android.HiltTestActivity
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.profile.impl.data.EditableProfileLink
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The drag gesture isn't reachable with TalkBack, so each link row exposes
 * move up / move down custom actions. They must sit on the node TalkBack
 * focuses — the merged, clickable row that carries the link's text — or the
 * screen reader never offers them.
 */
@HiltAndroidTest
class EditProfileLinksAccessibilityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    private val events = mutableListOf<EditProfileEvent>()

    private fun link(
        n: Int,
        title: String,
    ) = EditableProfileLink(localId = "l$n", url = "https://s$n.example", title = title, uri = "at://me/app.bsky.actor.link/$n", cid = "c$n")

    @Before
    fun setUp() {
        hiltRule.inject()
        composeTestRule.setContent {
            NubecitaTheme(dynamicColor = false) {
                EditProfileLinksSection(
                    state =
                        EditProfileViewState(
                            links = persistentListOf(link(1, "First"), link(2, "Second"), link(3, "Third")),
                            linksStatus = LinksLoadStatus.Loaded,
                        ),
                    onEvent = { events += it },
                )
            }
        }
    }

    private fun actionLabels(title: String): List<String> =
        composeTestRule
            .onNodeWithText(title)
            .fetchSemanticsNode()
            .config
            .getOrElse(SemanticsActions.CustomActions) { emptyList() }
            .map { it.label }

    @Test
    fun middleRow_offersMoveUpAndDown_onTheFocusedNode() {
        assertEquals(listOf("Move up", "Move down"), actionLabels("Second"))
    }

    @Test
    fun firstAndLastRows_offerOnlyTheValidDirection() {
        assertEquals(listOf("Move down"), actionLabels("First"))
        assertEquals(listOf("Move up"), actionLabels("Third"))
    }

    @Test
    fun invokingMoveUp_sendsMoveLink() {
        val moveUp =
            composeTestRule
                .onNodeWithText("Second")
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .single { it.label == "Move up" }
        composeTestRule.runOnIdle { moveUp.action() }
        assertEquals(listOf<EditProfileEvent>(EditProfileEvent.MoveLink(from = 1, to = 0)), events)
    }
}
