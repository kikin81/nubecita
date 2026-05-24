package net.kikin.nubecita.feature.settings.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.settings.impl.data.DefaultSettingsAccountRepository
import net.kikin.nubecita.feature.settings.impl.data.SettingsAccountRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SettingsRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindSettingsAccountRepository(impl: DefaultSettingsAccountRepository): SettingsAccountRepository
}
