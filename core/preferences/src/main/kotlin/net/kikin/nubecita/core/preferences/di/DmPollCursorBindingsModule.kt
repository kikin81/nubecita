package net.kikin.nubecita.core.preferences.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.preferences.DefaultDmPollCursorStore
import net.kikin.nubecita.core.preferences.DmPollCursorStore
import javax.inject.Singleton

/**
 * Binds [DmPollCursorStore] for all variants. Unlike
 * [UserPreferencesBindingsModule] this is not flavor-split: the bench flavor
 * never injects the cursor store (the background DM-poll worker is registered
 * only in the production-flavor `AppInitializer`), so the binding stays
 * inert there — Hilt never constructs [DefaultDmPollCursorStore] and the
 * shared DataStore is not activated by it.
 *
 * `public abstract class` so an instrumentation test can swap the binding via
 * `@TestInstallIn(replaces = [DmPollCursorBindingsModule::class])`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DmPollCursorBindingsModule {
    @Binds
    @Singleton
    internal abstract fun bindDmPollCursorStore(impl: DefaultDmPollCursorStore): DmPollCursorStore
}
