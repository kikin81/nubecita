package net.kikin.nubecita.wear.launch

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import net.kikin.nubecita.wear.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
                WearLaunchTarget.Landing to "landing",
                WearLaunchTarget.Feed to "feed",
                WearLaunchTarget.Messages to "messages",
                WearLaunchTarget.NewPost to "new_post",
            )
        expected.forEach { (target, extra) ->
            val intent = shadowOf(launchIntent(context, target)).savedIntent

            assertEquals(MainActivity::class.java.name, intent.component?.className)
            assertEquals(extra, intent.getStringExtra(EXTRA_LAUNCH_TARGET))
            val freshTask = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            assertEquals("$target flags", freshTask, intent.flags and freshTask)
        }
    }

    @Test
    fun eachTargetRoundTripsThroughItsExtra() {
        WearLaunchTarget.entries.forEach { target ->
            val intent = shadowOf(launchIntent(context, target)).savedIntent

            assertEquals(target, launchTargetOf(intent.getStringExtra(EXTRA_LAUNCH_TARGET), intent.action, restoring = false))
        }
    }

    @Test
    fun everyTargetIsShapedLikeTheLauncherIntent() {
        // Android compares a launcher start with the intent that created the
        // task. When they differ it puts a second MainActivity on top.
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClass(context, MainActivity::class.java)

        WearLaunchTarget.entries.forEach { target ->
            val intent = shadowOf(launchIntent(context, target)).savedIntent

            assertTrue("$target: $intent", intent.filterEquals(launcher))
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
