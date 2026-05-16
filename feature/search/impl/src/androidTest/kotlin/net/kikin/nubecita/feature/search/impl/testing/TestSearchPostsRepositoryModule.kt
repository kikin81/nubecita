package net.kikin.nubecita.feature.search.impl.testing

import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import net.kikin.nubecita.feature.search.impl.data.SearchPostsRepository
import net.kikin.nubecita.feature.search.impl.di.SearchPostsRepositoryModule

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [SearchPostsRepositoryModule::class],
)
internal interface TestSearchPostsRepositoryModule {
    @Binds
    fun bindFakeSearchPostsRepository(impl: FakeSearchPostsRepository): SearchPostsRepository
}
