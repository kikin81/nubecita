package net.kikin.nubecita.core.analytics

/**
 * Provider-agnostic analytics sink for the whole app.
 *
 * Features depend on this interface only — never on Firebase types — so the
 * analytics backend can be swapped (Firebase → PostHog/Amplitude/self-hosted)
 * by changing a single module instead of every call site.
 *
 * There is intentionally **no** `log(name: String, params: Map<...>)` escape
 * hatch: callers pass a typed [AnalyticsEvent] / [UserProperty] / [AnalyticsScreen]
 * whose params are enums / booleans / bucketed counts, which makes PII leakage
 * structurally impossible and keeps us under GA4's caps by construction.
 *
 * All three calls are fire-and-forget — implementations must never block an MVI
 * flow or throw into the UI.
 */
interface AnalyticsClient {
    fun log(event: AnalyticsEvent)

    fun setUserProperty(property: UserProperty)

    fun logScreen(screen: AnalyticsScreen)
}
