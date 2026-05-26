package net.kikin.nubecita.feature.settings.impl

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.provider.Settings
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import net.kikin.nubecita.core.testing.android.HiltTestActivity
import net.kikin.nubecita.designsystem.NubecitaTheme
import org.hamcrest.CoreMatchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Asserts the Settings "Notifications" row deep-links into the OS app-
 * notification-settings page with the correct intent shape: action
 * `Settings.ACTION_APP_NOTIFICATION_SETTINGS` and the test app's package
 * name in `Settings.EXTRA_APP_PACKAGE`.
 *
 * Uses Espresso Intents to capture the outgoing `startActivity` call and
 * stubs a no-op result so the OS settings page never actually launches —
 * the test stays self-contained and doesn't depend on the device's
 * settings activity being reachable from the instrumentation process.
 */
@HiltAndroidTest
class SettingsNotificationsRowInstrumentationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        Intents.init()
        // Stub the outgoing intent so the launch is intercepted instead
        // of actually firing the OS settings activity (which would push
        // a foreign activity on top of the test, breaking subsequent
        // tests sharing the same instrumentation process).
        intending(hasAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS))
            .respondWith(ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun notificationsRow_tap_firesAppNotificationSettingsIntent() {
        val notificationsLabel =
            composeTestRule.activity.getString(R.string.settings_notifications_row_label)
        val targetPackage =
            InstrumentationRegistry.getInstrumentation().targetContext.packageName

        composeTestRule.setContent {
            NubecitaTheme(dynamicColor = false) {
                SettingsScreen(onBack = {})
            }
        }

        composeTestRule.onNodeWithText(notificationsLabel).performClick()

        intended(
            allOf(
                hasAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS),
                hasExtra(Settings.EXTRA_APP_PACKAGE, targetPackage),
            ),
        )
    }
}
