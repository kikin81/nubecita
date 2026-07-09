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
 * Registers the deep-link matchers that turn a DM notification tap into a [Chat]
 * NavKey. `:app`'s `MainActivity.handleIntent` iterates the Hilt-bound matcher
 * set (sorted by path-segment specificity) and publishes the resolved key to the
 * `DeepLinkRouter`, which `MainShell` pushes onto the back stack. Mirrors
 * `ProfileDeepLinkModule`.
 *
 * Two shapes, both under the host-scoped `nubecita://chat` `<intent-filter>` on
 * `MainActivity`:
 *
 * - **`nubecita://chat/convo/{convoId}`** → `Chat(convoId)` — what the notifier
 *   ([net.kikin.nubecita.feature.chats.impl.worker.MessagingStyleDmNotifier])
 *   emits today. Opens the conversation directly whether group or 1:1, so a
 *   group notification opens the group (nubecita-g1ph). Two segments, so it
 *   outranks the 1-segment DID form at the specificity sort.
 * - **`nubecita://chat/{otherUserDid}`** → `Chat(otherUserDid)` — legacy 1:1 form
 *   kept so a notification posted by a pre-fix build still resolves (its
 *   `getConvoForMembers` resolution is 1:1-only). No current producer.
 *
 * `accept` rejects a malformed tap target at the matcher boundary (a blank
 * convoId, or a non-DID other-user) so it never reaches `getConvoForMembers`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object ChatDeepLinkModule {
    @Provides
    @IntoSet
    fun provideChatConvoDeepLinkMatcher(): NavKeyDeepLinkMatcher =
        uriDeepLinkMatcher(
            uriPattern = "nubecita://chat/convo/{convoId}",
            serializer = Chat.serializer(),
            filters = listOf(IntentActionFilter(Intent.ACTION_VIEW)),
            accept = { chat -> !chat.convoId.isNullOrBlank() },
        )

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
