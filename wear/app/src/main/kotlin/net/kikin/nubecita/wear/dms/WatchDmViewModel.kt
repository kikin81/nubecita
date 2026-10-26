package net.kikin.nubecita.wear.dms

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.wear.data.WatchDmRepository
import net.kikin.nubecita.wear.data.db.WatchConvoEntity
import net.kikin.nubecita.wear.data.db.WatchMessageEntity
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
    ) : MviViewModel<WatchDmState, WatchDmEvent, WatchDmEffect>(WatchDmState()) {
        private val account = MutableStateFlow<String?>(null)
        private val openConvo = MutableStateFlow<String?>(null)

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
                        combine(list, thread) { (header, convos, lastByConvo), open ->
                            WatchDmState(
                                status = if (header == null) WatchDmStatus.NotSynced else WatchDmStatus.Ready,
                                unreadCount = header?.unreadCount ?: 0,
                                convos = convos.map { it.toUi(lastByConvo[it.convoId]) }.toImmutableList(),
                                thread =
                                    open?.let { (id, messages) ->
                                        convos.firstOrNull { it.convoId == id }?.let { c ->
                                            WatchThreadUi(c.convoId, c.title, c.isGroup, messages.map { it.toUi() }.toImmutableList())
                                        }
                                    },
                            )
                        }
                    }
                }.onEach { state -> setState { state } }
                .launchIn(viewModelScope)
        }

        override fun handleEvent(event: WatchDmEvent) {
            when (event) {
                is WatchDmEvent.AccountChanged -> {
                    if (event.accountDid != account.value) openConvo.value = null
                    account.value = event.accountDid
                }
                is WatchDmEvent.ThreadOpened -> openConvo.value = event.convoId
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

internal fun WatchMessageEntity.toUi() =
    WatchMessageUi(
        id = messageId,
        fromViewer = fromViewer,
        senderName = senderName,
        text = text,
        sentAtMs = sentAtMs,
        isDeleted = isDeleted,
        hasAttachment = hasAttachment,
    )
