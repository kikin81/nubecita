package net.kikin.nubecita.wear.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.wear.complication.UnreadDmsComplicationUpdater
import net.kikin.nubecita.wear.data.sync.DmSyncObserver

@Module
@InstallIn(SingletonComponent::class)
internal interface WearComplicationModule {
    @Binds
    @IntoSet
    fun bindUnreadDmsComplicationUpdater(impl: UnreadDmsComplicationUpdater): DmSyncObserver
}
