package net.kikin.nubecita.feature.profile.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.profile.impl.data.DefaultProfileRepository
import net.kikin.nubecita.feature.profile.impl.data.ProfileRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface ProfileRepositoryModule {
    // Singleton so the editor's write and the profile screen's read share one
    // instance — the `ownProfileUpdates` signal only bridges the two VMs if
    // they hold the same repository.
    @Binds
    @Singleton
    fun bindProfileRepository(impl: DefaultProfileRepository): ProfileRepository
}
