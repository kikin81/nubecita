package net.kikin.nubecita.core.actors.internal

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.actors.ActorRepository
import net.kikin.nubecita.core.actors.BlockRepository
import javax.inject.Singleton

/**
 * Hilt binding for [ActorRepository] → [DefaultActorRepository].
 *
 * Public visibility (with internal-only binding methods) so downstream
 * feature modules' instrumented tests can swap bindings via
 * `@TestInstallIn(replaces = [ActorsModule::class])`. Mirrors the
 * pattern in `PostingModule` and `AuthBindingsModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ActorsModule {
    @Binds
    @Singleton
    internal abstract fun bindActorRepository(impl: DefaultActorRepository): ActorRepository

    @Binds
    @Singleton
    internal abstract fun bindBlockRepository(impl: DefaultBlockRepository): BlockRepository
}
