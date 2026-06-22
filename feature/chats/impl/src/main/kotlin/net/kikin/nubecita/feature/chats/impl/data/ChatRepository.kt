package net.kikin.nubecita.feature.chats.impl.data

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.StateFlow
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.feature.chats.impl.ChatHeader
import net.kikin.nubecita.feature.chats.impl.ConvoRowUi
import net.kikin.nubecita.feature.chats.impl.GroupMemberUi
import net.kikin.nubecita.feature.chats.impl.JoinRequestUi
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
    fun observeConvos(): StateFlow<ImmutableList<ConvoRowUi>?>

    /**
     * Fetches the ACCEPTED convo list from the network and publishes it into
     * [observeConvos]. Returns success/failure for the caller's load-status
     * lifecycle; the items themselves flow through [observeConvos]. On failure
     * the cache is left untouched, so a failed refresh keeps the prior list.
     */
    suspend fun refreshConvos(): Result<Unit>

    /**
     * The in-memory cache of pending message REQUESTS (`listConvos(status=request)`)
     * as a hot stream. `null` = not loaded yet. Kept separate from [observeConvos]
     * so requests never count toward the unread badge and a request-fetch failure
     * can't disturb the accepted list. Populated by [refreshRequestConvos].
     */
    fun observeRequestConvos(): StateFlow<ImmutableList<ConvoRowUi>?>

    /**
     * Fetches the pending message-request list (`status=request`) and publishes it
     * into [observeRequestConvos]. Independent of [refreshConvos] so the Chats tab
     * can refresh both concurrently and surface a requests-only failure without
     * failing the accepted list. On failure the request cache is left untouched.
     */
    suspend fun refreshRequestConvos(): Result<Unit>

    /**
     * Leaves [convoId] via `chat.bsky.convo.leaveConvo`; on success removes it from
     * both the accepted and request caches (it could be in either). The conversation
     * drops out of the viewer's inbox — there is no server-side undo. The optional
     * leave-with-undo window is a ViewModel concern (nubecita-kc17.4), not here.
     */
    suspend fun leaveConvo(convoId: String): Result<Unit>

    /**
     * Accepts a pending message request via `chat.bsky.convo.acceptConvo`; on success
     * moves [convoId] from the request cache to the front of the accepted cache.
     */
    suspend fun acceptConvo(convoId: String): Result<Unit>

    /**
     * Mutes ([muted] = true) or unmutes [convoId] via `chat.bsky.convo.muteConvo` /
     * `unmuteConvo`; on success patches the matching cached convo's `muted` flag in
     * whichever cache holds it.
     */
    suspend fun setMuted(
        convoId: String,
        muted: Boolean,
    ): Result<Unit>

    /**
     * Resolves a peer DID into the appview-side convoId for a 1:1 DM. Wraps
     * `chat.bsky.convo.getConvoForMembers`. The thread's TopAppBar header now comes
     * from [getConvo] (kind-aware); only [ConvoResolution.convoId] is consumed on the
     * production path — the other profile fields are retained for the bench fake's
     * on-demand DM simulation.
     */
    suspend fun resolveConvo(otherUserDid: String): Result<ConvoResolution>

    /**
     * Batch-resolves actor profiles by DID via `app.bsky.actor.getProfiles`. Used to
     * hydrate GROUP message senders whose profile isn't in the convo's current member
     * roster ([getConvo]'s `members`) — e.g. a member who has since left — since the
     * wire `MessageView.sender` carries only a DID. [dids] is chunked to the lexicon's
     * 25-actor limit; returns the resolved [AuthorUi]s (order/!completeness not
     * guaranteed — callers key by `did`). An empty [dids] short-circuits to success.
     */
    suspend fun getProfiles(dids: List<String>): Result<List<AuthorUi>>

    /**
     * Loads a single conversation by [convoId] via `chat.bsky.convo.getConvo` and maps it
     * to a kind-aware [ChatConvo] (header + canPost). Used to open a thread when the convoId
     * is already known (group convos, and the convo-list direct path).
     */
    suspend fun getConvo(convoId: String): Result<ChatConvo>

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

    /**
     * Adds [emoji] (a single emoji grapheme) as the viewer's reaction to [messageId] in
     * [convoId] via `chat.bsky.convo.addReaction`; returns the server-updated message (its
     * authoritative reactions) for optimistic reconciliation.
     */
    suspend fun addReaction(
        convoId: String,
        messageId: String,
        emoji: String,
    ): Result<MessageUi>

    /** Removes the viewer's [emoji] reaction from [messageId] via `removeReaction`; returns the updated message. */
    suspend fun removeReaction(
        convoId: String,
        messageId: String,
        emoji: String,
    ): Result<MessageUi>

    /**
     * Marks [convoId] read via `chat.bsky.convo.updateRead` (omitting the
     * messageId so the server marks read up to the latest message) and
     * optimistically zeros the matching cached convo's `unreadCount` in
     * [observeConvos] — so the in-row badge and the aggregate bottom-nav badge
     * flip to read immediately, without waiting for the next [refreshConvos].
     *
     * Best-effort: a no-op cache patch when the convo isn't in the loaded cache
     * (the server call still runs), and on network failure the cache is left
     * untouched and the failure is returned for the caller to ignore. Marking an
     * already-read convo is harmless (idempotent server-side).
     */
    suspend fun markConvoRead(convoId: String): Result<Unit>

    /**
     * Fetches a page of the account's `chat.bsky.convo.getLog` event stream
     * from [cursor] (`null` = from the server's current head), keeping only
     * create-message events as [ChatLogEvent]s plus the advanced cursor. Used
     * by the background DM-poll worker (v2, nubecita-1fy.15) to detect new
     * inbound messages while the app is backgrounded; unrelated to the
     * foreground convo cache (this does not touch [observeConvos]).
     */
    suspend fun getLog(cursor: String? = null): Result<ChatLogPage>

    /**
     * Loads one page of a group convo's member roster via `chat.bsky.convo.getConvoMembers`,
     * mapping each `chat.bsky.actor.ProfileViewBasic` to a [GroupMemberUi]. Groups cap at 50
     * members, so a single `limit=100` call ([GET_CONVO_MEMBERS_PAGE_LIMIT]) returns the full
     * roster; [cursor] (`null` = first page) is wired for completeness should the cap ever rise.
     */
    suspend fun getConvoMembers(
        convoId: String,
        cursor: String? = null,
    ): Result<MemberPage>

    /** Create a group named [name] with [dids] as initial (pending) members. Returns the new convoId. */
    suspend fun createGroup(
        name: String,
        dids: List<String>,
    ): Result<String>

    /** Add [dids] to group [convoId]. Added with pending status; invitees must accept. */
    suspend fun addMembers(
        convoId: String,
        dids: List<String>,
    ): Result<Unit>

    /** Permanently remove [dids] from group [convoId]. */
    suspend fun removeMembers(
        convoId: String,
        dids: List<String>,
    ): Result<Unit>

    /** Pending join requests for group [convoId]. */
    suspend fun getJoinRequests(
        convoId: String,
        cursor: String? = null,
    ): Result<JoinRequestPage>

    /** Approve the pending request from [did] (they become a member). */
    suspend fun approveJoinRequest(
        convoId: String,
        did: String,
    ): Result<Unit>

    /** Reject the pending request from [did]. */
    suspend fun rejectJoinRequest(
        convoId: String,
        did: String,
    ): Result<Unit>
}

/**
 * A single loaded conversation: its id, the kind-aware [ChatHeader] for the
 * thread TopAppBar, and a lightweight [canPost] gate (see `canViewerPost`).
 */
data class ChatConvo(
    val convoId: String,
    val header: ChatHeader,
    val canPost: Boolean,
)

data class ConvoResolution(
    val convoId: String,
    val otherUserHandle: String,
    val otherUserDisplayName: String?,
    val otherUserAvatarUrl: String?,
)

data class MessagePage(
    val messages: ImmutableList<MessageUi> = persistentListOf(),
    val nextCursor: String? = null,
)

data class ChatLogPage(
    val events: ImmutableList<ChatLogEvent> = persistentListOf(),
    val nextCursor: String? = null,
)

data class MemberPage(
    val members: ImmutableList<GroupMemberUi> = persistentListOf(),
    val cursor: String? = null,
)

data class JoinRequestPage(
    val requests: ImmutableList<JoinRequestUi> = persistentListOf(),
    val cursor: String? = null,
)

internal const val LIST_CONVOS_PAGE_LIMIT: Int = 30
internal const val GET_MESSAGES_PAGE_LIMIT: Int = 50

// Groups cap at 50 members, so a single limit=100 page returns the full roster.
internal const val GET_CONVO_MEMBERS_PAGE_LIMIT: Int = 100

internal const val JOIN_REQUESTS_PAGE_LIMIT: Int = 50
