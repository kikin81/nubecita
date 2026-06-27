package net.kikin.nubecita.feature.postdetail.impl

import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.AnalyticsEvent
import net.kikin.nubecita.core.analytics.AnalyticsScreen
import net.kikin.nubecita.core.analytics.UserProperty

/**
 * Recording [AnalyticsClient] fake — captures logged events so a test can assert
 * the `InteractPost` events fired on like/repost.
 */
internal class RecordingAnalyticsClient : AnalyticsClient {
    val events = mutableListOf<AnalyticsEvent>()

    override fun log(event: AnalyticsEvent) {
        events += event
    }

    override fun setUserProperty(property: UserProperty) = Unit

    override fun logScreen(screen: AnalyticsScreen) = Unit
}
