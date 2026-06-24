package net.kikin.nubecita.core.push.internal

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import net.kikin.nubecita.core.push.FcmTokenProvider

/**
 * Android-bound [FcmTokenProvider] backed by `FirebaseMessaging.getInstance().token`.
 *
 * `FirebaseMessaging.token` returns a `Task<String>` that resolves once the
 * Firebase SDK has a fresh registration token from the IID server. The
 * coroutine-play-services `Task.await()` extension suspends the calling
 * coroutine until the task completes; failures surface as an exception that
 * the surrounding `runCatchingExceptCancellation` in
 * [net.kikin.nubecita.core.push.PushRegistrationCoordinator.onSessionEstablished]
 * captures into `Result.failure` and pushes through the backoff loop.
 */
internal class FirebaseFcmTokenProvider : FcmTokenProvider {
    // FirebaseMessaging.getToken() (the `token` accessor) is @Deprecated in favor of
    // Firebase's V1 registration model (register() + onNewToken), which has no
    // synchronous Task<String> equivalent (register() returns Task<Void>). Suppressed
    // here; the real migration off the synchronous fetch is tracked in nubecita-8pd0.
    @Suppress("DEPRECATION")
    override suspend fun current(): String = FirebaseMessaging.getInstance().token.await()
}
