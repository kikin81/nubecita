package net.kikin.nubecita.feature.login.impl

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.intent.rule.IntentsRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import net.kikin.nubecita.core.testing.android.HiltTestActivity
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.login.impl.testing.FakeAuthRepository
import org.hamcrest.Matchers.allOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Reference instrumentation test for `:feature:login:impl`. Verifies that
 * a user typing a handle and tapping the submit button triggers the
 * Custom Tab launch path (i.e. an `Intent.ACTION_VIEW` with the OAuth
 * authorization URL as data).
 *
 * The OAuth round-trip (Custom Tab → provider → redirect → token
 * exchange → SignedIn) is intentionally NOT covered here — Custom Tab
 * can't be driven reliably from instrumentation, and the post-callback
 * path is exercised in `:core:auth/src/androidTest/` (nubecita-z9d) on
 * top of a stateful refresh fixture.
 *
 * `AuthRepository` and `OAuthRedirectBroker` are faked via Hilt's
 * `@TestInstallIn(replaces = [AuthBindingsModule::class])` —
 * `FakeAuthRepository.beginLogin(...)` returns a fixed authorization
 * URL that the test asserts the launched intent points at.
 */
@HiltAndroidTest
class LoginScreenInstrumentationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    // IntentsRule wraps Espresso's Intents.init() / Intents.release()
    // around the test so intended(...) and intending(...) work without
    // manual lifecycle.
    @get:Rule(order = 2)
    val intentsRule = IntentsRule()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun submittingHandle_launchesCustomTabIntentForAuthorizationUrl() {
        composeTestRule.setContent {
            NubecitaTheme {
                LoginScreen()
            }
        }

        // The screen has exactly one editable text field (the handle
        // input). hasSetTextAction() resolves to it without depending
        // on label/placeholder string values.
        composeTestRule.onNode(hasSetTextAction()).performTextInput(VALID_HANDLE)

        composeTestRule.onNodeWithText(SUBMIT_BUTTON_TEXT).performClick()

        // CustomTabsIntent.launchUrl(context, uri) fires
        // Intent.ACTION_VIEW with the URI as data. Match on action +
        // data only — CustomTabsIntent attaches additional bundle
        // extras (browser-helper flags) we don't care about asserting.
        intended(
            allOf(
                hasAction(Intent.ACTION_VIEW),
                hasData(Uri.parse(FakeAuthRepository.DEFAULT_AUTHORIZATION_URL)),
            ),
        )
    }

    private companion object {
        const val VALID_HANDLE = "alice.bsky.social"
        const val SUBMIT_BUTTON_TEXT = "Sign in with Bluesky"
    }
}
