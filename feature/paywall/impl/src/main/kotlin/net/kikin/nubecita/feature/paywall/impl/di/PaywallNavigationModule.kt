package net.kikin.nubecita.feature.paywall.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.LocalAppNavigator
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.core.common.navigation.OuterShell
import net.kikin.nubecita.feature.paywall.api.PaywallRoute
import net.kikin.nubecita.feature.paywall.api.PaywallSuccessRoute
import net.kikin.nubecita.feature.paywall.impl.PaywallScreen
import net.kikin.nubecita.feature.paywall.impl.PaywallSuccessScreen

/**
 * `@MainShell` provider for the [PaywallRoute] sub-route. Pushed onto the
 * inner back stack from non-Pro upsell surfaces (the fullscreen-video
 * pop-out button — nubecita-q5ge.8 — and the Settings Pro row —
 * nubecita-q5ge.10) via `navState.add(PaywallRoute(source))`; the close affordance
 * and a Pro-granting purchase/restore pop the same inner stack via
 * [net.kikin.nubecita.core.common.navigation.MainShellNavState.removeLast].
 */
@Module
@InstallIn(SingletonComponent::class)
internal object PaywallNavigationModule {
    @Provides
    @IntoSet
    @MainShell
    fun providePaywallEntries(): EntryProviderInstaller =
        {
            entry<PaywallRoute> { route ->
                val navState = LocalMainShellNavState.current
                PaywallScreen(
                    onDismiss = { navState.removeLast() },
                    // Fresh purchase: replace the paywall with the thank-you
                    // screen so Continue/Back both pop once to the prior surface.
                    onPurchaseSuccess = { navState.replaceTop(PaywallSuccessRoute) },
                    source = route.source,
                )
            }
            entry<PaywallSuccessRoute> {
                val navState = LocalMainShellNavState.current
                PaywallSuccessScreen(onContinue = { navState.removeLast() })
            }
        }

    /**
     * `@OuterShell` registration of the SAME [PaywallRoute] on the outer
     * `NavDisplay`. This is a deliberate exception to CLAUDE.md's "tab-related →
     * `@MainShell`" rule: the fullscreen video player is an `@OuterShell` route,
     * so its non-Pro pop-out upsell (nubecita-q5ge.8) can't reach
     * `LocalMainShellNavState`. It instead pushes `PaywallRoute` onto the outer
     * stack via `LocalAppNavigator`, showing the paywall *over* the video
     * (preserving the user's video context); [onDismiss] pops back to the player
     * on close or a completed purchase. `PaywallScreen` has no MainShell
     * dependency, so it renders identically in either shell.
     *
     * The two providers never collide: each `NavDisplay` only collects its own
     * qualifier's set and only renders keys present in its own back stack.
     */
    @Provides
    @IntoSet
    @OuterShell
    fun provideOuterPaywallEntries(): EntryProviderInstaller =
        {
            entry<PaywallRoute> { route ->
                val navigator = LocalAppNavigator.current
                PaywallScreen(
                    onDismiss = { navigator.goBack() },
                    // Replace the paywall with the thank-you screen: pop the
                    // paywall, push success (the outer Navigator has no
                    // replace-top, so goBack + goTo is the equivalent).
                    onPurchaseSuccess = {
                        navigator.goBack()
                        navigator.goTo(PaywallSuccessRoute)
                    },
                    source = route.source,
                )
            }
            entry<PaywallSuccessRoute> {
                val navigator = LocalAppNavigator.current
                PaywallSuccessScreen(onContinue = { navigator.goBack() })
            }
        }
}
