package net.kikin.nubecita.core.video.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.video.PipDeviceSupport
import net.kikin.nubecita.core.video.supportsPip
import javax.inject.Singleton

/**
 * Feature-detects Picture-in-Picture support once and exposes it as the
 * [PipDeviceSupport]-qualified boolean that `PipController` injects. Performing
 * the `PackageManager` call here (not inside the controller) keeps the
 * controller platform-free and unit-testable.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object PipModule {
    @Provides
    @Singleton
    @PipDeviceSupport
    fun providePipDeviceSupport(
        @ApplicationContext context: Context,
    ): Boolean = context.supportsPip()
}
