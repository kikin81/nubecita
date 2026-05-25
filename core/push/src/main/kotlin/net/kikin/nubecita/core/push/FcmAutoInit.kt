package net.kikin.nubecita.core.push

/**
 * Injection seam for flipping Firebase Messaging's `isAutoInitEnabled` flag
 * back on at the moment the push coordinator confirms the app is running
 * under the real Hilt graph (not `HiltTestApplication`).
 *
 * The `:app` manifest sets `firebase_messaging_auto_init_enabled=false`
 * so [com.google.firebase.provider.FirebaseInitProvider] — which runs at
 * process load, before [net.kikin.nubecita.NubecitaApplication.onCreate] —
 * doesn't start [NubecitaFcmService] before the test runner has a chance
 * to swap in [HiltTestApplication]. Without the guard, every instrumented
 * test crashes inside `Hilt_NubecitaFcmService.onCreate` with "The
 * component was not created" the moment Firebase pings the service.
 *
 * Lives behind this seam so pure-JVM unit tests for
 * [PushRegistrationCoordinator] don't transitively call
 * [com.google.firebase.messaging.FirebaseMessaging.getInstance], which
 * touches `android.os.Process.myPid` and dies under the AGP unit-test
 * stub framework.
 */
interface FcmAutoInit {
    fun enable()
}
