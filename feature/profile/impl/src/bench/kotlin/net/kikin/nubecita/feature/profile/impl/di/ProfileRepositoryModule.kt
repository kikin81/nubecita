package net.kikin.nubecita.feature.profile.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.posts.BenchPostSource
import net.kikin.nubecita.feature.profile.impl.data.BenchFakeProfileRepository
import net.kikin.nubecita.feature.profile.impl.data.BenchLinkFaviconFetcher
import net.kikin.nubecita.feature.profile.impl.data.BenchProfilePostSource
import net.kikin.nubecita.feature.profile.impl.data.LinkFaviconFetcher
import net.kikin.nubecita.feature.profile.impl.data.ProfileRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface ProfileRepositoryModule {
    @Binds
    @Singleton
    fun bindProfileRepository(impl: BenchFakeProfileRepository): ProfileRepository

    @Binds
    fun bindLinkFaviconFetcher(impl: BenchLinkFaviconFetcher): LinkFaviconFetcher

    @Binds
    @IntoSet
    fun bindProfilePostSource(impl: BenchProfilePostSource): BenchPostSource
}
