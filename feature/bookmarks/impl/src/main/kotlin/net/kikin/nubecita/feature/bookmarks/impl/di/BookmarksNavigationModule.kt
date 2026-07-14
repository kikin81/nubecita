package net.kikin.nubecita.feature.bookmarks.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.bookmarks.api.Bookmarks
import net.kikin.nubecita.feature.bookmarks.impl.BookmarksScreen

/**
 * `@MainShell` provider for the [Bookmarks] sub-route. Pushed onto the
 * inner back stack from the own-profile top bar via
 * `navState.add(Bookmarks)`; the back arrow inside [BookmarksScreen]
 * pops the same inner stack.
 *
 * Registered as a plain full-screen entry (no `adaptiveDialog`): a
 * scrollable post list reads as a full page on every width, unlike the
 * Settings form which presents as a centered dialog on tablet.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object BookmarksNavigationModule {
    @Provides
    @IntoSet
    @MainShell
    fun provideBookmarksEntries(): EntryProviderInstaller =
        {
            entry<Bookmarks> {
                val navState = LocalMainShellNavState.current
                BookmarksScreen(onBack = { navState.removeLast() })
            }
        }
}
