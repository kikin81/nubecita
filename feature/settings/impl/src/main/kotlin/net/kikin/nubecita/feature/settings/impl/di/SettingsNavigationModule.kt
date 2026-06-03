package net.kikin.nubecita.feature.settings.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.core.common.navigation.adaptiveDialog
import net.kikin.nubecita.feature.settings.api.Settings
import net.kikin.nubecita.feature.settings.impl.SettingsScreen

/**
 * `@MainShell` provider for the [Settings] sub-route. Pushed onto the
 * inner back stack from the You-tab Profile via
 * `navState.add(Settings)`; the back arrow inside [SettingsScreen]
 * pops the same inner stack.
 *
 * Tagged [adaptiveDialog] so Settings presents full-screen on Compact and
 * as a centered dialog on Medium/Expanded. Sub-routes opened from here that
 * are also tagged `adaptiveDialog` (e.g. About) coalesce into the *same*
 * dialog and swap content on tablet; on phone they push as full-screen pages.
 *
 * Graduated out of `:feature:profile:impl/ProfileNavigationModule` in
 * nubecita-77l. Section sub-routes added by post-77l tasks (Push
 * notifications deep-dive, etc.) declare their providers in this
 * module too.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object SettingsNavigationModule {
    @Provides
    @IntoSet
    @MainShell
    fun provideSettingsEntries(): EntryProviderInstaller =
        {
            entry<Settings>(metadata = adaptiveDialog()) {
                val navState = LocalMainShellNavState.current
                SettingsScreen(
                    onBack = { navState.removeLast() },
                    // Pushes sub-routes (today: PaywallRoute from the Pro upsell
                    // row) onto the same inner back stack. The VM emits the
                    // NavKey via an effect; the screen forwards it here.
                    onNavigateTo = { navState.add(it) },
                )
            }
        }
}
