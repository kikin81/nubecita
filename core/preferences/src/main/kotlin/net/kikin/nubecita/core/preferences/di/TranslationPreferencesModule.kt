package net.kikin.nubecita.core.preferences.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.preferences.DefaultTranslationPreferences
import net.kikin.nubecita.core.preferences.DeviceLocales
import net.kikin.nubecita.core.preferences.SystemDeviceLocales
import net.kikin.nubecita.core.preferences.TranslationPreferences
import javax.inject.Singleton

/**
 * Lives in `src/main/` — compiled into every variant — unlike
 * [UserPreferencesBindingsModule], which is split per flavor.
 *
 * There is nothing for a bench fake to do here. The bench flavor fakes user
 * preferences because the onboarding routing gate has to be forced open
 * before a benchmark journey can start; translation preferences gate nothing
 * at startup, default to off, and are backed by a local DataStore that needs
 * no network. The real implementation is therefore both offline-safe and the
 * more useful one for a bench smoke test, which can flip a toggle and observe
 * it actually persist.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TranslationPreferencesModule {
    @Binds
    @Singleton
    internal abstract fun bindTranslationPreferences(impl: DefaultTranslationPreferences): TranslationPreferences

    @Binds
    @Singleton
    internal abstract fun bindDeviceLocales(impl: SystemDeviceLocales): DeviceLocales
}
