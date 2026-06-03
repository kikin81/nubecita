package net.kikin.nubecita.feature.chats.impl.data

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.StateFlow
import net.kikin.nubecita.feature.chats.impl.ConvoListItemUi
import net.kikin.nubecita.feature.chats.impl.MessageUi

/**
 * `chat.bsky.convo.*` fetch surface scoped to `:feature:chats:impl`.
 *
 * Two screens share this interface (and, via the `@Singleton` binding, the same
 * instance — so the convo cache below is a single source of truth):
 * - The Chats tab home observes [observeConvos] and triggers [refreshConvos].
 * - The chat thread screen (`resolveConvo` + `getMessages` + `sendMessage`).
 *
 * The convo list lives in an in-memory reactive cache rather than being
 * re-fetched per screen: [sendMessage] patches the cached convo's last-message
 * preview on success, so the inbox updates live when the user sends from a
 * thread — no polling, no refetch.
 */
interface ChatRepository {
    /**
     * The in-memory convo-list cache as a hot stream. `null` means "not loaded
     * yet" (the inbox renders its initial loading state); a non-null list is the
     * latest known convos, newest-first. Populated by [refreshConvos] and patched
     * in place by [sendMessage].
     */
    fun observeConvos(): StateFlow<ImmutableList<ConvoListItemUi>?>

    /**
     * Fetches the convo list from the network and publishes it into
     * [observeConvos]. Returns success/failure for the caller's load-status
     * lifecycle; the items themselves flow through [observeConvos]. On failure
     * the cache is left untouched, so a failed refresh keeps the prior list.
     */
    suspend fun refreshConvos(): Result<Unit>

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
     *
     * On success it also patches the matching convo in [observeConvos] — updating
     * its last-message preview/timestamp and moving it to the top — so the inbox
     * reflects the send live. A no-op when the convo isn't in the loaded cache
     * (e.g. the inbox was never opened); the next [refreshConvos] picks it up.
     */
    suspend fun sendMessage(
        convoId: String,
        text: String,
    ): Result<MessageUi>
}

data class ConvoResolution(
    val convoId: String,
    val otherUserHandle: String,
    val otherUserDisplayName: String?,
    val otherUserAvatarUrl: String?,
    val otherUserAvatarHue: Int,
)

data class MessagePage(
    val messages: ImmutableList<MessageUi> = persistentListOf(),
    val nextCursor: String? = null,
)

internal const val LIST_CONVOS_PAGE_LIMIT: Int = 30
internal const val GET_MESSAGES_PAGE_LIMIT: Int = 50
