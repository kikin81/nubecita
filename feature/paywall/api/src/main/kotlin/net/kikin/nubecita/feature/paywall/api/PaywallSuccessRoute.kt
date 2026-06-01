package net.kikin.nubecita.feature.paywall.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the post-purchase "thank you" screen
 * (nubecita-ykpc).
 *
 * The paywall **replaces** itself with this route on a fresh successful
 * purchase (never on restore): on `@MainShell` via
 * `MainShellNavState.replaceTop(PaywallSuccessRoute)`, on `@OuterShell` (the
 * fullscreen-video pop-out entry) via `Navigator.goBack()` + `goTo(...)`.
 * Replacing the paywall entry — rather than stacking on top of it — means the
 * screen's **Continue** button and the system **Back** gesture both pop exactly
 * once back to the surface the user came from (the PiP video / Settings),
 * never landing them back on the now-pointless plan picker.
 *
 * Registered on both shells (like [PaywallRoute]) because a purchase can be
 * initiated from either: an in-tab upsell (Settings) on the inner shell, or the
 * fullscreen video pop-out on the outer shell.
 */
@Serializable
data object PaywallSuccessRoute : NavKey
