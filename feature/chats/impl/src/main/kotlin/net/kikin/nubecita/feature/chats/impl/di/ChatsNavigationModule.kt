package net.kikin.nubecita.feature.chats.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.chats.api.Chats
import net.kikin.nubecita.feature.chats.impl.ChatsScreen
import timber.log.Timber

@Module
@InstallIn(SingletonComponent::class)
internal object ChatsNavigationModule {
    @Provides
    @IntoSet
    @MainShell
    fun provideChatsEntries(): EntryProviderInstaller =
        {
            entry<Chats> {
                // nn3.2 replaces this lambda with: navState.add(Chat(otherUserDid)).
                // Until then, log + no-op so taps are visible in instrumentation but don't crash.
                ChatsScreen(
                    onNavigateToChat = { did ->
                        Timber.tag("ChatsNavigation").i("Tap convo did=%s (Chat NavKey lands in nn3.2)", did)
                    },
                )
            }
        }
}
