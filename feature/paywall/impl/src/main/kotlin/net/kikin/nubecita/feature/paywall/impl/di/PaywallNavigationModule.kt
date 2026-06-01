package net.kikin.nubecita.feature.paywall.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.paywall.api.PaywallRoute
import net.kikin.nubecita.feature.paywall.impl.PaywallScreen

/**
 * `@MainShell` provider for the [PaywallRoute] sub-route. Pushed onto the
 * inner back stack from non-Pro upsell surfaces (the fullscreen-video
 * pop-out button — nubecita-q5ge.8 — and the Settings Pro row —
 * nubecita-q5ge.10) via `navState.add(PaywallRoute)`; the close affordance
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
            entry<PaywallRoute> {
                val navState = LocalMainShellNavState.current
                PaywallScreen(onDismiss = { navState.removeLast() })
            }
        }
}
