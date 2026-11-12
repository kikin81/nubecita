package net.kikin.nubecita.wear.launch

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import net.kikin.nubecita.wear.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The PendingIntents the complication and launcher widget fire: each opens
 * MainActivity at its target in a fresh task, and they never collapse into
 * one (which would send every tile to the same place).
 */
@RunWith(RobolectricTestRunner::class)
// A plain Application: the Hilt app would bootstrap the whole graph for an intent check.
@Config(sdk = [35], application = Application::class)
class LaunchIntentTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun eachTargetOpensMainActivityInAFreshTask() {
        val expected =
            mapOf(
                WearLaunchTarget.Feed to MainActivity.ACTION_OPEN_FEED,
                WearLaunchTarget.Messages to MainActivity.ACTION_OPEN_MESSAGES,
                WearLaunchTarget.NewPost to MainActivity.ACTION_NEW_POST,
            )
        expected.forEach { (target, action) ->
            val intent = shadowOf(launchIntent(context, target)).savedIntent

            assertEquals(MainActivity::class.java.name, intent.component?.className)
            assertEquals(action, intent.action)
            val freshTask = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            assertEquals("$target flags", freshTask, intent.flags and freshTask)
        }
    }

    @Test
    fun targetsGetDistinctPendingIntents() {
        val feed = launchIntent(context, WearLaunchTarget.Feed)
        val messages = launchIntent(context, WearLaunchTarget.Messages)
        val newPost = launchIntent(context, WearLaunchTarget.NewPost)

        assertNotEquals(feed, messages)
        assertNotEquals(feed, newPost)
        assertNotEquals(messages, newPost)
    }
}
