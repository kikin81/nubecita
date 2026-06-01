package net.kikin.nubecita.core.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import net.kikin.nubecita.core.analytics.AnalyticsValue.BoolVal
import net.kikin.nubecita.core.analytics.AnalyticsValue.DoubleVal
import net.kikin.nubecita.core.analytics.AnalyticsValue.LongVal
import net.kikin.nubecita.core.analytics.AnalyticsValue.Str
import javax.inject.Inject

/**
 * Firebase-backed [AnalyticsClient]. Translates the neutral [AnalyticsEvent] /
 * [UserProperty] / [AnalyticsScreen] model into Firebase's `Bundle` shape.
 *
 * `FirebaseAnalytics.logEvent` is already non-blocking, so call sites invoke
 * these methods directly — no coroutine/dispatcher. Names are validated under
 * [BuildConfig.DEBUG] so a malformed event fails in debug + unit tests, never
 * silently in production.
 */
internal class FirebaseAnalyticsClient
    @Inject
    constructor(
        private val firebaseAnalytics: FirebaseAnalytics,
    ) : AnalyticsClient {
        override fun log(event: AnalyticsEvent) {
            if (BuildConfig.DEBUG) AnalyticsValidator.requireValid(event)
            firebaseAnalytics.logEvent(event.name, event.params.toBundle())
        }

        override fun setUserProperty(property: UserProperty) {
            if (BuildConfig.DEBUG) AnalyticsValidator.requireValid(property)
            firebaseAnalytics.setUserProperty(property.name, property.value)
        }

        override fun logScreen(screen: AnalyticsScreen) {
            val bundle =
                Bundle(2).apply {
                    putString(FirebaseAnalytics.Param.SCREEN_NAME, screen.screenName)
                    // Set SCREEN_CLASS explicitly. Per Firebase's "Measure
                    // screenviews" doc, when screen_class is omitted Analytics
                    // fills it with the foreground Activity — which, for this
                    // single-Activity Compose app, collapses every screen onto
                    // `MainActivity` (and ComponentActivity / LicenseActivity
                    // during Custom-Tab / billing sub-flows) in GA4's "screen
                    // class" dimension. Using the route name keeps both GA4
                    // screen dimensions clean and per-screen. (Automatic
                    // Activity screen_view reporting is already disabled via the
                    // manifest meta-data — nubecita-049f.2 — so these manual
                    // events are the sole source of screen_view.)
                    putString(FirebaseAnalytics.Param.SCREEN_CLASS, screen.screenName)
                }
            firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
        }
    }

private fun Map<String, AnalyticsValue>.toBundle(): Bundle {
    val bundle = Bundle(size)
    for ((key, value) in this) {
        when (value) {
            is Str -> bundle.putString(key, value.value)
            is LongVal -> bundle.putLong(key, value.value)
            is DoubleVal -> bundle.putDouble(key, value.value)
            is BoolVal -> bundle.putLong(key, if (value.value) 1L else 0L)
        }
    }
    return bundle
}
