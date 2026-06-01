package net.kikin.nubecita.core.analytics

import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Firebase-backed [AnalyticsInstanceIdProvider]. Wraps
 * `FirebaseAnalytics.appInstanceId` (a `Task<String>`) as a suspend call.
 *
 * Suspends via [suspendCancellableCoroutine] rather than pulling in
 * `kotlinx-coroutines-play-services` for a single `Task.await()`. A failed
 * lookup (e.g. analytics collection disabled) resolves to `null` so the caller
 * simply skips the RevenueCat link rather than crashing startup.
 */
internal class FirebaseAnalyticsInstanceIdProvider
    @Inject
    constructor(
        private val firebaseAnalytics: FirebaseAnalytics,
    ) : AnalyticsInstanceIdProvider {
        override suspend fun appInstanceId(): String? =
            suspendCancellableCoroutine { continuation ->
                firebaseAnalytics.appInstanceId
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resume(null) }
            }
    }
