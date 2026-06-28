package net.kikin.nubecita.core.testing

import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.AnalyticsEvent
import net.kikin.nubecita.core.analytics.AnalyticsScreen
import net.kikin.nubecita.core.analytics.UserProperty

/**
 * Recording [AnalyticsClient] fake for tests — captures everything logged so a
 * test can assert the exact events, screens, and user properties emitted.
 *
 * The three lists are independent; a test reads whichever it needs (most assert
 * [events]; screen-view tests read [screens]; user-property tests read
 * [properties]). Shared across modules from `:core:testing` so each feature's
 * unit tests don't re-declare their own copy.
 */
class RecordingAnalyticsClient : AnalyticsClient {
    val events = mutableListOf<AnalyticsEvent>()
    val screens = mutableListOf<AnalyticsScreen>()
    val properties = mutableListOf<UserProperty>()

    override fun log(event: AnalyticsEvent) {
        events += event
    }

    override fun setUserProperty(property: UserProperty) {
        properties += property
    }

    override fun logScreen(screen: AnalyticsScreen) {
        screens += screen
    }
}
