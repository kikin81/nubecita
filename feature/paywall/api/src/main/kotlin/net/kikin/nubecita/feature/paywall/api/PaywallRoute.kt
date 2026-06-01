package net.kikin.nubecita.feature.paywall.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the Nubecita Pro paywall.
 *
 * Pushed onto MainShell's inner back stack from any non-Pro upsell
 * surface via `navState.add(PaywallRoute)` — the fullscreen-video
 * pop-out button (nubecita-q5ge.8) and the Settings "Nubecita Pro"
 * row (nubecita-q5ge.10) are the first two callers. The paywall pops
 * itself off the same inner stack on dismiss (close affordance, or a
 * completed purchase/restore that grants Pro).
 *
 * `@MainShell`, not `@OuterShell`: the paywall is a tab sub-route, so
 * its entry provider is collected by the inner `NavDisplay` and it
 * shows over the active tab (the bottom bar is suppressed for
 * sub-routes on phones — see `MainShell`'s sub-route detection).
 */
@Serializable
data object PaywallRoute : NavKey
