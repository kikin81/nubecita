package net.kikin.nubecita.core.preferences.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.preferences.DefaultDmPollCursorStore
import net.kikin.nubecita.core.preferences.DefaultMessageCheckingPreference
import net.kikin.nubecita.core.preferences.DmPollCursorStore
import net.kikin.nubecita.core.preferences.MessageCheckingPreference
import javax.inject.Singleton

/**
 * Binds the background-messaging preference accessors ([DmPollCursorStore] and
 * [MessageCheckingPreference]) for all variants. Unlike
 * [UserPreferencesBindingsModule] this is not flavor-split: the bench flavor
 * never injects these (the background DM-poll worker is registered only in the
 * production-flavor `AppInitializer`), so the bindings stay inert there — Hilt
 * never constructs the impls and the shared DataStore is not activated by them.
 *
 * `public abstract class` so an instrumentation test can swap a binding via
 * `@TestInstallIn(replaces = [BackgroundMessagingPreferencesModule::class])`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BackgroundMessagingPreferencesModule {
    @Binds
    @Singleton
    internal abstract fun bindDmPollCursorStore(impl: DefaultDmPollCursorStore): DmPollCursorStore

    @Binds
    @Singleton
    internal abstract fun bindMessageCheckingPreference(impl: DefaultMessageCheckingPreference): MessageCheckingPreference
}
