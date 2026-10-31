package net.kikin.nubecita.core.profile.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.profile.CurrentAccountRepository
import net.kikin.nubecita.core.profile.DefaultCurrentAccountRepository
import javax.inject.Singleton

/**
 * Production binding for [CurrentAccountRepository]. The bench flavor ships a
 * same-FQN parallel that binds a fixed, offline account, so any
 * `@TestInstallIn(replaces = [CurrentAccountBindingsModule::class])` resolves
 * identically across flavors.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CurrentAccountBindingsModule {
    @Binds
    @Singleton
    internal abstract fun bindCurrentAccountRepository(impl: DefaultCurrentAccountRepository): CurrentAccountRepository
}
