package net.kikin.nubecita.wear.dms

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.wear.data.PhoneActionSupport
import net.kikin.nubecita.wear.data.WatchDmActionRepository
import net.kikin.nubecita.wear.data.WatchDmRepository
import net.kikin.nubecita.wear.data.db.WatchConvoEntity
import net.kikin.nubecita.wear.data.db.WatchMessageEntity
import net.kikin.nubecita.wear.data.db.WatchPendingDmEntity
import net.kikin.nubecita.wear.data.reactionList
import timber.log.Timber
import javax.inject.Inject

/**
 * The synced conversations and the open thread, straight from the offline
 * store (nubecita-by5w.2). Activity-scoped, like the feed: the list and the
 * thread read the same state. Works offline; it updates when the phone's next
 * DM poll publishes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WatchDmViewModel
    @Inject
    constructor(
        private val repository: WatchDmRepository,
        private val actions: WatchDmActionRepository,
        private val actionSupport: PhoneActionSupport,
    ) : MviViewModel<WatchDmState, WatchDmEvent, WatchDmEffect>(WatchDmState()) {
        private val account = MutableStateFlow<String?>(null)
        private val openConvo = MutableStateFlow<String?>(null)
        private val actionsAvailable = MutableStateFlow(false)

        init {
            account
                .flatMapLatest { did ->
                    if (did == null) {
                        flowOf(WatchDmState())
                    } else {
                        val list =
                            combine(repository.state(did), repository.convos(did), repository.lastMessages(did)) { header, convos, last ->
                                val lastByConvo = last.associateBy { it.convoId }
                                Triple(header, convos, lastByConvo)
                            }
                        // The thread is queried with a convo id only for this same account.
                        val thread =
                            openConvo.flatMapLatest { id ->
                                if (id == null) flowOf(null) else repository.messages(did, id).map { id to it }
                            }
                        combine(list, thread, actions.pending(did)) { (header, convos, lastByConvo), open, pending ->
                            WatchDmState(
                                status = if (header == null) WatchDmStatus.NotSynced else WatchDmStatus.Ready,
                                unreadCount = header?.unreadCount ?: 0,
                                convos = convos.map { it.toUi(lastByConvo[it.convoId]) }.toImmutableList(),
                                thread =
                                    open?.let { (id, messages) ->
                                        convos.firstOrNull { it.convoId == id }?.let { c ->
                                            val mine = pending.filter { it.convoId == id }
                                            val reactionsByMessage = mine.filter { it.kind == WatchPendingDmEntity.KIND_REACT }.groupBy { it.targetMessageId }
                                            WatchThreadUi(
                                                convoId = c.convoId,
                                                title = c.title,
                                                isGroup = c.isGroup,
                                                messages = messages.map { it.toUi(reactionsByMessage[it.messageId].orEmpty()) }.toImmutableList(),
                                                pendingReplies = mine.filter { it.kind == WatchPendingDmEntity.KIND_REPLY }.map { it.toUi() }.toImmutableList(),
                                            )
                                        }
                                    },
                            )
                        }
                    }
                }.combine(actionsAvailable) { state, available -> state.copy(actionsAvailable = available) }
                .onEach { state -> setState { state } }
                .launchIn(viewModelScope)
        }

        override fun handleEvent(event: WatchDmEvent) {
            when (event) {
                is WatchDmEvent.AccountChanged -> {
                    if (event.accountDid != account.value) openConvo.value = null
                    account.value = event.accountDid
                    if (event.accountDid != null) checkActionSupport()
                }
                is WatchDmEvent.ThreadOpened -> openConvo.value = event.convoId
                is WatchDmEvent.ReplySent -> withAccount { did -> actions.sendReply(did, event.convoId, event.text) }
                is WatchDmEvent.ReactionSent -> withAccount { did -> actions.sendReaction(did, event.convoId, event.messageId, event.emoji) }
                is WatchDmEvent.RetryTapped -> withAccount { actions.retry(event.actionId) }
            }
        }

        private fun withAccount(block: suspend (String) -> Unit) {
            val did = account.value ?: return
            viewModelScope.launch { block(did) }
        }

        private fun checkActionSupport() {
            viewModelScope.launch {
                actionsAvailable.value =
                    try {
                        actionSupport.dmAvailable()
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        Timber.w(error, "couldn't check the phone's DM action support")
                        false
                    }
            }
        }
    }

internal fun WatchConvoEntity.toUi(last: WatchMessageEntity?) =
    WatchConvoUi(
        convoId = convoId,
        title = title,
        isGroup = isGroup,
        unreadCount = unreadCount,
        muted = muted,
        lastMessageAtMs = lastMessageAtMs,
        lastMessage = last?.toUi(),
    )

internal fun WatchMessageEntity.toUi(pendingReactions: List<WatchPendingDmEntity> = emptyList()) =
    WatchMessageUi(
        id = messageId,
        fromViewer = fromViewer,
        senderName = senderName,
        text = text,
        sentAtMs = sentAtMs,
        isDeleted = isDeleted,
        hasAttachment = hasAttachment,
        reactions = reactionList().map { WatchReactionUi(it.emoji, it.count, it.mine) }.toImmutableList(),
        pendingReactions = pendingReactions.map { it.toUi() }.toImmutableList(),
    )

internal fun WatchPendingDmEntity.toUi() =
    WatchPendingDmUi(
        actionId = actionId,
        text = text,
        status =
            when (status) {
                WatchPendingDmEntity.STATUS_SENT -> WatchSendStatus.SENT
                WatchPendingDmEntity.STATUS_FAILED -> WatchSendStatus.FAILED
                else -> WatchSendStatus.SENDING
            },
    )
