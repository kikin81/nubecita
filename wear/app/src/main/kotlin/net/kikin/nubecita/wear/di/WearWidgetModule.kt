package net.kikin.nubecita.wear.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.wear.data.sync.DmSyncObserver
import net.kikin.nubecita.wear.widget.GlanceLauncherWidgetRefresh
import net.kikin.nubecita.wear.widget.LauncherWidgetRefresh
import net.kikin.nubecita.wear.widget.LauncherWidgetUpdater

/** The launcher widget refreshes alongside the complication when synced DMs change. */
@Module
@InstallIn(SingletonComponent::class)
internal interface WearWidgetModule {
    @Binds
    fun bindLauncherWidgetRefresh(impl: GlanceLauncherWidgetRefresh): LauncherWidgetRefresh

    @Binds
    @IntoSet
    fun bindLauncherWidgetUpdater(impl: LauncherWidgetUpdater): DmSyncObserver
}
