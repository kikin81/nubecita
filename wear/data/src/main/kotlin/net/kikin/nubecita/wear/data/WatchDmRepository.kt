package net.kikin.nubecita.wear.data

import kotlinx.coroutines.flow.Flow
import net.kikin.nubecita.wear.data.db.WatchConvoEntity
import net.kikin.nubecita.wear.data.db.WatchDmDao
import net.kikin.nubecita.wear.data.db.WatchDmStateEntity
import net.kikin.nubecita.wear.data.db.WatchMessageEntity
import javax.inject.Inject

/** Read side of the synced DMs, for the watch UI (nubecita-by5w.2). */
interface WatchDmRepository {
    /** Null until the phone has synced DMs once. */
    fun state(accountDid: String): Flow<WatchDmStateEntity?>

    fun convos(accountDid: String): Flow<List<WatchConvoEntity>>

    /** Each conversation's newest message, for the list's preview line. */
    fun lastMessages(accountDid: String): Flow<List<WatchMessageEntity>>

    /** Oldest first. */
    fun messages(
        accountDid: String,
        convoId: String,
    ): Flow<List<WatchMessageEntity>>
}

/** Production read side over Room. Bound by the Wear app's production flavor (bench binds a fake). */
class RoomWatchDmRepository
    @Inject
    constructor(
        private val dao: WatchDmDao,
    ) : WatchDmRepository {
        override fun state(accountDid: String) = dao.observeState(accountDid)

        override fun convos(accountDid: String) = dao.observeConvos(accountDid)

        override fun lastMessages(accountDid: String) = dao.observeLastMessages(accountDid)

        override fun messages(
            accountDid: String,
            convoId: String,
        ) = dao.observeMessages(accountDid, convoId)
    }
