package net.kikin.nubecita.feature.notifications.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.notifications.impl.data.DefaultNotificationsRepository
import net.kikin.nubecita.feature.notifications.impl.data.NotificationsRepository

@Module
@InstallIn(SingletonComponent::class)
internal interface NotificationsRepositoryModule {
    @Binds
    fun bindNotificationsRepository(impl: DefaultNotificationsRepository): NotificationsRepository
}
