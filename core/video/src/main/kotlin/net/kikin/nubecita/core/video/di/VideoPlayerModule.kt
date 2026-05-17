package net.kikin.nubecita.core.video.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.core.video.SharedVideoPlayer
import net.kikin.nubecita.core.video.createSharedVideoPlayer
import javax.inject.Singleton

/**
 * Hilt module for [SharedVideoPlayer]. Process-scoped: one ExoPlayer
 * per process. Reuses the existing `@ApplicationScope` (SupervisorJob +
 * Dispatchers.Default) provided by `:core:common` so the idle-release
 * timer runs on the same long-lived scope as other "outlives any
 * screen" work in the app — no duplicated scope construction.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object VideoPlayerModule {
    @Provides
    @Singleton
    fun provideSharedVideoPlayer(
        @ApplicationContext context: Context,
        @ApplicationScope appScope: CoroutineScope,
    ): SharedVideoPlayer =
        createSharedVideoPlayer(
            context = context,
            scope = appScope,
        )
}
