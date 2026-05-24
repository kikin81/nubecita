package net.kikin.nubecita.core.profile.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.profile.ActorProfileRepository
import net.kikin.nubecita.core.profile.DefaultActorProfileRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ActorProfileModule {
    @Binds
    @Singleton
    abstract fun bindActorProfileRepository(impl: DefaultActorProfileRepository): ActorProfileRepository
}
