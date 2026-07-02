package net.kikin.nubecita.feature.widgets.impl.entitlement

import javax.inject.Inject

/**
 * Gate for the configurable (Pro-surface) widget (D-C9). C ships the
 * configurable widget with the gate present but **always allowed**; sub-project
 * D swaps in an `isPro`-backed implementation (+ a paywall upsell state) by
 * rebinding this seam — no change to the widget or config activity.
 */
internal interface WidgetEntitlementGate {
    /** Whether the configurable widget may render its feed for the current user. */
    suspend fun isConfigurableWidgetAllowed(): Boolean
}

/**
 * C's gate: always allowed (the configurable widget ships ungated this change).
 * D replaces this binding with an entitlement-backed one.
 */
internal class AlwaysAllowedWidgetEntitlementGate
    @Inject
    constructor() : WidgetEntitlementGate {
        override suspend fun isConfigurableWidgetAllowed(): Boolean = true
    }
