package net.kikin.nubecita.feature.chats.impl.worker

import net.kikin.nubecita.core.wearcontract.WearAction
import net.kikin.nubecita.core.wearcontract.WearDmAction
import net.kikin.nubecita.core.wearcontract.WearDmActions
import net.kikin.nubecita.core.wearsync.WearActionHandler
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import javax.inject.Inject

/**
 * Runs the watch's quick replies and reactions (nubecita-by5w.3) through
 * [ChatRepository], next to the rest of the DM code. A reply returns the sent
 * message's id, so the watch can match it in the next DM snapshot. Both expire
 * after [WearDmActions.MAX_AGE_MS]: a reply that sat on a disconnected watch
 * shouldn't arrive an hour late.
 */
internal class DmWearActionHandler
    @Inject
    constructor(
        private val repository: ChatRepository,
    ) : WearActionHandler {
        override fun canHandle(action: WearAction): Boolean = action is WearDmAction

        override fun maxAgeMs(action: WearAction): Long = WearDmActions.MAX_AGE_MS

        override suspend fun execute(action: WearAction): Result<String?> {
            if (action !is WearDmAction) return Result.failure(IllegalArgumentException("not a DM action: $action"))
            return when (action) {
                is WearAction.DmReply -> repository.sendMessage(action.convoId, action.text).map { it.id }
                is WearAction.DmReact -> repository.addReaction(action.convoId, action.messageId, action.emoji).map { null }
            }
        }
    }
