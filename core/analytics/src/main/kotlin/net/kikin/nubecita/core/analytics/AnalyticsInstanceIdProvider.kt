package net.kikin.nubecita.core.analytics

import javax.inject.Inject

/**
 * Resolves the analytics backend's per-install client id (Firebase's
 * *app-instance id* / GA4 client id).
 *
 * Kept separate from [AnalyticsClient] so that interface stays a pure
 * fire-and-forget sink — this is the one analytics call that suspends (the
 * Firebase id arrives via a `Task`). It exists so the RevenueCat↔Firebase link
 * can be wired at startup *without* leaking a Firebase type past
 * `:core:analytics`: the composition root reads the id through this interface
 * and hands it to `:core:billing`'s link seam.
 */
interface AnalyticsInstanceIdProvider {
    /** The app-instance id, or `null` when unavailable (analytics disabled, bench flavor, or the lookup fails). */
    suspend fun appInstanceId(): String?
}

/**
 * Inert [AnalyticsInstanceIdProvider] returning `null`. Bound by the bench
 * flavor (which never links Firebase) and reusable by tests — mirrors
 * [NoOpAnalyticsClient].
 */
class NoOpAnalyticsInstanceIdProvider
    @Inject
    constructor() : AnalyticsInstanceIdProvider {
        override suspend fun appInstanceId(): String? = null
    }
