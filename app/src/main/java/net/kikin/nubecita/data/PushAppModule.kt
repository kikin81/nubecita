package net.kikin.nubecita.data

import androidx.annotation.DrawableRes
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
 *   shade. This is `ic_stat_nubecita`, a dedicated 24dp asset — the
 *   Phase-2 item this comment used to defer. It stopped being optional:
 *   `ic_launcher_foreground` carries an adaptive-icon safe margin
 *   (artwork at 0.7 scale in a 108dp canvas, ~54% coverage) and Android
 *   draws a small icon from the alpha channel margin included, so it
 *   rendered visibly shrunken beside apps shipping a real asset.
 */
@Module
@InstallIn(SingletonComponent::class)
object PushAppModule {
    @Provides
    @Singleton
    fun providePushAppConfig(): PushAppConfig = PushAppConfig(applicationId = BuildConfig.APPLICATION_ID)

    @Provides
    @PushSmallIconRes
    @DrawableRes
    fun providePushSmallIconRes(): Int = R.drawable.ic_stat_nubecita
}
