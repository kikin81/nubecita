package net.kikin.nubecita.feature.settings.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.settings.api.Settings
import net.kikin.nubecita.feature.settings.impl.SettingsScreen

/**
 * `@MainShell` provider for the [Settings] sub-route. Pushed onto the
 * inner back stack from the You-tab Profile via
 * `navState.add(Settings)`; the back arrow inside [SettingsScreen]
 * pops the same inner stack.
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
            entry<Settings> {
                val navState = LocalMainShellNavState.current
                SettingsScreen(onBack = { navState.removeLast() })
            }
        }
}
