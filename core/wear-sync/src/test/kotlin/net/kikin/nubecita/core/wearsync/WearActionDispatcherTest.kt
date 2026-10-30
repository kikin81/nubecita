package net.kikin.nubecita.core.wearsync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.analytics.InteractPost
import net.kikin.nubecita.core.analytics.PostAction
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.analytics.WatchAction
import net.kikin.nubecita.core.analytics.WatchActionOutcome
import net.kikin.nubecita.core.analytics.WatchActionType
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.testing.RecordingAnalyticsClient
import net.kikin.nubecita.core.wearcontract.WearAction
import net.kikin.nubecita.core.wearcontract.WearActionFailure
import net.kikin.nubecita.core.wearcontract.WearActionRequest
import net.kikin.nubecita.core.wearcontract.WearActionResult
import net.kikin.nubecita.core.wearcontract.WearCodec
import net.kikin.nubecita.core.wearcontract.WearDecodeResult
import net.kikin.nubecita.core.wearcontract.WearPaths
import net.kikin.nubecita.core.wearsync.internal.WearDataLayer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant

class WearActionDispatcherTest {
    private val alice = "did:plc:alice"
    private val watch = "watch-node"
    private val post = "at://did:plc:bob/app.bsky.feed.post/3k"
    private val session = MutableStateFlow<SessionState>(SessionState.SignedIn(handle = "alice.bsky.social", did = alice))
    private val dataLayer = RecordingDataLayer()
    private val analytics = RecordingAnalyticsClient()
    private val likes = RecordingHandler { Result.success("at://$alice/app.bsky.feed.like/1") }

    private fun dispatcher(vararg handlers: WearActionHandler = arrayOf(likes)) =
        WearActionDispatcher(
            dataLayer = dataLayer,
            sessionStateProvider =
                object : SessionStateProvider {
                    override val state: StateFlow<SessionState> = session

                    override suspend fun refresh() = Unit
                },
            handlers = handlers.toSet(),
            analytics = analytics,
            clock =
                object : Clock {
                    override fun now() = Instant.fromEpochMilliseconds(5_000L)
                },
        )

    private fun request(
        id: String = "a1",
        did: String = alice,
        action: WearAction = WearAction.Like(post, "cid"),
    ) = WearCodec.encode(WearActionRequest.serializer(), WearActionRequest(actionId = id, accountDid = did, createdAtMs = 1L, action = action))

    private fun onlyResult(): WearActionResult {
        val (path, json) = dataLayer.puts.single()
        assertEquals(WearPaths.actionResult(alice, "a1"), path)
        assertTrue(dataLayer.urgent.single())
        return (WearCodec.decode(WearActionResult.serializer(), json) as WearDecodeResult.Decoded).value
    }

    @Test
    fun successfulAction_writesAnOkResult_thenDeletesTheActionItem() =
        runTest {
            dispatcher().onActionItem(watch, WearPaths.action(alice, "a1"), request())

            val result = onlyResult()
            assertNull(result.failure)
            assertEquals("at://$alice/app.bsky.feed.like/1", result.recordUri)
            assertEquals(5_000L, result.completedAtMs)
            assertEquals(listOf(WearAction.Like(post, "cid")), likes.executed)
            assertEquals(listOf("result", "delete:$watch:${WearPaths.action(alice, "a1")}"), dataLayer.order)
            assertEquals(
                listOf(InteractPost(PostAction.Like, PostSurface.Watch), WatchAction(WatchActionType.Like, WatchActionOutcome.Ok)),
                analytics.events,
            )
        }

    @Test
    fun actionForAnotherAccount_isNotRun_andAnswersAccount() =
        runTest {
            val bob = "did:plc:bob"
            dispatcher().onActionItem(watch, WearPaths.action(bob, "a1"), request(did = bob))

            assertTrue(likes.executed.isEmpty())
            val (path, json) = dataLayer.puts.single()
            assertEquals(WearPaths.actionResult(bob, "a1"), path)
            val result = (WearCodec.decode(WearActionResult.serializer(), json) as WearDecodeResult.Decoded).value
            assertEquals(WearActionFailure.ACCOUNT, result.failure)
            assertEquals(listOf("delete:$watch:${WearPaths.action(bob, "a1")}"), dataLayer.deletes)
            assertEquals(listOf(WatchAction(WatchActionType.Like, WatchActionOutcome.Account)), analytics.events)
        }

    @Test
    fun signedOut_answersAccount() =
        runTest {
            session.value = SessionState.SignedOut

            dispatcher().onActionItem(watch, WearPaths.action(alice, "a1"), request())

            assertTrue(likes.executed.isEmpty())
            assertEquals(WearActionFailure.ACCOUNT, onlyResult().failure)
        }

    @Test
    fun actionWithNoHandler_answersUnsupported() =
        runTest {
            dispatcher().onActionItem(watch, WearPaths.action(alice, "a1"), request(action = WearAction.Unbookmark(post, "cid")))

            assertEquals(WearActionFailure.UNSUPPORTED, onlyResult().failure)
        }

    @Test
    fun unreadablePayload_answersUnsupported_andStillDeletesTheItem() =
        runTest {
            val future = """{"schemaVersion":1,"actionId":"a1","accountDid":"$alice","createdAtMs":1,"action":{"type":"quote","postUri":"$post"}}"""

            dispatcher().onActionItem(watch, WearPaths.action(alice, "a1"), future)

            assertEquals(WearActionFailure.UNSUPPORTED, onlyResult().failure)
            assertEquals(1, dataLayer.deletes.size)
            assertEquals(listOf(WatchAction(WatchActionType.Unknown, WatchActionOutcome.Unsupported)), analytics.events)
        }

    @Test
    fun payloadDisagreeingWithItsPath_isNotRun() =
        runTest {
            dispatcher().onActionItem(watch, WearPaths.action(alice, "a1"), request(id = "other"))

            assertTrue(likes.executed.isEmpty())
            assertEquals(WearActionFailure.UNSUPPORTED, onlyResult().failure)
        }

    @Test
    fun handlerFailure_andHandlerCrash_bothAnswerError() =
        runTest {
            val failing = RecordingHandler { Result.failure(IllegalStateException("post deleted")) }
            dispatcher(failing).onActionItem(watch, WearPaths.action(alice, "a1"), request())
            assertEquals(WearActionFailure.ERROR, onlyResult().failure)

            dataLayer.reset()
            val crashing = RecordingHandler { error("boom") }
            dispatcher(crashing).onActionItem(watch, WearPaths.action(alice, "a1"), request())
            assertEquals(WearActionFailure.ERROR, onlyResult().failure)
            assertEquals(List(2) { WatchAction(WatchActionType.Like, WatchActionOutcome.Error) }, analytics.events)
        }

    @Test
    fun redeliveredAction_withAResultAlready_isOnlyDeleted_neverRunTwice() =
        runTest {
            dataLayer.existingResults += WearPaths.actionResult(alice, "a1")

            dispatcher().onActionItem(watch, WearPaths.action(alice, "a1"), request())

            assertTrue(likes.executed.isEmpty())
            assertTrue(dataLayer.puts.isEmpty())
            assertEquals(listOf("delete:$watch:${WearPaths.action(alice, "a1")}"), dataLayer.deletes)
            assertTrue(analytics.events.isEmpty(), "a redelivered action was already counted")
        }

    @Test
    fun resultForALongerIdSharingThePrefix_doesNotCountAsHandled() =
        runTest {
            dataLayer.existingResults += WearPaths.actionResult(alice, "a10")

            dispatcher().onActionItem(watch, WearPaths.action(alice, "a1"), request())

            assertEquals(listOf(WearAction.Like(post, "cid")), likes.executed)
            assertNull(onlyResult().failure)
        }

    @Test
    fun anActionOlderThanItsHandlersMaxAge_isNotRun_andAnswersExpired() =
        runTest {
            val shortLived =
                object : WearActionHandler {
                    var ran = false

                    override fun canHandle(action: WearAction) = true

                    override fun maxAgeMs(action: WearAction) = 1_000L

                    override suspend fun execute(action: WearAction): Result<String?> {
                        ran = true
                        return Result.success(null)
                    }
                }
            // createdAtMs = 1, the clock reads 5_000: 4.999 s old, past a 1 s limit.
            dispatcher(shortLived).onActionItem(watch, WearPaths.action(alice, "a1"), request())

            assertEquals(WearActionFailure.EXPIRED, onlyResult().failure)
            assertTrue(!shortLived.ran)
            assertEquals(1, dataLayer.deletes.size)
            assertEquals(listOf(WatchAction(WatchActionType.Like, WatchActionOutcome.Expired)), analytics.events)
        }

    @Test
    fun aSuccessfulDmAction_isCountedAsAWatchAction_butNotAsAPostInteraction() =
        runTest {
            val dms =
                object : WearActionHandler {
                    override fun canHandle(action: WearAction) = action is WearAction.DmReply

                    override suspend fun execute(action: WearAction): Result<String?> = Result.success(null)
                }

            dispatcher(dms).onActionItem(watch, WearPaths.action(alice, "a1"), request(action = WearAction.DmReply("c1", "hi")))

            assertEquals(listOf(WatchAction(WatchActionType.DmReply, WatchActionOutcome.Ok)), analytics.events)
        }

    @Test
    fun nonActionPaths_areIgnored() =
        runTest {
            dispatcher().onActionItem(watch, WearPaths.actionResult(alice, "a1"), request())
            dispatcher().onActionItem(watch, WearPaths.SESSION, request())

            assertTrue(dataLayer.order.isEmpty())
        }

    private class RecordingHandler(
        private val outcome: () -> Result<String?>,
    ) : WearActionHandler {
        val executed = mutableListOf<WearAction>()

        override fun canHandle(action: WearAction) = action is WearAction.Like

        override suspend fun execute(action: WearAction): Result<String?> {
            executed += action
            return outcome()
        }
    }

    private class RecordingDataLayer : WearDataLayer {
        val puts = mutableListOf<Pair<String, String>>()
        val urgent = mutableListOf<Boolean>()
        val deletes = mutableListOf<String>()
        val order = mutableListOf<String>()
        val existingResults = mutableListOf<String>()

        fun reset() {
            puts.clear()
            urgent.clear()
            deletes.clear()
            order.clear()
        }

        override suspend fun hasWearAppNode() = true

        override suspend fun put(
            path: String,
            json: String,
            urgent: Boolean,
            assets: Map<String, ByteArray>,
        ) {
            puts += path to json
            this.urgent += urgent
            order += "result"
        }

        override suspend fun deleteUnder(pathPrefix: String) = Unit

        override suspend fun delete(path: String) = Unit

        override suspend fun deleteFromNode(
            nodeId: String,
            path: String,
        ) {
            deletes += "delete:$nodeId:$path"
            order += "delete:$nodeId:$path"
        }

        override suspend fun pathsUnder(pathPrefix: String): List<String> = existingResults.filter { it.startsWith(pathPrefix) }

        override suspend fun read(path: String): String? = null
    }
}
