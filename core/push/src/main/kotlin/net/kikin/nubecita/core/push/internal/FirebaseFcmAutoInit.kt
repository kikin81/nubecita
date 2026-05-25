package net.kikin.nubecita.core.push.internal

import com.google.firebase.messaging.FirebaseMessaging
import net.kikin.nubecita.core.push.FcmAutoInit

/**
 * Android-bound [FcmAutoInit]. Flips
 * `FirebaseMessaging.isAutoInitEnabled = true` to opt FCM back in after
 * the `:app` manifest's `firebase_messaging_auto_init_enabled=false`
 * disables auto-init at process load (test-safety; see [FcmAutoInit]'s
 * KDoc).
 */
internal class FirebaseFcmAutoInit : FcmAutoInit {
    override fun enable() {
        FirebaseMessaging.getInstance().isAutoInitEnabled = true
    }
}
