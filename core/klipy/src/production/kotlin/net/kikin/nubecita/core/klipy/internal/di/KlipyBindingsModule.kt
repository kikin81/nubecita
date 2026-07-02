package net.kikin.nubecita.core.klipy.internal.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.klipy.KlipyRepository
import net.kikin.nubecita.core.klipy.internal.DefaultKlipyRepository
import javax.inject.Singleton

/**
 * Production binding for [KlipyRepository] → [DefaultKlipyRepository] (the real
 * KLIPY REST client).
 *
 * Lives in `src/production/`; the parallel bench-flavor copy in `src/bench/`
 * (same FQN) binds [net.kikin.nubecita.core.klipy.internal.BenchFakeKlipyRepository]
 * so the picker is exercisable offline. AGP source-set selection includes
 * exactly one copy per variant. Declared `abstract class` (not `object`) and
 * public so instrumentation tests can swap it via
 * `@TestInstallIn(replaces = [KlipyBindingsModule::class])`, matching
 * `:core:preferences`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class KlipyBindingsModule {
    @Binds
    @Singleton
    internal abstract fun bindKlipyRepository(impl: DefaultKlipyRepository): KlipyRepository
}
