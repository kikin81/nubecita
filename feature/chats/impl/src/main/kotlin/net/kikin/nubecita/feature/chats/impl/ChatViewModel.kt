package net.kikin.nubecita.feature.chats.impl

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.feature.chats.api.Chat
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.applyOptimisticToggle
import net.kikin.nubecita.feature.chats.impl.data.toChatError
import net.kikin.nubecita.feature.chats.impl.data.toThreadItems
import kotlin.time.Clock

/**
 * MVI presenter for the chat thread screen.
 *
 * Receives the [Chat] NavKey via assisted injection at the navigation entry
 * point — matches the project pattern used by `ComposerViewModel` /
 * `MediaViewerViewModel`. Auto-loads on construction and normalizes the NavKey:
 * a `convoId` opens directly (group or direct); an `otherUserDid` resolves to
 * its 1:1 convoId first via `chat.bsky.convo.getConvoForMembers`. Either path
 * then loads the kind-aware header + `canPost` gate via `chat.bsky.convo.getConvo`
 * and the first page of messages via `chat.bsky.convo.getMessages`. The composer
 * send gate folds composer-non-blank with [canPostFlow]. Refresh / retry events
 * re-run the chain. Single-flight via [inFlightLoad].
 */
@HiltViewModel(assistedFactory = ChatViewModel.Factory::class)
class ChatViewModel
    @AssistedInject
    constructor(
        @Assisted private val chat: Chat,
        private val repository: ChatRepository,
    ) : MviViewModel<ChatScreenViewState, ChatEvent, ChatEffect>(ChatScreenViewState()) {
        @AssistedFactory
        interface Factory {
            fun create(chat: Chat): ChatViewModel
        }

        // The NavKey carries EITHER a convoId (group or direct, already resolved)
        // OR an otherUserDid (1:1 start that still needs resolving). Chat.init
        // guarantees at least one is non-null.
        private val convoIdArg: String? = chat.convoId
        private val otherUserDidArg: String? = chat.otherUserDid
        private var inFlightLoad: Job? = null

        /**
         * Resolved convo id (set on the first successful `resolveConvo`). Sends
         * are gated on this — a send before the load chain resolves is a no-op.
         */
        private var convoId: String? = null

        /**
         * Canonical newest-first message list (the source the thread `LazyColumn`
         * derives from via [toThreadItems]). Held outside `UiState` so optimistic
         * appends and the success/failure reconcile can recompute grouping; the
         * projected `ThreadItem` stream is what lands on [ChatScreenViewState].
         */
        private var messages: List<MessageUi> = emptyList()
        private var sendCounter = 0

        /**
         * `(messageId, emoji)` pairs with an add/remove-reaction call in flight.
         * Guards against a rapid double-tap of the same chip issuing two racing
         * (non-idempotent) calls; a second toggle of the same key is dropped until
         * the first resolves.
         */
        private val inFlightReactions = mutableSetOf<Pair<String, String>>()

        /**
         * Accumulating DID → sender profile map for GROUP message attribution. Seeded
         * from the loaded convo's member roster ([ChatHeader.Group.members]) and then
         * augmented by [hydrateMissingSenders] for senders absent from the roster (e.g.
         * a member who has since left) — the wire `MessageView.sender` is DID-only, so
         * the roster alone can't attribute every historical message. Empty for direct
         * threads. Read by [commitMessages] when projecting [ThreadItem]s.
         */
        private val senderProfiles = mutableMapOf<String, AuthorUi>()

        /**
         * Whether the viewer may post in the open convo (from the loaded
         * [ChatConvo.canPost]). Folded into the send gate so a read-only convo
         * disables send even with non-blank composer text. Defaults to `true`
         * (optimistic) until the convo loads.
         */
        private val canPostFlow = MutableStateFlow(true)

        /**
         * Composer text/selection, owned here per the sanctioned MVI editor
         * exception (CLAUDE.md). The screen wires the `state =` text-field
         * overload; a `snapshotFlow` collector projects non-blank-ness into
         * [ChatScreenViewState.isSendEnabled].
         */
        val textFieldState: TextFieldState = TextFieldState()

        init {
            // The send gate is composer-non-blank AND canPost. snapshotFlow
            // projects the boolean (not the raw text) so it only emits when
            // blank/non-blank flips; distinctUntilChanged after the combine
            // collapses redundant emissions, so per-keystroke setState churn is
            // avoided.
            combine(
                snapshotFlow { textFieldState.text.toString().isNotBlank() },
                canPostFlow,
            ) { nonBlank, canPost -> nonBlank && canPost }
                .distinctUntilChanged()
                .onEach { enabled -> setState { copy(isSendEnabled = enabled) } }
                .launchIn(viewModelScope)
            launchLoad()
        }

        override fun handleEvent(event: ChatEvent) {
            when (event) {
                ChatEvent.Refresh -> launchLoad()
                ChatEvent.RetryClicked -> launchLoad()
                ChatEvent.BackPressed -> Unit // screen handles back via nav state
                ChatEvent.Send -> onSend()
                is ChatEvent.RetrySend -> onRetrySend(event.tempId)
                is ChatEvent.QuotedPostTapped ->
                    sendEffect(ChatEffect.NavigateToPost(event.quotedPostUri))
                ChatEvent.GroupDetailsTapped -> onGroupDetailsTapped()
                is ChatEvent.ToggleReaction -> onToggleReaction(event.messageId, event.emoji)
                is ChatEvent.ReplyTo -> onReplyTo(event.messageId)
                ChatEvent.CancelReply -> setState { copy(replyingTo = null) }
            }
        }

        /**
         * Capture [messageId] as the composer's reply target so the reply banner
         * shows and the next send carries a reply reference. No-op on a missing,
         * unsent, or deleted message (you can't meaningfully reply to those).
         */
        private fun onReplyTo(messageId: String) {
            val target = messages.firstOrNull { it.id == messageId } ?: return
            if (target.sendStatus != MessageSendStatus.Sent || target.isDeleted) return
            setState {
                copy(
                    replyingTo =
                        RepliedMessageUi(
                            id = target.id,
                            senderDid = target.senderDid,
                            text = target.text,
                            isDeleted = target.isDeleted,
                            isFromViewer = target.isOutgoing,
                            // Resolve the quoted author's name once, here, from the loaded
                            // header — the reply banner reads it directly instead of doing a
                            // per-recomposition DID→profile lookup. Null for the viewer's own
                            // message (labelled "You") or an unresolvable group DID.
                            senderName = if (target.isOutgoing) null else resolveSenderName(target.senderDid),
                        ),
                )
            }
        }

        /**
         * Display name for [senderDid] from the loaded [ChatHeader]: the peer's name
         * for a direct chat, or the matching member's name for a group. Null when the
         * header hasn't loaded or a group member is no longer in the roster (e.g. they
         * left) — the banner then falls back to a bare "Replying".
         */
        private fun resolveSenderName(senderDid: String): String? =
            when (val header = uiState.value.header) {
                is ChatHeader.Direct -> header.displayName ?: header.handle
                is ChatHeader.Group ->
                    header.members.firstOrNull { it.did == senderDid }?.let { it.displayName ?: it.handle }
                null -> null
            }

        /**
         * Push the group-details screen for the open group convo. Guarded on the
         * resolved [convoId] (the overflow item is group-only and only visible once
         * the convo loads, so this is a no-op before resolution).
         */
        private fun onGroupDetailsTapped() {
            val convo = convoId ?: return
            sendEffect(ChatEffect.NavigateToGroupDetails(convo))
        }

        /**
         * Optimistic reaction toggle: flip the viewer's [emoji] on [messageId] in
         * place via [applyOptimisticToggle], then call add/removeReaction. On
         * success the row reconciles to the server-authoritative reactions; on
         * failure it rolls back to the exact pre-toggle reactions and emits a
         * transient [ChatEffect.ShowReactionError]. An in-flight `(messageId, emoji)`
         * guard drops a rapid second tap of the same chip so the two calls can't
         * race (the SDK addReaction/removeReaction are not idempotent under
         * concurrency). No-ops on a missing/unsent/deleted target.
         */
        private fun onToggleReaction(
            messageId: String,
            emoji: String,
        ) {
            val convo = convoId ?: return
            val key = messageId to emoji
            if (key in inFlightReactions) return
            val target = messages.firstOrNull { it.id == messageId } ?: return
            if (target.sendStatus != MessageSendStatus.Sent || target.isDeleted) return
            val wasReacted = target.reactions.firstOrNull { it.emoji == emoji }?.reactedByViewer == true
            val original = target.reactions
            inFlightReactions += key
            updateMessageReactions(messageId, applyOptimisticToggle(original, emoji).toImmutableList())
            viewModelScope.launch {
                val result =
                    if (wasReacted) {
                        repository.removeReaction(convo, messageId, emoji)
                    } else {
                        repository.addReaction(convo, messageId, emoji)
                    }
                result
                    .onSuccess { server -> updateMessageReactions(messageId, server.reactions) }
                    .onFailure {
                        updateMessageReactions(messageId, original) // rollback to the exact pre-toggle reactions
                        sendEffect(ChatEffect.ShowReactionError)
                    }
                inFlightReactions -= key
            }
        }

        /** Replace [messageId]'s reactions and re-project the thread. */
        private fun updateMessageReactions(
            messageId: String,
            reactions: ImmutableList<ReactionUi>,
        ) {
            messages = messages.map { if (it.id == messageId) it.copy(reactions = reactions) else it }
            commitMessages()
        }

        /**
         * Optimistic send: clear the field, append a `Sending` row with a client
         * temp id, then call the repository. On success the temp row is replaced
         * by the server message (`Sent`); on failure it flips to `Failed`. The
         * server id reconcile means the next `getMessages` refresh (which replaces
         * [messages] wholesale) won't double the row. The transient error effect +
         * inline retry are a follow-up (child D).
         *
         * If a refresh completes mid-send and wipes the temp row, the reconcile
         * re-surfaces the result instead of dropping it: a confirmed message is
         * re-inserted (de-duped on server id in case the refresh already pulled it
         * in), and a failed row is re-inserted so the send isn't silently lost.
         */
        private fun onSend() {
            val convo = convoId ?: return
            // Defensive: the send button is already disabled when !canPost, but the
            // IME Send action can still fire — veto it here too.
            if (!canPostFlow.value) return
            val text = textFieldState.text.toString()
            if (text.isBlank()) return
            textFieldState.clearText()
            // Snapshot + clear the reply target: the optimistic row carries it (so the
            // preview shows immediately) and the send request references it; the banner
            // dismisses the moment the message leaves the composer.
            val replyTarget = uiState.value.replyingTo
            if (replyTarget != null) setState { copy(replyingTo = null) }
            val tempId = "local:${sendCounter++}"
            // Group the optimistic row with the viewer's prior outgoing run; the
            // empty fallback (first send in a fresh convo) is a lone run, so the
            // missing did doesn't mis-group. Reconcile swaps in the server did.
            val viewerDid = messages.firstOrNull { it.isOutgoing }?.senderDid.orEmpty()
            val optimistic =
                MessageUi(
                    id = tempId,
                    senderDid = viewerDid,
                    isOutgoing = true,
                    text = text,
                    isDeleted = false,
                    sentAt = Clock.System.now(),
                    sendStatus = MessageSendStatus.Sending,
                    replyTo = replyTarget,
                )
            messages = listOf(optimistic) + messages
            commitMessages()
            dispatchSend(convo, tempId, optimistic)
        }

        /**
         * Inline retry from a `Failed` row: flip it back to `Sending` in place
         * (reusing its `local:<n>` temp id so the reconcile path is unchanged)
         * and re-issue the send with the row's existing text. No-op if the row
         * was already reconciled away or isn't `Failed` (a stale tap after a
         * concurrent refresh, say).
         */
        private fun onRetrySend(tempId: String) {
            val convo = convoId ?: return
            val existing = messages.firstOrNull { it.id == tempId } ?: return
            if (existing.sendStatus != MessageSendStatus.Failed) return
            val retrying = existing.copy(sendStatus = MessageSendStatus.Sending)
            messages = messages.map { if (it.id == tempId) retrying else it }
            commitMessages()
            dispatchSend(convo, tempId, retrying)
        }

        /**
         * Shared send + reconcile for both the first attempt ([onSend]) and an
         * inline retry ([onRetrySend]). [optimistic] is the in-flight `Sending`
         * row carrying the text to send; on success its [tempId] row is replaced
         * by the server message, on failure it flips to `Failed` and a transient
         * [ChatEffect.ShowSendError] is emitted. The mid-send-refresh fallbacks
         * (temp row wiped wholesale by a concurrent `getMessages`) re-surface the
         * result so neither a sent nor a failed message is silently lost.
         */
        private fun dispatchSend(
            convoId: String,
            tempId: String,
            optimistic: MessageUi,
        ) {
            viewModelScope.launch {
                repository
                    .sendMessage(convoId, optimistic.text, replyToMessageId = optimistic.replyTo?.id)
                    .onSuccess { rawServer ->
                        // Preserve the reply target if the sendMessage response omits it,
                        // so the preview doesn't blink away after the optimistic reconcile.
                        val server =
                            if (rawServer.replyTo == null && optimistic.replyTo != null) {
                                rawServer.copy(replyTo = optimistic.replyTo)
                            } else {
                                rawServer
                            }
                        messages =
                            when {
                                messages.any { it.id == tempId } -> messages.map { if (it.id == tempId) server else it }
                                messages.any { it.id == server.id } -> messages // refresh already pulled it in
                                else -> listOf(server) + messages // temp row wiped mid-send; don't lose the sent message
                            }
                        commitMessages()
                    }.onFailure { throwable ->
                        val failed = optimistic.copy(sendStatus = MessageSendStatus.Failed)
                        messages =
                            if (messages.any { it.id == tempId }) {
                                messages.map { if (it.id == tempId) failed else it }
                            } else {
                                listOf(failed) + messages // temp row wiped mid-send; re-surface the failure
                            }
                        commitMessages()
                        sendEffect(ChatEffect.ShowSendError(throwable.toChatError()))
                    }
            }
        }

        /**
         * Recompute the projected `ThreadItem` stream from [messages]. Defaults to
         * preserving the current refresh flag (send-path commits must not disturb a
         * concurrent refresh); the load-success path passes `isRefreshing = false`
         * explicitly to clear it.
         */
        private fun commitMessages(
            isRefreshing: Boolean = (uiState.value.status as? ChatLoadStatus.Loaded)?.isRefreshing ?: false,
        ) {
            // GROUP attribution: project each incoming message via the accumulating
            // [senderProfiles] map (roster + hydrated stragglers). Direct threads never
            // populate it → empty map → unchanged bare rendering. Snapshot to an
            // immutable copy so the pure mapper sees a stable view.
            val profiles = senderProfiles.toMap()
            setState {
                copy(
                    status =
                        ChatLoadStatus.Loaded(
                            items =
                                messages.toThreadItems(
                                    now = Clock.System.now(),
                                    senderProfiles = profiles,
                                ),
                            isRefreshing = isRefreshing,
                        ),
                )
            }
        }

        /**
         * Fill in profiles for GROUP incoming senders missing from [senderProfiles]
         * (the roster didn't include them — e.g. they left the group). Wire
         * `MessageView.sender` is DID-only, so we batch-fetch via the repository's
         * `getProfiles` and merge, then re-project. Fire-and-forget and best-effort:
         * a failure leaves those rows bare (graceful degradation), and direct threads
         * are a no-op (no group senders to resolve). Skips when nothing is missing, so
         * a fully-rostered group issues zero extra calls.
         */
        private fun hydrateMissingSenders() {
            // Attribution is group-only — a 1:1 thread renders bare, so never fetch
            // (or display) the other user's profile per-message there.
            if (uiState.value.header !is ChatHeader.Group) return
            val missing =
                messages
                    .asSequence()
                    .filter { !it.isOutgoing && it.senderDid.isNotBlank() }
                    .map { it.senderDid }
                    .filter { it !in senderProfiles }
                    .distinct()
                    .toList()
            if (missing.isEmpty()) return
            viewModelScope.launch {
                repository.getProfiles(missing).onSuccess { profiles ->
                    if (profiles.isEmpty()) return@onSuccess
                    profiles.forEach { senderProfiles[it.did] = it }
                    commitMessages()
                }
            }
        }

        private fun launchLoad() {
            if (inFlightLoad?.isActive == true) return
            val priorStatus = uiState.value.status
            if (priorStatus is ChatLoadStatus.Loaded) {
                setState { copy(status = priorStatus.copy(isRefreshing = true)) }
            }
            inFlightLoad =
                viewModelScope.launch {
                    // Normalize the NavKey: a convoId opens directly (group or
                    // direct); an otherUserDid resolves to its 1:1 convoId first.
                    // requireNotNull is safe — Chat.init guarantees at least one of
                    // convoId/otherUserDid is non-null, and this branch only runs
                    // when convoIdArg is null.
                    val resolvedConvoId: Result<String> =
                        if (convoIdArg != null) {
                            Result.success(convoIdArg)
                        } else {
                            repository.resolveConvo(requireNotNull(otherUserDidArg)).map { it.convoId }
                        }
                    resolvedConvoId
                        .onSuccess { id ->
                            repository
                                .getConvo(id)
                                .onSuccess { convo ->
                                    convoId = convo.convoId
                                    canPostFlow.value = convo.canPost
                                    // Seed group attribution from the member roster; the
                                    // gaps (senders who left) are filled by hydrateMissingSenders.
                                    (convo.header as? ChatHeader.Group)?.members?.forEach {
                                        senderProfiles[it.did] = it
                                    }
                                    setState { copy(header = convo.header, canPost = convo.canPost) }
                                    // Group headers seed only a partial member preview (getConvo
                                    // returns ~7); fetch the full roster once to show the accurate
                                    // "N members" count. Fire-and-forget + best-effort: a failure
                                    // leaves memberCount null so the header falls back to the
                                    // facepile (members.size). Direct convos skip this entirely.
                                    if (convo.header is ChatHeader.Group) {
                                        viewModelScope.launch {
                                            repository.getConvoMembers(convo.convoId).onSuccess { page ->
                                                setState {
                                                    val h = header
                                                    if (h is ChatHeader.Group) {
                                                        copy(header = h.copy(memberCount = page.members.size))
                                                    } else {
                                                        this
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    repository
                                        .getMessages(convo.convoId)
                                        .onSuccess { page ->
                                            messages = page.messages
                                            commitMessages(isRefreshing = false)
                                            // Resolve any senders the roster didn't cover
                                            // (group members who have since left, etc.).
                                            hydrateMissingSenders()
                                            // Opening the thread marks it read: clears the
                                            // server-side unreadCount and optimistically zeros
                                            // the cached convo so the in-row + bottom-nav badges
                                            // flip immediately. Best-effort; failure is ignored.
                                            // Fire-and-forget so a slow/hung mark-read network
                                            // call doesn't keep inFlightLoad active and block a
                                            // manual refresh/retry (launchLoad's isActive guard).
                                            viewModelScope.launch { repository.markConvoRead(convo.convoId) }
                                        }.onFailure { throwable ->
                                            handleFailure(throwable)
                                        }
                                }.onFailure { throwable ->
                                    handleFailure(throwable)
                                }
                        }.onFailure { throwable ->
                            handleFailure(throwable)
                        }
                    inFlightLoad = null
                }
        }

        private fun handleFailure(throwable: Throwable) {
            val error = throwable.toChatError()
            val prior = uiState.value.status
            if (prior is ChatLoadStatus.Loaded) {
                setState { copy(status = prior.copy(isRefreshing = false)) }
            } else {
                setState { copy(status = ChatLoadStatus.InitialError(error)) }
            }
        }
    }
