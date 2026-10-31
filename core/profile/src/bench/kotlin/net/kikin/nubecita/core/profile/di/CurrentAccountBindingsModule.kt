package net.kikin.nubecita.core.profile.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.profile.BenchCurrentAccountRepository
import net.kikin.nubecita.core.profile.CurrentAccountRepository
import javax.inject.Singleton

/**
 * Bench-flavor parallel of the production [CurrentAccountBindingsModule]; same
 * FQN so test replacements resolve identically across flavors. Binds the fixed,
 * offline [BenchCurrentAccountRepository].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CurrentAccountBindingsModule {
    @Binds
    @Singleton
    internal abstract fun bindCurrentAccountRepository(impl: BenchCurrentAccountRepository): CurrentAccountRepository
}
