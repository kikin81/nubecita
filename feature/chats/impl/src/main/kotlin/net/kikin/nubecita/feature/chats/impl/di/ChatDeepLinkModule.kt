package net.kikin.nubecita.feature.chats.impl.di

import android.content.Intent
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.IntentActionFilter
import net.kikin.nubecita.core.common.navigation.NavKeyDeepLinkMatcher
import net.kikin.nubecita.core.common.navigation.uriDeepLinkMatcher
import net.kikin.nubecita.feature.chats.api.Chat

/**
 * Registers the deep-link matcher that turns a DM notification tap
 * (`nubecita://chat/{otherUserDid}`) into a [Chat] NavKey. `:app`'s
 * `MainActivity.handleIntent` already iterates the Hilt-bound matcher set and
 * publishes the resolved key to the `DeepLinkRouter`, which `MainShell` pushes
 * onto the back stack. Mirrors `ProfileDeepLinkModule`.
 *
 * The custom `nubecita://chat` scheme/host has a matching `<intent-filter>` on
 * `MainActivity` so the OS routes the (package-constrained) VIEW intent built by
 * [net.kikin.nubecita.feature.chats.impl.worker.MessagingStyleDmNotifier].
 *
 * `accept` rejects anything that isn't a DID at the matcher boundary so a
 * malformed tap target never reaches `getConvoForMembers`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object ChatDeepLinkModule {
    @Provides
    @IntoSet
    fun provideChatDeepLinkMatcher(): NavKeyDeepLinkMatcher =
        uriDeepLinkMatcher(
            uriPattern = "nubecita://chat/{otherUserDid}",
            serializer = Chat.serializer(),
            filters = listOf(IntentActionFilter(Intent.ACTION_VIEW)),
            accept = { chat -> chat.otherUserDid?.startsWith("did:") == true },
        )
}
