package net.kikin.nubecita.feature.chats.impl.worker

import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies [MessagesNotificationChannelInstaller] against the real Android
 * `NotificationManager`. Pure-JVM unit tests can't cover this — channel
 * persistence lives in framework code.
 *
 * Regression guard for nubecita-29rw: the "Messages" channel must exist after
 * an eager startup install, NOT only after the first DM notification posts.
 * Before the fix the channel was created lazily inside
 * [MessagingStyleDmNotifier.notify], so a fresh install / Clear Data left it
 * absent from Settings > Notifications until a new DM arrived.
 */
@RunWith(AndroidJUnit4::class)
class MessagesNotificationChannelInstallerInstrumentationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val managerCompat get() = NotificationManagerCompat.from(context)

    @After
    fun tearDown() {
        // Channels persist for the lifetime of the test app's install; delete
        // the one this test created so a re-run starts from a clean slate.
        managerCompat.deleteNotificationChannel(ChatNotificationIds.CHANNEL_ID)
    }

    @Test
    fun install_creates_the_messages_channel_eagerly() {
        // Precondition: the channel does not exist before install.
        managerCompat.deleteNotificationChannel(ChatNotificationIds.CHANNEL_ID)
        assertNull(
            "messages channel must not exist before install",
            managerCompat.getNotificationChannelCompat(ChatNotificationIds.CHANNEL_ID),
        )

        MessagesNotificationChannelInstaller().install(context)

        val channel = managerCompat.getNotificationChannelCompat(ChatNotificationIds.CHANNEL_ID)
        assertNotNull("expected the 'messages' channel to exist after install", channel)
        assertEquals(
            "messages channel importance must be HIGH",
            NotificationManagerCompat.IMPORTANCE_HIGH,
            channel!!.importance,
        )
        // androidx.core 1.19 rejects a blank channel name; a blank name would
        // also render an unusable Settings entry (nubecita-yvod neighbourhood).
        assertTrue("messages channel name must not be blank", channel.name?.isNotBlank() == true)
    }

    @Test
    fun install_is_idempotent_across_repeated_calls() {
        val installer = MessagesNotificationChannelInstaller()

        installer.install(context)
        installer.install(context)

        val matching =
            managerCompat.notificationChannelsCompat.filter { it.id == ChatNotificationIds.CHANNEL_ID }
        assertEquals("redundant install must not duplicate the channel", 1, matching.size)
    }
}
