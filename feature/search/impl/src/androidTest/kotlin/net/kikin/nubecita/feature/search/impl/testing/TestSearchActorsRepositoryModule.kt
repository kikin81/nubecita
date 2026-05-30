package net.kikin.nubecita.feature.search.impl.testing

import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import net.kikin.nubecita.core.actors.ActorRepository
import net.kikin.nubecita.core.actors.internal.ActorsModule

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [ActorsModule::class],
)
internal interface TestSearchActorsRepositoryModule {
    @Binds
    fun bindFakeActorRepository(impl: FakeSearchActorsRepository): ActorRepository
}
