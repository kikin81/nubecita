package net.kikin.nubecita.wear.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.wearcontract.WearAccount
import net.kikin.nubecita.core.wearcontract.WearActionFailure
import net.kikin.nubecita.core.wearcontract.WearActionResult
import net.kikin.nubecita.core.wearcontract.WearAuthor
import net.kikin.nubecita.core.wearcontract.WearCodec
import net.kikin.nubecita.core.wearcontract.WearConvo
import net.kikin.nubecita.core.wearcontract.WearDmSnapshot
import net.kikin.nubecita.core.wearcontract.WearFeedKey
import net.kikin.nubecita.core.wearcontract.WearFeedSnapshot
import net.kikin.nubecita.core.wearcontract.WearMedia
import net.kikin.nubecita.core.wearcontract.WearMessage
import net.kikin.nubecita.core.wearcontract.WearPaths
import net.kikin.nubecita.core.wearcontract.WearPost
import net.kikin.nubecita.core.wearcontract.WearReaction
import net.kikin.nubecita.core.wearcontract.WearSessionSnapshot
import net.kikin.nubecita.core.wearcontract.WearViewer
import net.kikin.nubecita.wear.data.db.WatchActionDao
import net.kikin.nubecita.wear.data.db.WatchConvoEntity
import net.kikin.nubecita.wear.data.db.WatchDmActionDao
import net.kikin.nubecita.wear.data.db.WatchDmDao
import net.kikin.nubecita.wear.data.db.WatchDmStateEntity
import net.kikin.nubecita.wear.data.db.WatchFeedDao
import net.kikin.nubecita.wear.data.db.WatchFeedEntity
import net.kikin.nubecita.wear.data.db.WatchMessageEntity
import net.kikin.nubecita.wear.data.db.WatchPendingActionEntity
import net.kikin.nubecita.wear.data.db.WatchPendingDmEntity
import net.kikin.nubecita.wear.data.db.WatchPostEntity
import net.kikin.nubecita.wear.data.sync.WatchImageStore
import net.kikin.nubecita.wear.data.sync.WatchSyncIngester
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant

class WatchSyncIngesterTest {
    private val alice = "did:plc:alice"
    private val bob = "did:plc:bob"
    private val path = WearPaths.feed(alice, WearFeedKey.FOLLOWING)

    private val dao = FakeDao()
    private val images = FakeImages()
    private val actions = FakeActionDao()
    private val dms = FakeDmDao()
    private val dmActions = FakeDmActionDao()
    private var nowMs = 1_000L
    private val ingester =
        WatchSyncIngester(
            dao,
            images,
            actions,
            dms,
            dmActions,
            object : Clock {
                override fun now() = Instant.fromEpochMilliseconds(nowMs)
            },
        )

    private fun snapshot(
        publishedAtMs: Long,
        vararg posts: WearPost,
    ) = WearCodec.encode(
        WearFeedSnapshot.serializer(),
        WearFeedSnapshot(publishedAtMs = publishedAtMs, accountDid = alice, feedKey = WearFeedKey.FOLLOWING, posts = posts.toList()),
    )

    private fun post(
        uri: String,
        avatar: String? = "a0",
        thumb: String? = null,
    ) = WearPost(
        uri = uri,
        cid = "cid",
        author = WearAuthor(did = "did:plc:x", handle = "x.bsky.social", avatarAssetKey = avatar),
        text = "hi $uri",
        createdAtMs = 1,
        likeCount = 4,
        viewer = WearViewer(likeUri = "at://like", bookmarked = true),
        media = thumb?.let { WearMedia(imageCount = 1, thumbnailAssetKey = it) },
    )

    private val assets: suspend (String) -> ByteArray? = { key -> if (key == "missing") null else key.encodeToByteArray() }

    @Test
    fun a_snapshot_is_stored_in_order_with_local_image_paths() =
        runTest {
            ingester.ingestFeed(path, snapshot(10, post("p1", thumb = "t0"), post("p2")), assets)

            val posts = dao.posts.value
            assertEquals(listOf("p1", "p2"), posts.map { it.uri })
            assertEquals(listOf(0, 1), posts.map { it.position })
            assertEquals("/img/10/a0", posts[0].authorAvatarPath)
            assertEquals("/img/10/t0", posts[0].thumbnailPath)
            assertEquals("at://like", posts[0].likeUri)
            assertTrue(posts[0].bookmarked)
            // The shared avatar is written once.
            assertEquals(listOf("a0", "t0"), images.written)
        }

    @Test
    fun an_older_or_repeated_snapshot_is_ignored() =
        runTest {
            ingester.ingestFeed(path, snapshot(10, post("new")), assets)
            ingester.ingestFeed(path, snapshot(5, post("old")), assets)
            ingester.ingestFeed(path, snapshot(10, post("again")), assets)

            assertEquals(listOf("new"), dao.posts.value.map { it.uri })
        }

    @Test
    fun a_newer_snapshot_replaces_the_feed_and_prunes_old_images() =
        runTest {
            ingester.ingestFeed(path, snapshot(10, post("p1"), post("p2")), assets)
            ingester.ingestFeed(path, snapshot(20, post("p3")), assets)

            assertEquals(listOf("p3"), dao.posts.value.map { it.uri })
            assertEquals(20L, images.keptGeneration)
        }

    @Test
    fun a_missing_asset_leaves_its_path_empty() =
        runTest {
            ingester.ingestFeed(path, snapshot(10, post("p1", avatar = "missing")), assets)

            assertNull(
                dao.posts.value
                    .single()
                    .authorAvatarPath,
            )
        }

    @Test
    fun a_newer_schema_or_garbage_is_ignored() =
        runTest {
            ingester.ingestFeed(path, """{"schemaVersion":99,"whatever":true}""", assets)
            ingester.ingestFeed(path, "not json", assets)

            assertTrue(dao.posts.value.isEmpty())
        }

    @Test
    fun a_deleted_feed_drops_its_rows_and_images() =
        runTest {
            ingester.ingestFeed(path, snapshot(10, post("p1")), assets)

            ingester.onFeedDeleted(path)

            assertTrue(dao.posts.value.isEmpty())
            assertTrue(dao.feeds.value.isEmpty())
            assertNull(images.keptGeneration)
        }

    @Test
    fun signing_out_drops_everything() =
        runTest {
            ingester.ingestFeed(path, snapshot(10, post("p1")), assets)

            ingester.onSession(WearCodec.encode(WearSessionSnapshot.serializer(), WearSessionSnapshot(publishedAtMs = 1)))

            assertTrue(dao.posts.value.isEmpty())
            assertEquals(listOf<String?>(null), images.keptAccounts)
        }

    @Test
    fun switching_accounts_drops_the_previous_account() =
        runTest {
            ingester.ingestFeed(path, snapshot(10, post("p1")), assets)

            ingester.onSession(
                WearCodec.encode(
                    WearSessionSnapshot.serializer(),
                    WearSessionSnapshot(publishedAtMs = 1, account = WearAccount(did = bob, handle = "bob.bsky.social")),
                ),
            )

            assertTrue(dao.posts.value.none { it.accountDid == alice })
            assertEquals(listOf<String?>(bob), images.keptAccounts)
        }

    private fun result(
        id: String,
        failure: WearActionFailure? = null,
        record: String? = null,
    ) = WearCodec.encode(WearActionResult.serializer(), WearActionResult(actionId = id, accountDid = alice, completedAtMs = 500, failure = failure, recordUri = record))

    private fun pending(
        id: String,
        kind: String = "like",
        postUri: String = "p1",
        confirmed: Boolean = false,
        completedAtMs: Long? = null,
    ) = WatchPendingActionEntity(actionId = id, accountDid = alice, postUri = postUri, kind = kind, createdAtMs = 100, confirmed = confirmed, completedAtMs = completedAtMs)

    @Test
    fun an_ok_result_confirms_the_action_with_its_record() =
        runTest {
            actions.rows += pending("a1")

            val handled = ingester.onActionResult(WearPaths.actionResult(alice, "a1"), result("a1", record = "at://like/new"))

            assertTrue(handled)
            val row = actions.rows.single()
            assertTrue(row.confirmed)
            assertEquals("at://like/new", row.recordUri)
            assertEquals(500L, row.completedAtMs)
        }

    @Test
    fun a_failed_or_unreadable_result_drops_the_action() =
        runTest {
            actions.rows += pending("a1")
            actions.rows += pending("a2")

            ingester.onActionResult(WearPaths.actionResult(alice, "a1"), result("a1", failure = WearActionFailure.ERROR))
            ingester.onActionResult(WearPaths.actionResult(alice, "a2"), "not json")

            assertTrue(actions.rows.isEmpty())
        }

    @Test
    fun a_non_result_path_is_not_handled() =
        runTest {
            assertEquals(false, ingester.onActionResult(WearPaths.action(alice, "a1"), result("a1")))
        }

    @Test
    fun a_snapshot_showing_a_confirmed_action_settles_it_but_keeps_unanswered_ones() =
        runTest {
            // post() is liked in the snapshot (likeUri set).
            actions.rows += pending("shown", kind = "like", confirmed = true, completedAtMs = 900)
            actions.rows += pending("notYet", kind = "unlike", postUri = "p2", confirmed = true, completedAtMs = 900)
            actions.rows += pending("waiting", kind = "bookmark", postUri = "p3")

            ingester.ingestFeed(path, snapshot(10, post("p1"), post("p2"), post("p3")), assets)

            assertEquals(listOf("notYet", "waiting"), actions.rows.map { it.actionId })
        }

    @Test
    fun signing_out_drops_the_watch_actions_too() =
        runTest {
            actions.rows += pending("a1")

            ingester.onSession(WearCodec.encode(WearSessionSnapshot.serializer(), WearSessionSnapshot(publishedAtMs = 1)))

            assertTrue(actions.rows.isEmpty())
        }

    private class FakeActionDao : WatchActionDao {
        val rows = mutableListOf<WatchPendingActionEntity>()

        override fun observe(accountDid: String): Flow<List<WatchPendingActionEntity>> = MutableStateFlow(rows.toList())

        override suspend fun all(accountDid: String) = rows.filter { it.accountDid == accountDid }

        override suspend fun insert(action: WatchPendingActionEntity) {
            rows += action
        }

        override suspend fun markConfirmed(
            actionId: String,
            recordUri: String?,
            completedAtMs: Long,
        ) {
            val i = rows.indexOfFirst { it.actionId == actionId }
            if (i >= 0) rows[i] = rows[i].copy(confirmed = true, recordUri = recordUri, completedAtMs = completedAtMs)
        }

        override suspend fun delete(actionIds: List<String>) {
            rows.removeAll { it.actionId in actionIds }
        }

        override suspend fun deleteExcept(accountDid: String) {
            rows.removeAll { it.accountDid != accountDid }
        }
    }

    private fun dmSnapshot(
        publishedAtMs: Long,
        vararg convos: WearConvo,
    ) = WearCodec.encode(WearDmSnapshot.serializer(), WearDmSnapshot(publishedAtMs = publishedAtMs, accountDid = alice, unreadCount = 3, convos = convos.toList()))

    private val dmPath = WearPaths.dms(alice)

    private fun convo(
        id: String,
        vararg texts: String,
    ) = WearConvo(convoId = id, title = "Bob", unreadCount = 1, messages = texts.mapIndexed { i, t -> WearMessage(id = "$id-$i", fromViewer = i % 2 == 1, text = t, sentAtMs = i.toLong()) })

    @Test
    fun a_dm_snapshot_is_stored_in_order_with_its_messages() =
        runTest {
            ingester.ingestDms(dmPath, dmSnapshot(10, convo("c1", "hi", "hey"), convo("c2", "yo")))

            assertEquals(3, dms.state?.unreadCount)
            assertEquals(listOf("c1" to 0, "c2" to 1), dms.convos.map { it.convoId to it.position })
            assertEquals(
                listOf("hi", "hey"),
                dms.messages
                    .filter { it.convoId == "c1" }
                    .sortedBy { it.position }
                    .map { it.text },
            )
            assertTrue(dms.messages.single { it.messageId == "c1-1" }.fromViewer)
        }

    @Test
    fun an_older_dm_snapshot_is_ignored_and_a_newer_one_replaces_all() =
        runTest {
            ingester.ingestDms(dmPath, dmSnapshot(10, convo("c1", "new")))
            ingester.ingestDms(dmPath, dmSnapshot(5, convo("old", "stale")))
            assertEquals(listOf("c1"), dms.convos.map { it.convoId })

            ingester.ingestDms(dmPath, dmSnapshot(20, convo("c2", "newer")))
            assertEquals(listOf("c2"), dms.convos.map { it.convoId })
            assertEquals(listOf("newer"), dms.messages.map { it.text })
        }

    @Test
    fun deleting_the_dm_snapshot_or_signing_out_clears_dms() =
        runTest {
            ingester.ingestDms(dmPath, dmSnapshot(10, convo("c1", "hi")))
            ingester.onDmsDeleted(dmPath)
            assertNull(dms.state)
            assertTrue(dms.convos.isEmpty())

            ingester.ingestDms(dmPath, dmSnapshot(11, convo("c1", "hi")))
            ingester.onSession(WearCodec.encode(WearSessionSnapshot.serializer(), WearSessionSnapshot(publishedAtMs = 1)))
            assertNull(dms.state)
        }

    private class FakeDmDao : WatchDmDao {
        var state: WatchDmStateEntity? = null
        val convos = mutableListOf<WatchConvoEntity>()
        val messages = mutableListOf<WatchMessageEntity>()

        override fun observeState(accountDid: String): Flow<WatchDmStateEntity?> = MutableStateFlow(state)

        override fun observeConvos(accountDid: String): Flow<List<WatchConvoEntity>> = MutableStateFlow(convos.toList())

        override fun observeMessages(
            accountDid: String,
            convoId: String,
        ): Flow<List<WatchMessageEntity>> = MutableStateFlow(messages.filter { it.convoId == convoId })

        override fun observeLastMessages(accountDid: String): Flow<List<WatchMessageEntity>> = MutableStateFlow(emptyList())

        override suspend fun publishedAtMs(accountDid: String) = state?.takeIf { it.accountDid == accountDid }?.publishedAtMs

        override suspend fun upsertState(state: WatchDmStateEntity) {
            this.state = state
        }

        override suspend fun insertConvos(convos: List<WatchConvoEntity>) {
            this.convos += convos
        }

        override suspend fun insertMessages(messages: List<WatchMessageEntity>) {
            this.messages += messages
        }

        override suspend fun deleteMessages(accountDid: String) {
            messages.removeAll { it.accountDid == accountDid }
        }

        override suspend fun deleteConvos(accountDid: String) {
            convos.removeAll { it.accountDid == accountDid }
        }

        override suspend fun deleteState(accountDid: String) {
            if (state?.accountDid == accountDid) state = null
        }

        override suspend fun deleteMessagesExcept(accountDid: String) {
            messages.removeAll { it.accountDid != accountDid }
        }

        override suspend fun deleteConvosExcept(accountDid: String) {
            convos.removeAll { it.accountDid != accountDid }
        }

        override suspend fun deleteStateExcept(accountDid: String) {
            if (state?.accountDid != accountDid) state = null
        }
    }

    private fun pendingDm(
        id: String,
        kind: String = WatchPendingDmEntity.KIND_REPLY,
        status: String = WatchPendingDmEntity.STATUS_SENDING,
        sent: String? = null,
        target: String? = null,
        text: String = "On my way",
    ) = WatchPendingDmEntity(actionId = id, accountDid = alice, convoId = "c1", kind = kind, text = text, targetMessageId = target, sentMessageId = sent, createdAtMs = 100, status = status)

    @Test
    fun a_result_marks_a_quick_reply_sent_with_its_message_or_failed() =
        runTest {
            dmActions.rows += pendingDm("r1")
            dmActions.rows += pendingDm("r2")

            ingester.onActionResult(WearPaths.actionResult(alice, "r1"), result("r1", record = "msg-42"))
            ingester.onActionResult(WearPaths.actionResult(alice, "r2"), result("r2", failure = WearActionFailure.EXPIRED))

            val byId = dmActions.rows.associateBy { it.actionId }
            assertEquals(WatchPendingDmEntity.STATUS_SENT, byId.getValue("r1").status)
            assertEquals("msg-42", byId.getValue("r1").sentMessageId)
            assertEquals(WatchPendingDmEntity.STATUS_FAILED, byId.getValue("r2").status)
        }

    @Test
    fun a_dm_snapshot_stores_reactions_and_settles_the_replies_and_reactions_it_shows() =
        runTest {
            dmActions.rows += pendingDm("reply-shown", status = WatchPendingDmEntity.STATUS_SENT, sent = "c1-1")
            dmActions.rows += pendingDm("reply-not-yet", status = WatchPendingDmEntity.STATUS_SENT, sent = "elsewhere")
            dmActions.rows += pendingDm("react-shown", kind = WatchPendingDmEntity.KIND_REACT, status = WatchPendingDmEntity.STATUS_SENT, target = "c1-0", text = "❤️")
            dmActions.rows += pendingDm("still-sending")
            val withReaction =
                WearConvo(
                    convoId = "c1",
                    title = "Bob",
                    messages =
                        listOf(
                            WearMessage(id = "c1-0", fromViewer = false, text = "hi", sentAtMs = 0, reactions = listOf(WearReaction("❤️", 1, mine = true))),
                            WearMessage(id = "c1-1", fromViewer = true, text = "On my way", sentAtMs = 1),
                        ),
                )

            ingester.ingestDms(dmPath, dmSnapshot(10, withReaction))

            assertEquals(listOf("reply-not-yet", "still-sending"), dmActions.rows.map { it.actionId })
            assertTrue(
                dms.messages
                    .single { it.messageId == "c1-0" }
                    .reactions
                    .contains("❤️"),
            )
        }

    private class FakeDmActionDao : WatchDmActionDao {
        val rows = mutableListOf<WatchPendingDmEntity>()

        override fun observe(accountDid: String): Flow<List<WatchPendingDmEntity>> = MutableStateFlow(rows.toList())

        override suspend fun all(accountDid: String) = rows.filter { it.accountDid == accountDid }

        override suspend fun get(actionId: String) = rows.firstOrNull { it.actionId == actionId }

        override suspend fun insert(action: WatchPendingDmEntity) {
            rows += action
        }

        override suspend fun markSent(
            actionId: String,
            sentMessageId: String?,
        ) {
            val i = rows.indexOfFirst { it.actionId == actionId }
            if (i >= 0) rows[i] = rows[i].copy(status = WatchPendingDmEntity.STATUS_SENT, sentMessageId = sentMessageId)
        }

        override suspend fun markFailed(actionId: String) {
            val i = rows.indexOfFirst { it.actionId == actionId }
            if (i >= 0) rows[i] = rows[i].copy(status = WatchPendingDmEntity.STATUS_FAILED)
        }

        override suspend fun delete(actionIds: List<String>) {
            rows.removeAll { it.actionId in actionIds }
        }

        override suspend fun deleteExcept(accountDid: String) {
            rows.removeAll { it.accountDid != accountDid }
        }
    }

    private class FakeDao : WatchFeedDao {
        val feeds = MutableStateFlow(emptyList<WatchFeedEntity>())
        val posts = MutableStateFlow(emptyList<WatchPostEntity>())

        override fun observeFeeds(accountDid: String): Flow<List<WatchFeedEntity>> = feeds.map { list -> list.filter { it.accountDid == accountDid } }

        override fun observePosts(
            accountDid: String,
            feedKey: String,
        ): Flow<List<WatchPostEntity>> = posts.map { list -> list.filter { it.accountDid == accountDid && it.feedKey == feedKey } }

        override suspend fun publishedAtMs(
            accountDid: String,
            feedKey: String,
        ): Long? = feeds.value.firstOrNull { it.accountDid == accountDid && it.feedKey == feedKey }?.publishedAtMs

        override suspend fun upsertFeed(feed: WatchFeedEntity) {
            feeds.value = feeds.value.filterNot { it.accountDid == feed.accountDid && it.feedKey == feed.feedKey } + feed
        }

        override suspend fun insertPosts(posts: List<WatchPostEntity>) {
            this.posts.value = (this.posts.value + posts).sortedBy { it.position }
        }

        override suspend fun deletePosts(
            accountDid: String,
            feedKey: String,
        ) {
            posts.value = posts.value.filterNot { it.accountDid == accountDid && it.feedKey == feedKey }
        }

        override suspend fun deleteFeedRow(
            accountDid: String,
            feedKey: String,
        ) {
            feeds.value = feeds.value.filterNot { it.accountDid == accountDid && it.feedKey == feedKey }
        }

        override suspend fun deletePostsExcept(accountDid: String) {
            posts.value = posts.value.filter { it.accountDid == accountDid }
        }

        override suspend fun deleteFeedsExcept(accountDid: String) {
            feeds.value = feeds.value.filter { it.accountDid == accountDid }
        }
    }

    private class FakeImages : WatchImageStore {
        val written = mutableListOf<String>()
        var keptGeneration: Long? = -1
        val keptAccounts = mutableListOf<String?>()

        override suspend fun write(
            accountDid: String,
            feedKey: String,
            generation: Long,
            key: String,
            bytes: ByteArray,
        ): String {
            written += key
            return "/img/$generation/$key"
        }

        override suspend fun pruneFeed(
            accountDid: String,
            feedKey: String,
            keepGeneration: Long?,
        ) {
            keptGeneration = keepGeneration
        }

        override suspend fun keepOnlyAccount(accountDid: String?) {
            keptAccounts += accountDid
        }
    }
}
