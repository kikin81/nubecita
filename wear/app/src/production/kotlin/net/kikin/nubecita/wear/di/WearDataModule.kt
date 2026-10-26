package net.kikin.nubecita.wear.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.wear.data.CapabilityPhoneActionSupport
import net.kikin.nubecita.wear.data.DataLayerPhoneSessionSource
import net.kikin.nubecita.wear.data.PhoneActionSupport
import net.kikin.nubecita.wear.data.PhoneConnection
import net.kikin.nubecita.wear.data.PhoneSessionSource
import net.kikin.nubecita.wear.data.PlayServicesPhoneConnection
import net.kikin.nubecita.wear.data.RoomWatchActionRepository
import net.kikin.nubecita.wear.data.RoomWatchDmRepository
import net.kikin.nubecita.wear.data.RoomWatchFeedRepository
import net.kikin.nubecita.wear.data.WatchActionRepository
import net.kikin.nubecita.wear.data.WatchDmRepository
import net.kikin.nubecita.wear.data.WatchFeedRepository

@Module
@InstallIn(SingletonComponent::class)
internal interface WearDataModule {
    @Binds
    fun bindPhoneConnection(impl: PlayServicesPhoneConnection): PhoneConnection

    @Binds
    fun bindPhoneSessionSource(impl: DataLayerPhoneSessionSource): PhoneSessionSource

    @Binds
    fun bindWatchFeedRepository(impl: RoomWatchFeedRepository): WatchFeedRepository

    @Binds
    fun bindWatchActionRepository(impl: RoomWatchActionRepository): WatchActionRepository

    @Binds
    fun bindWatchDmRepository(impl: RoomWatchDmRepository): WatchDmRepository

    @Binds
    fun bindPhoneActionSupport(impl: CapabilityPhoneActionSupport): PhoneActionSupport
}
