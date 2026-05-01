package net.kikin.nubecita.feature.login.impl

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.platform.app.InstrumentationRegistry
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
 *
 * The `Intent.ACTION_VIEW` is stubbed via Espresso `intending(...)` so
 * the actual browser launch is intercepted — without the stub, the
 * Custom Tab intent would resolve to a real browser on the emulator
 * and could pollute test isolation or flake when no browser is
 * installed.
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

        // Intercept Custom Tab launches so the real browser doesn't open
        // on the emulator. The intent is still recorded for intended(...).
        intending(hasAction(Intent.ACTION_VIEW))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
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

        // Resolve the button text from the module's string resource so
        // copy / localization changes don't break the test.
        val submitText =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .getString(R.string.login_submit)
        composeTestRule.onNodeWithText(submitText).performClick()

        // The submit click → ViewModel.submitLogin() → coroutine →
        // sendEffect(LaunchCustomTab) → LaunchedEffect collector →
        // CustomTabsIntent.launchUrl(...) chain is asynchronous. Poll
        // intended(...) under composeTestRule.waitUntil so the
        // assertion succeeds as soon as the intent has been recorded
        // rather than failing on a fixed instant.
        composeTestRule.waitUntil(timeoutMillis = INTENT_WAIT_TIMEOUT_MILLIS) {
            runCatching {
                intended(
                    allOf(
                        hasAction(Intent.ACTION_VIEW),
                        hasData(Uri.parse(FakeAuthRepository.DEFAULT_AUTHORIZATION_URL)),
                    ),
                )
            }.isSuccess
        }
    }

    private companion object {
        const val VALID_HANDLE = "alice.bsky.social"
        const val INTENT_WAIT_TIMEOUT_MILLIS = 5_000L
    }
}
