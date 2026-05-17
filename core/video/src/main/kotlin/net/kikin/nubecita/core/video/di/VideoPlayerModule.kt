package net.kikin.nubecita.core.video.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import net.kikin.nubecita.core.video.SharedVideoPlayer
import net.kikin.nubecita.core.video.createSharedVideoPlayer
import javax.inject.Singleton

/**
 * Hilt module for [SharedVideoPlayer]. The holder is process-scoped
 * (one ExoPlayer per process); a dedicated `SupervisorJob`-backed
 * scope drives the idle-release timer independently of any UI
 * coroutine context so a cancelled feed scope doesn't kill the
 * release timer mid-countdown.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object VideoPlayerModule {
    @Provides
    @Singleton
    fun provideSharedVideoPlayer(
        @ApplicationContext context: Context,
    ): SharedVideoPlayer =
        createSharedVideoPlayer(
            context = context,
            scope = CoroutineScope(SupervisorJob()),
        )
}
