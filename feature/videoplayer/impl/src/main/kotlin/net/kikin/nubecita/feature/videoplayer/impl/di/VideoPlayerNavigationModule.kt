package net.kikin.nubecita.feature.videoplayer.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.videoplayer.api.VideoPlayerRoute
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerScreen

/**
 * Registers the fullscreen player route on the `@MainShell`-qualified
 * `EntryProviderInstaller` multibinding so `MainShell`'s inner
 * `NavDisplay` can resolve [VideoPlayerRoute] to [VideoPlayerScreen].
 */
@Module
@InstallIn(SingletonComponent::class)
internal object VideoPlayerNavigationModule {
    @Provides
    @IntoSet
    @MainShell
    fun provideVideoPlayerEntries(): EntryProviderInstaller =
        {
            entry<VideoPlayerRoute> {
                VideoPlayerScreen()
            }
        }
}
