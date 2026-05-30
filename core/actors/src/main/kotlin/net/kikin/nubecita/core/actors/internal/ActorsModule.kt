package net.kikin.nubecita.core.actors.internal

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.actors.ActorRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface ActorsModule {
    @Binds
    @Singleton
    fun bindActorRepository(impl: DefaultActorRepository): ActorRepository
}
