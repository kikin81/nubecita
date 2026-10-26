package net.kikin.nubecita.core.wearsync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.wearcontract.WearAccount
import net.kikin.nubecita.core.wearcontract.WearCodec
import net.kikin.nubecita.core.wearcontract.WearDecodeResult
import net.kikin.nubecita.core.wearcontract.WearPaths
import net.kikin.nubecita.core.wearcontract.WearSessionSnapshot
import net.kikin.nubecita.core.wearsync.internal.WearDataLayer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class WearSessionPublisherTest {
    private val alice = SessionState.SignedIn(handle = "alice.bsky.social", did = "did:plc:alice")
    private val session = MutableStateFlow<SessionState>(SessionState.Loading)
    private val dataLayer = FakeWearDataLayer()
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(1_000)
        }

    private fun TestScope.publisher() =
        WearSessionPublisher(
            scope = backgroundScope,
            sessionStateProvider =
                object : SessionStateProvider {
                    override val state: StateFlow<SessionState> = session

                    override suspend fun refresh() = Unit
                },
            dataLayer = dataLayer,
            clock = clock,
        )

    @Test
    fun signedIn_withAWatch_publishesTheAccount_nonUrgent() =
        runTest(UnconfinedTestDispatcher()) {
            dataLayer.hasWatch = true
            publisher().start()

            session.value = alice
            runCurrent()

            val put = dataLayer.puts.single()
            assertEquals(WearPaths.SESSION, put.path)
            assertEquals(false, put.urgent)
            assertEquals(WearAccount(did = "did:plc:alice", handle = "alice.bsky.social"), put.session().account)
            assertEquals(1_000L, put.session().publishedAtMs)
        }

    @Test
    fun signedIn_withoutAWatch_publishesNothing() =
        runTest(UnconfinedTestDispatcher()) {
            dataLayer.hasWatch = false
            publisher().start()

            session.value = alice
            runCurrent()

            assertTrue(dataLayer.puts.isEmpty())
            assertTrue(dataLayer.deletedPrefixes.isEmpty())
        }

    @Test
    fun signedOut_wipesEverything_andPublishesSignedOut_evenWithoutAWatch() =
        runTest(UnconfinedTestDispatcher()) {
            dataLayer.hasWatch = false
            publisher().start()

            session.value = SessionState.SignedOut
            runCurrent()

            assertEquals(listOf(WearPaths.PREFIX), dataLayer.deletedPrefixes)
            val put = dataLayer.puts.single()
            assertEquals(WearPaths.SESSION, put.path)
            assertEquals(null, put.session().account)
        }

    @Test
    fun loading_publishesNothing() =
        runTest(UnconfinedTestDispatcher()) {
            dataLayer.hasWatch = true
            publisher().start()
            runCurrent()

            assertTrue(dataLayer.puts.isEmpty())
            assertTrue(dataLayer.deletedPrefixes.isEmpty())
        }

    @Test
    fun republish_sendsTheCurrentSession_urgently() =
        runTest(UnconfinedTestDispatcher()) {
            dataLayer.hasWatch = true
            session.value = alice
            val publisher = publisher()

            publisher.republish()

            val put = dataLayer.puts.single()
            assertEquals(true, put.urgent)
            assertEquals("did:plc:alice", put.session().account?.did)
        }

    @Test
    fun aDataLayerFailure_doesNotStopFollowingTheSession() =
        runTest(UnconfinedTestDispatcher()) {
            dataLayer.hasWatch = true
            dataLayer.failNextPut = true
            publisher().start()

            session.value = alice
            runCurrent()
            session.value = SessionState.SignedOut
            runCurrent()

            assertEquals(
                null,
                dataLayer.puts
                    .single()
                    .session()
                    .account,
            )
        }

    @Test
    fun start_isIdempotent() =
        runTest(UnconfinedTestDispatcher()) {
            dataLayer.hasWatch = true
            val publisher = publisher()
            publisher.start()
            publisher.start()

            session.value = alice
            runCurrent()

            assertEquals(1, dataLayer.puts.size)
        }

    private data class Put(
        val path: String,
        val json: String,
        val urgent: Boolean,
    ) {
        fun session(): WearSessionSnapshot = (WearCodec.decode(WearSessionSnapshot.serializer(), json) as WearDecodeResult.Decoded).value
    }

    private class FakeWearDataLayer : WearDataLayer {
        var hasWatch = false
        var failNextPut = false
        val puts = mutableListOf<Put>()
        val deletedPrefixes = mutableListOf<String>()

        override suspend fun hasWearAppNode(): Boolean = hasWatch

        override suspend fun put(
            path: String,
            json: String,
            urgent: Boolean,
        ) {
            if (failNextPut) {
                failNextPut = false
                error("Play services unavailable")
            }
            puts += Put(path, json, urgent)
        }

        override suspend fun deleteUnder(pathPrefix: String) {
            deletedPrefixes += pathPrefix
        }
    }
}
