package net.kikin.nubecita.feature.search.impl

import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.AnalyticsEvent
import net.kikin.nubecita.core.analytics.AnalyticsScreen
import net.kikin.nubecita.core.analytics.UserProperty

/**
 * Shared recording [AnalyticsClient] fake for the search-module unit tests
 * (Posts / Actors / Feeds VM tests). Captures logged events so a test can
 * assert the exact `search_perform` / `view_feed` sequence.
 */
internal class RecordingAnalyticsClient : AnalyticsClient {
    val events = mutableListOf<AnalyticsEvent>()

    override fun log(event: AnalyticsEvent) {
        events += event
    }

    override fun setUserProperty(property: UserProperty) = Unit

    override fun logScreen(screen: AnalyticsScreen) = Unit
}
