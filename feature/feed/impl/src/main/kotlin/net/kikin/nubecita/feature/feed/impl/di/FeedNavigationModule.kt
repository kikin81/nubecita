package net.kikin.nubecita.feature.feed.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.feed.impl.FeedScreen

@Module
@InstallIn(SingletonComponent::class)
internal object FeedNavigationModule {
    @Provides
    @IntoSet
    @MainShell
    fun provideFeedEntries(): EntryProviderInstaller =
        {
            entry<Feed> {
                FeedScreen(
                    // PostDetail screen does not exist yet — wired as a no-op until
                    // the post-detail epic lands. Replace with `navigator.goTo(PostDetail(post.uri))`.
                    onNavigateToPost = {},
                    // Profile screen does not exist yet — wired as a no-op until
                    // the profile epic lands. Replace with `navigator.goTo(Profile(authorDid))`.
                    onNavigateToAuthor = {},
                )
            }
        }
}
