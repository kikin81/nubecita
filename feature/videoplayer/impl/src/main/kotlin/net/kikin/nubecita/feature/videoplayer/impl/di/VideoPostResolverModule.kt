package net.kikin.nubecita.feature.videoplayer.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.videoplayer.impl.data.DefaultVideoPostResolver
import net.kikin.nubecita.feature.videoplayer.impl.data.VideoPostResolver

@Module
@InstallIn(SingletonComponent::class)
internal interface VideoPostResolverModule {
    @Binds
    fun bindVideoPostResolver(impl: DefaultVideoPostResolver): VideoPostResolver
}
