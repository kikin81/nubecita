package net.kikin.nubecita.feature.profile.impl

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import net.kikin.nubecita.core.testing.android.HiltTestActivity
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.profile.impl.testing.FakeAuthRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * End-to-end Settings stub sign-out flow:
 *
 * - Tap Sign Out → opens the confirmation dialog.
 * - Tap Confirm in the dialog → calls `AuthRepository.signOut()`.
 * - On failure, the error snackbar surfaces with the expected copy.
 *
 * Uses [TestAuthRepositoryModule] (Hilt test module) to replace the
 * production `AuthBindingsModule` with a fake that counts calls and
 * lets the test class control the return value.
 */
@HiltAndroidTest
class SettingsStubInstrumentationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        FakeAuthRepository.shared.reset()
    }

    @Test
    fun signOut_success_callsAuthRepositoryExactlyOnce() {
        FakeAuthRepository.shared.nextSignOutResult = Result.success(Unit)

        composeTestRule.setContent {
            NubecitaTheme(dynamicColor = false) {
                SettingsStubScreen(onBack = {})
            }
        }

        // Tap the Sign Out button (resolves first via merged tree).
        composeTestRule.onNodeWithText("Sign out").performClick()
        // Dialog now open. The dialog's Confirm button is ALSO labeled "Sign out".
        // Use useUnmergedTree to surface the dialog's content separately,
        // and pick the second matching node (the dialog button, on top of the screen button).
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithText("Sign out", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size >= 2
        }
        // Tap the dialog confirm button.
        composeTestRule
            .onAllNodesWithText("Sign out", useUnmergedTree = true)[1]
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            FakeAuthRepository.shared.signOutCalls.get() >= 1
        }
        assertEquals(1, FakeAuthRepository.shared.signOutCalls.get())
    }

    @Test
    fun signOut_failure_surfacesErrorSnackbar() {
        FakeAuthRepository.shared.nextSignOutResult = Result.failure(IOException("net down"))

        composeTestRule.setContent {
            NubecitaTheme(dynamicColor = false) {
                SettingsStubScreen(onBack = {})
            }
        }

        composeTestRule.onNodeWithText("Sign out").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithText("Sign out", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size >= 2
        }
        composeTestRule
            .onAllNodesWithText("Sign out", useUnmergedTree = true)[1]
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithText("Couldn't sign out", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule
            .onNodeWithText("Couldn't sign out", substring = true)
            .assertIsDisplayed()
    }
}
