package net.kikin.nubecita.feature.paywall.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the Nubecita Pro paywall.
 *
 * Pushed onto MainShell's inner back stack from any non-Pro upsell
 * surface via `navState.add(PaywallRoute(source))` — the fullscreen-video
 * pop-out button (nubecita-q5ge.8) and the Settings "Nubecita Pro"
 * row (nubecita-q5ge.10) are the first two callers. The paywall pops
 * itself off the same inner stack on dismiss (close affordance, or a
 * completed purchase/restore that grants Pro).
 *
 * `@MainShell`, not `@OuterShell`: the paywall is a tab sub-route, so
 * its entry provider is collected by the inner `NavDisplay` and it
 * shows over the active tab (the bottom bar is suppressed for
 * sub-routes on phones — see `MainShell`'s sub-route detection).
 *
 * [source] tags which upsell surface opened it so the paywall view can be
 * attributed in analytics (PiP pop-out vs Settings vs Supporter badge). It
 * defaults to [PaywallSource.Other] so an untagged caller still compiles and
 * degrades to a neutral bucket rather than mis-attributing.
 */
@Serializable
data class PaywallRoute(
    val source: PaywallSource = PaywallSource.Other,
) : NavKey

/**
 * The upsell surface that opened the paywall. Carried on [PaywallRoute] (hence
 * in `:api`, serializable) and mapped to the analytics event's source at the log
 * site — mirroring how the plan id is mapped, so `:api` stays analytics-free.
 */
@Serializable
enum class PaywallSource {
    Pip,
    Settings,
    SupporterBadge,
    Other,
}
