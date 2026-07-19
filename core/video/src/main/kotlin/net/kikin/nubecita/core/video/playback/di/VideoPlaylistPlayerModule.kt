@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package net.kikin.nubecita.core.video.playback.di

import android.content.Context
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import net.kikin.nubecita.core.video.playback.VerticalVideoPlaylistPlayer
import net.kikin.nubecita.core.video.playback.VideoPlayerFactory

/**
 * Provides a per-ViewModel [VerticalVideoPlaylistPlayer]. Scoped to the
 * `ViewModelComponent` so each vertical-feed surface gets its own pool
 * (released in the hosting ViewModel's `onCleared`). Players are built by the
 * shared [VideoPlayerFactory] with a plain [DefaultTrackSelector] — vertical
 * playback is deliberate viewing, so no in-feed bitrate floor is pinned.
 */
@Module
@InstallIn(ViewModelComponent::class)
internal object VideoPlaylistPlayerModule {
    @Provides
    @ViewModelScoped
    fun provideVerticalVideoPlaylistPlayer(
        @ApplicationContext context: Context,
        factory: VideoPlayerFactory,
    ): VerticalVideoPlaylistPlayer =
        VerticalVideoPlaylistPlayer(
            playerProvider = { factory.create(DefaultTrackSelector(context)) },
        )
}
