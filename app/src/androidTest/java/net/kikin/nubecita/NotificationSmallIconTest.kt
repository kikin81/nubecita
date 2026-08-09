package net.kikin.nubecita

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.kikin.nubecita.data.ChatsAppModule
import net.kikin.nubecita.data.PushAppModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins the status-bar small icon across **both** notification paths.
 *
 * Nubecita posts notifications two different ways, and only one of them
 * runs our code:
 *
 * - **We render it.** `MessagingStyleDmNotifier` (DMs) and
 *   `PushNotificationBuilder` (data-only pushes) call `setSmallIcon` with
 *   an injected resource id.
 * - **Firebase renders it.** Bluesky's push gateway sends payloads carrying
 *   a `notification` block; while the app is backgrounded the system
 *   displays those itself and `NubecitaFcmService.onMessageReceived` is
 *   never called. That path takes its icon from the
 *   `com.google.firebase.messaging.default_notification_icon` manifest
 *   meta-data and, when the key is absent, silently falls back to
 *   `android:icon` — the adaptive launcher icon, which renders visibly
 *   shrunken at 24dp because of its safe-zone margin.
 *
 * That fallback is what shipped as `nubecita-4gqw`: a DM and a follow
 * notification sitting in the same status bar with different marks. The
 * meta-data assertion below is the regression guard — a unit test can't
 * cover it, because the value only exists in the merged manifest.
 */
@RunWith(AndroidJUnit4::class)
class NotificationSmallIconTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * `ApplicationInfo.metaData` is a platform type and is genuinely `null`
     * when the manifest declares no `<meta-data>` at all. Coercing that to an
     * empty bundle would report it as "the icon key is missing", and letting
     * it NPE would report nothing useful — either way the diagnostic, which
     * is this test's whole reason to exist, gets worse. Fail loudly and
     * distinctly instead.
     */
    private fun applicationMetaData(): Bundle =
        requireNotNull(
            context.packageManager
                .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                .metaData,
        ) {
            "The merged manifest declares no <meta-data> at all — not just a missing notification " +
                "icon key. Something removed the whole block from AndroidManifest.xml."
        }

    @Test
    fun firebaseRenderedNotifications_useTheNotificationDrawable_notTheLauncherIcon() {
        val declared = applicationMetaData().getInt(FIREBASE_DEFAULT_NOTIFICATION_ICON, 0)

        assertNotEquals(
            "com.google.firebase.messaging.default_notification_icon is missing from the merged " +
                "manifest. Firebase-rendered notifications will fall back to android:icon and " +
                "appear shrunken next to our own notifications (nubecita-4gqw).",
            0,
            declared,
        )
        assertEquals(
            "Firebase's default notification icon must be the dedicated notification asset.",
            R.drawable.ic_stat_nubecita,
            declared,
        )
    }

    // No separate "icon is not ic_launcher" test. It looks like it would pin
    // the historical regression, but it PASSES in exactly that broken state:
    // with the meta-data absent, getInt returns the 0 default, and 0 is
    // trivially != R.mipmap.ic_launcher. Verified by deleting the meta-data
    // and re-running. The assertion above subsumes it anyway — asserting the
    // value equals ic_stat_nubecita implies it is neither 0 nor the launcher.

    /**
     * The manifest value and the two Hilt-provided ids are three independent
     * declarations of the same thing. Nothing links them at compile time, so
     * changing one and forgetting the others is exactly how the icons drift
     * apart again — assert all three agree.
     */
    @Test
    fun allThreeSmallIconDeclarations_agree() {
        val fromManifest = applicationMetaData().getInt(FIREBASE_DEFAULT_NOTIFICATION_ICON, 0)

        assertEquals(
            "push small icon differs from the manifest",
            fromManifest,
            PushAppModule.providePushSmallIconRes(),
        )
        assertEquals(
            "DM small icon differs from the manifest",
            fromManifest,
            ChatsAppModule.provideDmNotificationSmallIconRes(),
        )
    }

    private companion object {
        const val FIREBASE_DEFAULT_NOTIFICATION_ICON =
            "com.google.firebase.messaging.default_notification_icon"
    }
}
