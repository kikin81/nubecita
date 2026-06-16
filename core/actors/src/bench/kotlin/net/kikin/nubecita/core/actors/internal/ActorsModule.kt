package net.kikin.nubecita.core.actors.internal

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.actors.ActorRepository
import net.kikin.nubecita.core.actors.BlockRepository
import javax.inject.Singleton

/**
 * Bench-flavor counterpart to the production [ActorsModule] at
 * `core/actors/src/production/.../internal/ActorsModule.kt`.
 *
 * AGP source-set selection picks exactly one of the two per variant:
 * - `productionDebug` / `productionRelease` see the production module
 *   (binds `DefaultActorRepository` → `ActorRepository`, real network).
 * - `benchDebug` / `benchRelease` see this module (binds
 *   [BenchFakeActorRepository] → `ActorRepository`, deterministic people).
 *
 * The shared FQN matters: both modules sit at
 * `net.kikin.nubecita.core.actors.internal.ActorsModule`, so they cannot
 * coexist on one variant's classpath. Mirrors `core/posts`'
 * production/bench `PostRepositoryModule` split.
 *
 * Bench previously had no people in the typeahead / People tab (the real
 * `DefaultActorRepository` hit the network and failed offline), which made
 * the typeahead person-tap path unreproducible on the bench build
 * (nubecita-m4jc).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ActorsModule {
    @Binds
    @Singleton
    internal abstract fun bindActorRepository(impl: BenchFakeActorRepository): ActorRepository

    @Binds
    @Singleton
    internal abstract fun bindBlockRepository(impl: BenchFakeBlockRepository): BlockRepository
}
