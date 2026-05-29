package net.kikin.nubecita.feature.chats.impl.data

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.feature.chats.impl.ConvoListItemUi
import net.kikin.nubecita.feature.chats.impl.MessageUi

/**
 * `chat.bsky.convo.*` fetch surface scoped to `:feature:chats:impl`.
 *
 * Two screens share this interface:
 * - The Chats tab home (`listConvos`).
 * - The chat thread screen (`resolveConvo` + `getMessages`).
 */
internal interface ChatRepository {
    suspend fun listConvos(
        cursor: String? = null,
        limit: Int = LIST_CONVOS_PAGE_LIMIT,
    ): Result<ConvoListPage>

    /**
     * Resolves a peer DID into the appview-side convoId plus the other user's profile
     * bits we need for the thread's TopAppBar. Wraps `chat.bsky.convo.getConvoForMembers`.
     */
    suspend fun resolveConvo(otherUserDid: String): Result<ConvoResolution>

    /**
     * Loads a page of messages for [convoId]. Page is newest-first per the lexicon.
     * Wraps `chat.bsky.convo.getMessages`. `cursor = null` requests the first page.
     */
    suspend fun getMessages(
        convoId: String,
        cursor: String? = null,
        limit: Int = GET_MESSAGES_PAGE_LIMIT,
    ): Result<MessagePage>

    /**
     * Sends [text] as a new message in [convoId] via `chat.bsky.convo.sendMessage`
     * and returns the server-confirmed message. The returned [MessageUi] is the
     * canonical (Sent) row — the composer replaces its optimistic echo with it.
     * A blank/whitespace guard is the caller's responsibility (the composer
     * disables send while empty); this method does not validate [text].
     */
    suspend fun sendMessage(
        convoId: String,
        text: String,
    ): Result<MessageUi>
}

internal data class ConvoListPage(
    val items: ImmutableList<ConvoListItemUi> = persistentListOf(),
    val nextCursor: String? = null,
)

internal data class ConvoResolution(
    val convoId: String,
    val otherUserHandle: String,
    val otherUserDisplayName: String?,
    val otherUserAvatarUrl: String?,
    val otherUserAvatarHue: Int,
)

internal data class MessagePage(
    val messages: ImmutableList<MessageUi> = persistentListOf(),
    val nextCursor: String? = null,
)

internal const val LIST_CONVOS_PAGE_LIMIT: Int = 30
internal const val GET_MESSAGES_PAGE_LIMIT: Int = 50
