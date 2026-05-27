package net.kikin.nubecita.feature.notifications.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.notifications.api.NotificationsTab
import net.kikin.nubecita.feature.notifications.impl.NotificationsScreen

@Module
@InstallIn(SingletonComponent::class)
internal object NotificationsNavigationModule {
    @Provides
    @IntoSet
    @MainShell
    fun provideNotificationsEntries(): EntryProviderInstaller =
        {
            entry<NotificationsTab> {
                NotificationsScreen()
            }
        }
}
