package net.kikin.nubecita.feature.profile.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.MainShell
import timber.log.Timber

/**
 * Provides `@MainShell`-qualified `EntryProviderInstaller`s for the
 * Profile and Settings NavKeys.
 *
 * **Bead C status: inert.** The providers exist to validate the Hilt
 * graph (the new `:feature:profile:impl` module is reachable from
 * `:app`'s DI component) and to validate the `@MainShell` qualifier
 * wiring. Each installer's lambda body is empty — no `entry<...>`
 * registrations — so the `EntryProviderBuilder` collected by
 * `MainShell` sees nothing from this module.
 *
 * Why empty rather than registering entries? `:app`'s
 * `MainShellPlaceholderModule` currently owns the
 * `@MainShell`-qualified `entry<Profile>` and `entry<Settings>`
 * providers. Adding non-empty providers here in Bead C would put TWO
 * entries on the inner `NavDisplay` for the same `NavKey`, which is
 * undefined behavior in Nav 3. Bead D activates the entry bodies
 * here AND strips the `:app` placeholders **in the same PR** — so the
 * cutover is atomic and there's no ambiguous window.
 *
 * Tracked in the `app-navigation-shell` spec delta of
 * `openspec/changes/add-profile-feature/specs/app-navigation-shell/spec.md`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object ProfileNavigationModule {
    @Provides
    @IntoSet
    @MainShell
    fun provideProfileEntries(): EntryProviderInstaller =
        {
            // Inert in Bead C. Bead D body:
            //   entry<Profile> { route ->
            //       val navState = LocalMainShellNavState.current
            //       val vm = hiltViewModel<ProfileViewModel, ProfileViewModel.Factory>(
            //           creationCallback = { it.create(route) },
            //       )
            //       ProfileScreen(
            //           viewModel = vm,
            //           onNavigateToPost = { uri -> navState.add(PostDetailRoute(uri)) },
            //           onNavigateToProfile = { handle -> navState.add(Profile(handle)) },
            //           onNavigateToSettings = { navState.add(Settings) },
            //           onBack = { navState.removeLast() },
            //       )
            //   }
            Timber.tag(TAG).d("Profile installer: inert (Bead C scaffolding)")
        }

    @Provides
    @IntoSet
    @MainShell
    fun provideSettingsEntries(): EntryProviderInstaller =
        {
            // Inert in Bead C. Bead F body wires SettingsStubScreen +
            // Sign Out via :core:auth's logout pathway.
            Timber.tag(TAG).d("Settings installer: inert (Bead C scaffolding)")
        }

    private const val TAG = "ProfileNav"
}
