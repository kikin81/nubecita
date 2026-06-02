package net.kikin.nubecita.feature.profile.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.profile.impl.data.BenchFakeProfileRepository
import net.kikin.nubecita.feature.profile.impl.data.ProfileRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface ProfileRepositoryModule {
    @Binds
    @Singleton
    fun bindProfileRepository(impl: BenchFakeProfileRepository): ProfileRepository
}
