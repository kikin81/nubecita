package net.kikin.nubecita.core.analytics

import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Firebase-backed [AnalyticsInstanceIdProvider]. Bridges
 * `FirebaseAnalytics.appInstanceId` (a `Task<String>`) to a suspend call via
 * the `kotlinx-coroutines-play-services` `Task.await()` extension — the same
 * pattern `:core:push` uses for the FCM token (`FirebaseFcmTokenProvider`).
 *
 * A failed lookup (e.g. analytics collection disabled) is mapped to `null` so
 * the caller simply skips the RevenueCat link rather than crashing startup.
 */
internal class FirebaseAnalyticsInstanceIdProvider
    @Inject
    constructor(
        private val firebaseAnalytics: FirebaseAnalytics,
    ) : AnalyticsInstanceIdProvider {
        override suspend fun appInstanceId(): String? =
            try {
                firebaseAnalytics.appInstanceId.await()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // Lookup failed (analytics disabled / no consent) — skip the link.
                null
            }
    }
