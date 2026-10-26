package net.kikin.nubecita.core.wearsync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.feedcache.FeedKey
import net.kikin.nubecita.core.wearsync.internal.WearDataLayer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant

class WearSyncRequestHandlerTest {
    private val session = MutableStateFlow<SessionState>(SessionState.SignedIn(handle = "alice.bsky.social", did = "did:plc:alice"))
    private val dataLayer = RecordingDataLayer()
    private val feeds = RecordingFeedSync()
    private val refresh = RecordingTrigger()
    private var nowMs = 1_000_000L

    private fun TestScope.handler(): WearSyncRequestHandler {
        val provider =
            object : SessionStateProvider {
                override val state: StateFlow<SessionState> = session

                override suspend fun refresh() = Unit
            }
        val clock =
            object : Clock {
                override fun now() = Instant.fromEpochMilliseconds(nowMs)
            }
        return WearSyncRequestHandler(
            sessionPublisher = WearSessionPublisher(backgroundScope, provider, dataLayer, clock),
            sessionStateProvider = provider,
            feedSync = feeds,
            refreshTrigger = refresh,
            clock = clock,
        )
    }

    @Test
    fun republishes_session_and_feeds_urgently_and_asks_for_a_refresh() =
        runTest {
            handler().onSyncRequest()

            assertTrue(dataLayer.urgentPuts > 0)
            assertEquals(listOf("did:plc:alice" to true), feeds.published)
            assertEquals(1, refresh.calls)
        }

    @Test
    fun a_second_open_within_the_cooldown_republishes_but_does_not_refetch() =
        runTest {
            val handler = handler()
            handler.onSyncRequest()
            nowMs += 60_000L

            handler.onSyncRequest()

            assertEquals(2, feeds.published.size)
            assertEquals(1, refresh.calls)
        }

    @Test
    fun after_the_cooldown_it_refreshes_again() =
        runTest {
            val handler = handler()
            handler.onSyncRequest()
            nowMs += WearSyncRequestHandler.REFRESH_COOLDOWN_MS

            handler.onSyncRequest()

            assertEquals(2, refresh.calls)
        }

    @Test
    fun a_clock_that_moved_backwards_does_not_block_refreshes() =
        runTest {
            val handler = handler()
            handler.onSyncRequest()
            nowMs -= 60 * 60_000L // the clock jumped back an hour

            handler.onSyncRequest()

            assertEquals(2, refresh.calls)
        }

    @Test
    fun signed_out_only_republishes_the_session() =
        runTest {
            session.value = SessionState.SignedOut

            handler().onSyncRequest()

            assertTrue(feeds.published.isEmpty())
            assertEquals(0, refresh.calls)
        }

    @Test
    fun a_failed_feed_republish_still_asks_for_a_refresh() =
        runTest {
            feeds.fail = true

            handler().onSyncRequest()

            assertEquals(1, refresh.calls)
        }

    private class RecordingFeedSync : WearFeedSync {
        val published = mutableListOf<Pair<String, Boolean>>()
        var fail = false

        override suspend fun feedKeysToRefresh(accountDid: String): List<FeedKey> = emptyList()

        override suspend fun publish(
            accountDid: String,
            urgent: Boolean,
        ) {
            if (fail) error("Data Layer unavailable")
            published += accountDid to urgent
        }
    }

    private class RecordingTrigger : WearFeedRefreshTrigger {
        var calls = 0

        override suspend fun refreshFeedsNow() {
            calls++
        }
    }

    private class RecordingDataLayer : WearDataLayer {
        var urgentPuts = 0

        override suspend fun hasWearAppNode() = true

        override suspend fun put(
            path: String,
            json: String,
            urgent: Boolean,
            assets: Map<String, ByteArray>,
        ) {
            if (urgent) urgentPuts++
        }

        override suspend fun deleteUnder(pathPrefix: String) = Unit

        override suspend fun delete(path: String) = Unit

        override suspend fun deleteFromNode(
            nodeId: String,
            path: String,
        ) = Unit

        override suspend fun pathsUnder(pathPrefix: String): List<String> = emptyList()

        override suspend fun read(path: String): String? = null
    }
}
