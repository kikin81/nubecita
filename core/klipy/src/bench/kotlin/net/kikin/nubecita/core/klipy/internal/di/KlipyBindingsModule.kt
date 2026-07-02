package net.kikin.nubecita.core.klipy.internal.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.klipy.KlipyRepository
import net.kikin.nubecita.core.klipy.internal.BenchFakeKlipyRepository
import javax.inject.Singleton

/**
 * Bench-flavor parallel of the production [KlipyBindingsModule]. Same FQN
 * (`net.kikin.nubecita.core.klipy.internal.di.KlipyBindingsModule`) so any
 * `@TestInstallIn(replaces = [KlipyBindingsModule::class])` reference resolves
 * identically across flavors; AGP picks this copy in `bench` variants only.
 *
 * Binds [KlipyRepository] → [BenchFakeKlipyRepository], which serves canned
 * fixture data with no key and no network — the offline picker path.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class KlipyBindingsModule {
    @Binds
    @Singleton
    internal abstract fun bindKlipyRepository(impl: BenchFakeKlipyRepository): KlipyRepository
}
