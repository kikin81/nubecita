package net.kikin.nubecita.feature.login.impl

import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.kikin.nubecita.designsystem.NubecitaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Contract: the login handle field advertises a `username` + `emailAddress`
 * autofill hint to the OS, so password managers (1Password, Google Autofill)
 * offer the saved Bluesky handle. Handles (`you.bsky.social`) are dotted and
 * email-ish, so both hint types are declared to widen manager match.
 *
 * The test renders the stateless [LoginScreen], confirms the single editable
 * field carries a `ContentType`, and asserts the exact hint set.
 */
@RunWith(AndroidJUnit4::class)
class LoginHandleAutofillTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun handleField_advertisesUsernameAndEmailAutofillHints() {
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

        // Assert the exact hint values, not just presence. `ContentType` on
        // Android is backed by a private `AndroidContentType` with a
        // `Set<String>` of platform autofill-hint constants; it has no public
        // accessor and uses identity equality (so a direct `==` against
        // `ContentType.Username + ContentType.EmailAddress` can't work — those
        // are distinct instances). Reflect the hint set and compare its values.
        assertEquals(
            setOf("username", "emailAddress"),
            contentType!!.androidAutofillHints(),
        )
    }
}

/**
 * Read the platform autofill-hint strings backing a [ContentType] via
 * reflection over androidx's internal `AndroidContentType.androidAutofillHints`.
 * Fragile by construction (internal shape) — but it fails loudly if the shape
 * changes, which is the correct signal for a test asserting the exact hints.
 */
@Suppress("UNCHECKED_CAST")
private fun ContentType.androidAutofillHints(): Set<String> {
    val getter = javaClass.getDeclaredMethod("getAndroidAutofillHints").apply { isAccessible = true }
    return getter.invoke(this) as Set<String>
}
