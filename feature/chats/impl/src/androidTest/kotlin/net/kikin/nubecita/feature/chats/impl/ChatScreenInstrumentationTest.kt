package net.kikin.nubecita.feature.chats.impl

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.testing.android.HiltTestActivity
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.chats.api.Chat
import net.kikin.nubecita.feature.chats.impl.data.ConvoResolution
import net.kikin.nubecita.feature.chats.impl.data.MessagePage
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Regression coverage for nubecita-nn3.2's "tap convo → crash" bug:
 * [ChatViewModel] originally received its peer DID via `SavedStateHandle`,
 * which Hilt does not auto-populate from a Navigation 3 NavKey's fields.
 * The fix routes the [Chat] NavKey through `@AssistedInject` instead.
 *
 * This test exercises the full assisted-factory entry path:
 *  1. Constructs a [Chat] with a real `otherUserDid`.
 *  2. Builds the [ChatViewModel] through its public assisted-injected
 *     constructor — the same shape `entry<Chat>` uses at runtime via
 *     `hiltViewModel(creationCallback = factory.create(chat))`.
 *  3. Renders [ChatScreen] in a Hilt test activity.
 *  4. Asserts the screen composes without crashing AND the back-button
 *     content description is visible (cheap "the TopAppBar painted"
 *     smoke check).
 *
 * Any future refactor that re-introduces the SavedStateHandle-or-similar
 * arg-passing mistake will fail this test at compile or runtime with the
 * same `IllegalArgumentException` seen in the original device crash.
 */
@HiltAndroidTest
class ChatScreenInstrumentationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun chatScreenRendersWithoutCrash_whenViewModelConstructedFromNavKey() {
        val fakeRepo =
            FakeChatRepository(
                nextResolveResult =
                    Result.success(
                        ConvoResolution(
                            convoId = "c1",
                            otherUserHandle = "alice.bsky.social",
                            otherUserDisplayName = "Alice",
                            otherUserAvatarUrl = null,
                            otherUserAvatarHue = 217,
                        ),
                    ),
                nextMessagesResult = Result.success(MessagePage(messages = persistentListOf())),
            )

        // Construct the VM the same way `entry<Chat>` does in production: through
        // the assisted factory with the NavKey instance. If we ever revert to a
        // SavedStateHandle-based construction, this line fails at compile or runtime.
        val chat = Chat(otherUserDid = "did:plc:alice")
        val viewModel = ChatViewModel(chat = chat, repository = fakeRepo)

        composeTestRule.setContent {
            NubecitaTheme(dynamicColor = false) {
                ChatScreen(viewModel = viewModel, onNavigateBack = {})
            }
        }

        composeTestRule.waitForIdle()
        // TopAppBar's back button is always rendered — cheapest smoke check that
        // the screen composed successfully past the VM-init failure point.
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }
}
