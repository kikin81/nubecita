package net.kikin.nubecita.wear.data

import kotlinx.coroutines.flow.Flow
import net.kikin.nubecita.wear.data.db.WatchFeedDao
import net.kikin.nubecita.wear.data.db.WatchFeedEntity
import net.kikin.nubecita.wear.data.db.WatchPostEntity
import javax.inject.Inject

/** Read side of the offline store, for the watch UI (nubecita-jh9c.4). */
interface WatchFeedRepository {
    fun feeds(accountDid: String): Flow<List<WatchFeedEntity>>

    fun posts(
        accountDid: String,
        feedKey: String,
    ): Flow<List<WatchPostEntity>>
}

/** Production read side over Room. Bound by the Wear app's production flavor (bench binds a fake). */
class RoomWatchFeedRepository
    @Inject
    constructor(
        private val dao: WatchFeedDao,
    ) : WatchFeedRepository {
        override fun feeds(accountDid: String): Flow<List<WatchFeedEntity>> = dao.observeFeeds(accountDid)

        override fun posts(
            accountDid: String,
            feedKey: String,
        ): Flow<List<WatchPostEntity>> = dao.observePosts(accountDid, feedKey)
    }
