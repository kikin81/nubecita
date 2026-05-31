package net.kikin.nubecita.core.analytics

import javax.inject.Inject

/**
 * Inert [AnalyticsClient] that drops every call.
 *
 * Lives in `src/main` (not `src/bench`) so unit tests and downstream feature
 * instrumentation tests can reuse it via `@TestInstallIn`. The bench flavor's
 * `AnalyticsModule` binds it so screenshot / baseline-profile / Macrobenchmark
 * runs emit zero analytics and never link Firebase.
 */
class NoOpAnalyticsClient
    @Inject
    constructor() : AnalyticsClient {
        override fun log(event: AnalyticsEvent) = Unit

        override fun setUserProperty(property: UserProperty) = Unit

        override fun logScreen(screen: AnalyticsScreen) = Unit
    }
