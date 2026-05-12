package net.kikin.nubecita.feature.profile.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.profile.impl.data.DefaultProfileRepository
import net.kikin.nubecita.feature.profile.impl.data.ProfileRepository

@Module
@InstallIn(SingletonComponent::class)
internal interface ProfileRepositoryModule {
    @Binds
    fun bindProfileRepository(impl: DefaultProfileRepository): ProfileRepository
}
