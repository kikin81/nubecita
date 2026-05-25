package net.kikin.nubecita.core.push

import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies [NotificationChannelInstaller] against the real Android
 * `NotificationManager`. Pure-JVM unit tests can't cover this — channel
 * persistence + importance enforcement live in framework code.
 */
@RunWith(AndroidJUnit4::class)
class NotificationChannelInstallerInstrumentationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val managerCompat get() = NotificationManagerCompat.from(context)

    @After
    fun tearDown() {
        // Channels persist across instrumentation runs (the OS keeps them
        // for the lifetime of the test app's install). Delete the ones this
        // test created so a re-run starts from a clean slate.
        EXPECTED_IMPORTANCE.keys.forEach(managerCompat::deleteNotificationChannel)
    }

    @Test
    fun install_creates_all_ten_channels_with_the_expected_importance_tier() {
        NotificationChannelInstaller().install(context)

        val channels = managerCompat.notificationChannelsCompat.associateBy { it.id }
        EXPECTED_IMPORTANCE.forEach { (id, expectedImportance) ->
            val channel = channels[id]
            assertNotNull("expected channel '$id' to exist", channel)
            assertEquals(
                "channel '$id' expected importance $expectedImportance",
                expectedImportance,
                channel!!.importance,
            )
        }
    }

    @Test
    fun install_is_idempotent_across_repeated_calls() {
        val installer = NotificationChannelInstaller()

        installer.install(context)
        val firstSnapshotIds =
            managerCompat.notificationChannelsCompat
                .map { it.id }
                .filter { it in EXPECTED_IMPORTANCE.keys }
                .toSet()

        installer.install(context)
        val secondSnapshotIds =
            managerCompat.notificationChannelsCompat
                .map { it.id }
                .filter { it in EXPECTED_IMPORTANCE.keys }
                .toSet()

        assertEquals(
            "channel set must be byte-identical after a redundant install",
            firstSnapshotIds,
            secondSnapshotIds,
        )
        assertEquals(
            "channel count must remain at 10 after a redundant install",
            10,
            secondSnapshotIds.size,
        )
    }

    private companion object {
        // Wire-reason channel IDs → expected NotificationManagerCompat importance.
        // Mirrors the three-tier mapping in NotificationChannelInstaller +
        // design.md. The test is paranoid-explicit so a future refactor that
        // accidentally flips a tier (e.g. demotes `verified` to LOW) fails
        // here with the exact channel name.
        val EXPECTED_IMPORTANCE: Map<String, Int> =
            mapOf(
                NotificationChannelInstaller.CHANNEL_REPLY to NotificationManagerCompat.IMPORTANCE_HIGH,
                NotificationChannelInstaller.CHANNEL_MENTION to NotificationManagerCompat.IMPORTANCE_HIGH,
                NotificationChannelInstaller.CHANNEL_QUOTE to NotificationManagerCompat.IMPORTANCE_HIGH,
                NotificationChannelInstaller.CHANNEL_VERIFIED to NotificationManagerCompat.IMPORTANCE_HIGH,
                NotificationChannelInstaller.CHANNEL_UNVERIFIED to NotificationManagerCompat.IMPORTANCE_HIGH,
                NotificationChannelInstaller.CHANNEL_FOLLOW to NotificationManagerCompat.IMPORTANCE_DEFAULT,
                NotificationChannelInstaller.CHANNEL_LIKE to NotificationManagerCompat.IMPORTANCE_LOW,
                NotificationChannelInstaller.CHANNEL_LIKE_VIA_REPOST to NotificationManagerCompat.IMPORTANCE_LOW,
                NotificationChannelInstaller.CHANNEL_REPOST to NotificationManagerCompat.IMPORTANCE_LOW,
                NotificationChannelInstaller.CHANNEL_REPOST_VIA_REPOST to NotificationManagerCompat.IMPORTANCE_LOW,
            )
    }
}
