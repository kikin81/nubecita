package net.kikin.nubecita.feature.search.impl.testing

import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import net.kikin.nubecita.feature.search.impl.data.SearchActorsRepository
import net.kikin.nubecita.feature.search.impl.di.SearchActorsRepositoryModule

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [SearchActorsRepositoryModule::class],
)
internal interface TestSearchActorsRepositoryModule {
    @Binds
    fun bindFakeSearchActorsRepository(impl: FakeSearchActorsRepository): SearchActorsRepository
}
