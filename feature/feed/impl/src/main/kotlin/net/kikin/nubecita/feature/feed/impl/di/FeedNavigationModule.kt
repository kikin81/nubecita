package net.kikin.nubecita.feature.feed.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.feed.impl.FeedScreen

@Module
@InstallIn(SingletonComponent::class)
internal object FeedNavigationModule {
    @Provides
    @IntoSet
    fun provideFeedEntries(): EntryProviderInstaller =
        {
            entry<Feed> { FeedScreen() }
        }
}
