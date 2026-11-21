package net.kikin.nubecita.core.profile.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.profile.ProfileCache
import net.kikin.nubecita.core.profile.internal.RoomProfileCache
import javax.inject.Singleton

// In `main`, not per flavor: the cache is local Room storage, so the offline
// bench build uses the same implementation.
@Module
@InstallIn(SingletonComponent::class)
abstract class ProfileCacheModule {
    @Binds
    @Singleton
    internal abstract fun bindProfileCache(impl: RoomProfileCache): ProfileCache
}
