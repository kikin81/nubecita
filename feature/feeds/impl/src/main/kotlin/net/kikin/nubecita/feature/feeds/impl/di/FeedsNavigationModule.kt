package net.kikin.nubecita.feature.feeds.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.feeds.api.Feeds
import net.kikin.nubecita.feature.feeds.impl.ManageFeedsScreen

/**
 * `@MainShell` provider for the [Feeds] sub-route (pinned-feeds
 * management). Pushed onto `MainShell`'s inner back stack from the Feed
 * chip row's trailing button via `navState.add(Feeds)`; the back arrow
 * inside [ManageFeedsScreen] pops the same inner stack.
 *
 * Replaces the `:app`-side `FeedsPlaceholderModule` deleted in
 * nubecita-ydfn.1 — per the `:api`-first stub convention, the placeholder
 * provider is removed in favour of this impl-module provider with no
 * bridging artifacts.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object FeedsNavigationModule {
    @Provides
    @IntoSet
    @MainShell
    fun provideFeedsEntries(): EntryProviderInstaller =
        {
            entry<Feeds> {
                val navState = LocalMainShellNavState.current
                ManageFeedsScreen(onBack = { navState.removeLast() })
            }
        }
}
