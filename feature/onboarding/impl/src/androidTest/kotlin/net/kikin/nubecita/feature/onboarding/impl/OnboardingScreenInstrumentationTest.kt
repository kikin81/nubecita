package net.kikin.nubecita.feature.onboarding.impl

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import net.kikin.nubecita.core.common.navigation.LocalAppNavigator
import net.kikin.nubecita.core.preferences.UserPreferencesRepository
import net.kikin.nubecita.core.testing.android.HiltTestActivity
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.login.api.Login
import net.kikin.nubecita.feature.onboarding.api.Onboarding
import net.kikin.nubecita.feature.onboarding.impl.testing.FakeUserPreferencesRepository
import net.kikin.nubecita.feature.onboarding.impl.testing.RecordingNavigator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * Instrumentation coverage for [OnboardingScreen]. Verifies the
 * screen-driven user journey end-to-end with the production VM wired
 * to a [FakeUserPreferencesRepository] and a [RecordingNavigator]
 * exposed via [LocalAppNavigator]:
 *
 *  - First page renders the Welcome eyebrow + title; Skip is visible.
 *  - Tap Next FAB → pager advances to the last page; Skip hides;
 *    ExtendedFAB labeled "Get started" replaces the circular FAB; the
 *    back-arrow IconButton becomes visible.
 *  - Tap "Get started" → flag persisted + `replaceTo(Login)`.
 *  - Tap Skip on the first page → flag persisted + `replaceTo(Login)`.
 *  - Persistence failure path → `replaceTo(Login)` still fires
 *    (the screen-side failsafe documented on
 *    [OnboardingEffect.NavigateToLogin]).
 *
 * The MainActivity-side bootstrap routing (combine on
 * `sessionState × hasSeenOnboarding`) is intentionally NOT covered
 * here — that lives at the activity boundary and is exercised by
 * `:app/src/androidTest/`. This test focuses on the screen's own
 * navigation contract.
 */
@HiltAndroidTest
class OnboardingScreenInstrumentationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Inject
    lateinit var preferences: UserPreferencesRepository

    private val navigator = RecordingNavigator(start = Onboarding)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun firstPage_rendersWelcomeCopyAndSkipButton() {
        setOnboardingContent()

        composeTestRule.onNodeWithText(skipLabel()).assertIsDisplayed()
        composeTestRule.onNodeWithText(welcomeEyebrowUppercase()).assertIsDisplayed()
        // The Next FAB exists; the Get-Started ExtendedFAB does not on page 0.
        composeTestRule
            .onNodeWithContentDescription(nextContentDescription())
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(getStartedLabel(), useUnmergedTree = true).assertIsNotDisplayed()
    }

    @Test
    fun tappingNextFab_advancesToLastPage_andSkipHides() {
        setOnboardingContent()

        composeTestRule
            .onNodeWithContentDescription(nextContentDescription())
            .performClick()

        // Wait for the pager's animateScrollToPage to settle and the
        // recomposition to flush. `waitForIdle` returns once Compose
        // reports no pending work.
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(skipLabel()).assertIsNotDisplayed()
        composeTestRule.onNodeWithText(getStartedLabel(), useUnmergedTree = true).assertIsDisplayed()
        // Back arrow is now part of the composition (was a Spacer on page 0).
        composeTestRule
            .onNodeWithContentDescription(backContentDescription())
            .assertIsDisplayed()
    }

    @Test
    fun tappingGetStarted_persistsFlag_andReplacesToLogin() {
        setOnboardingContent()
        composeTestRule
            .onNodeWithContentDescription(nextContentDescription())
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(getStartedLabel(), useUnmergedTree = true).performClick()

        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            navigator.replaceToCalls.isNotEmpty()
        }
        assertEquals(listOf<Any>(Login), navigator.replaceToCalls)
        val fake = preferences as FakeUserPreferencesRepository
        assertTrue(
            "Expected markOnboardingSeen() to be called at least once",
            fake.markCalls > 0,
        )
    }

    @Test
    fun tappingSkip_persistsFlag_andReplacesToLogin() {
        setOnboardingContent()

        composeTestRule.onNodeWithText(skipLabel()).performClick()

        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            navigator.replaceToCalls.isNotEmpty()
        }
        assertEquals(listOf<Any>(Login), navigator.replaceToCalls)
        val fake = preferences as FakeUserPreferencesRepository
        assertTrue(fake.markCalls > 0)
    }

    @Test
    fun persistenceFailure_stillReplacesToLogin_viaScreenFailsafe() {
        val fake = preferences as FakeUserPreferencesRepository
        fake.markFailure = RuntimeException("simulated disk failure")

        setOnboardingContent()

        composeTestRule.onNodeWithText(skipLabel()).performClick()

        // The screen-side LaunchedEffect must still navigate even though
        // the persist threw — without the failsafe, MainActivity's
        // collector would never observe a flag flip and the user would
        // be stranded on Onboarding.
        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            navigator.replaceToCalls.isNotEmpty()
        }
        assertEquals(listOf<Any>(Login), navigator.replaceToCalls)
        assertTrue(fake.markCalls > 0)
    }

    private fun setOnboardingContent() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppNavigator provides navigator) {
                NubecitaTheme {
                    OnboardingScreen()
                }
            }
        }
    }

    private fun skipLabel(): String = string(R.string.onboarding_skip)

    private fun getStartedLabel(): String = string(R.string.onboarding_get_started)

    private fun welcomeEyebrowUppercase(): String = string(R.string.onboarding_page_welcome_eyebrow).uppercase()

    private fun nextContentDescription(): String = string(R.string.onboarding_next_content_description)

    private fun backContentDescription(): String = string(R.string.onboarding_back_content_description)

    private fun string(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
