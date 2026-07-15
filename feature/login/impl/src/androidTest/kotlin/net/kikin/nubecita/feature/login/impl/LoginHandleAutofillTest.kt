package net.kikin.nubecita.feature.login.impl

import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import net.kikin.nubecita.designsystem.NubecitaTheme
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/**
 * Guards the login handle field's autofill hint (nubecita-p8h4). Renders the
 * stateless [LoginScreen] and asserts the single editable field advertises a
 * `ContentType` to the OS — that's what lets password managers (1Password,
 * Google Autofill) offer the saved Bluesky handle.
 *
 * Refutes the review claim that `ContentType.Username + ContentType.EmailAddress`
 * fails to compile / produces a garbage hint: this test only runs because it
 * compiles, and it confirms the semantics actually attach at runtime. (The
 * concrete hint strings — `["username", "emailAddress"]` — are an internal
 * `Array<String>` on androidx's `AndroidContentType`, so we assert the property
 * is present rather than reflecting into internal library state.)
 */
class LoginHandleAutofillTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun handleField_advertisesAutofillContentType() {
        composeRule.setContent {
            NubecitaTheme {
                LoginScreen(state = LoginState(handle = ""), onEvent = {})
            }
        }

        // The login screen has exactly one editable text field — the handle.
        // .assert(...) and fetchSemanticsNode() below both throw if it's absent.
        val handle = composeRule.onNode(hasSetTextAction())
        handle.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentType))

        val contentType: ContentType? =
            handle.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentType)
        assertNotNull("handle field must advertise a ContentType autofill hint", contentType)
    }
}
