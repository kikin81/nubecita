package net.kikin.nubecita.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.BuildConfig
import net.kikin.nubecita.R
import net.kikin.nubecita.core.push.di.PushAppConfig
import net.kikin.nubecita.core.push.di.PushSmallIconRes
import javax.inject.Singleton

/**
 * `:app`-side bindings for `:core:push` config that depends on app
 * identity / branding:
 *
 * - [PushAppConfig] carries the resolved `applicationId`
 *   ([BuildConfig.APPLICATION_ID]) the push gateway requires in the
 *   `registerPush` body's `appId` field. Kept out of `:core:push` so the
 *   module stays free of `:app`'s BuildConfig.
 * - `@PushSmallIconRes` is the drawable rendered in the status bar /
 *   shade. v1 reuses `ic_launcher_foreground` — Phase-2 polish item to
 *   add a dedicated monochrome push icon if the launcher foreground
 *   renders poorly tinted by the OS.
 */
@Module
@InstallIn(SingletonComponent::class)
object PushAppModule {
    @Provides
    @Singleton
    fun providePushAppConfig(): PushAppConfig = PushAppConfig(applicationId = BuildConfig.APPLICATION_ID)

    @Provides
    @PushSmallIconRes
    fun providePushSmallIconRes(): Int = R.drawable.ic_launcher_foreground
}
