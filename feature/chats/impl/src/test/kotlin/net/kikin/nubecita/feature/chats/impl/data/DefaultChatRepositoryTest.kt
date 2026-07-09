package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.runtime.NoAuth
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.feature.chats.impl.ConvoRowUi
import net.kikin.nubecita.feature.chats.impl.JoinRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DefaultChatRepository].
 *
 * Stands up a real [XrpcClient] over a Ktor [MockEngine] so the SDK's actual
 * `chat.bsky.convo.*` / `chat.bsky.group.*` / `app.bsky.actor.getProfiles`
 * codepaths (request encode → HTTP → response decode) run end-to-end against
 * deterministic wire JSON. Mirrors the harness in
 * [DefaultChatSettingsRepositoryTest]; the wire fixtures below match the
 * `atproto:models` 9.7.4 lexicon shapes.
 *
 * Scope: the repository's *orchestration* — endpoint wiring, the shared
 * `convosCache` / `requestConvosCache` StateFlow updates, session guards,
 * pagination cursors, `getProfiles` chunking, plus negative-path branches —
 * network-failure, signed-out guard, and cancellation-propagation (for the
 * methods that rethrow it) — across the repository's methods.
 * The wire→UI mappers and the cache-patch helpers are unit-tested separately
 * (ConvoMapperTest, MessageMapperTest, ConvoCachePatchTest, …), so this suite
 * spot-checks the mapped result rather than re-verifying field-by-field mapping.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultChatRepositoryTest {
    // -------------------------------------------------------------------------
    // refreshConvos / refreshRequestConvos — populate the shared caches
    // -------------------------------------------------------------------------

    @Test
    fun refreshConvos_success_populatesConvosCacheAndSendsAcceptedStatus() =
        runTest {
            val (engine, repo) =
                newRepo(signedIn = true) {
                    okJson(listConvosJson(directConvoJson(id = "c1", otherDid = "did:plc:alice", otherHandle = "alice.test")))
                }

            val result = repo.refreshConvos()

            assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()}")
            val rows = repo.observeConvos().value
            assertEquals(1, rows?.size)
            val direct = rows?.single() as ConvoRowUi.Direct
            assertEquals("did:plc:alice", direct.otherUserDid)
            // status=accepted is a query param on the listConvos GET.
            assertTrue(
                engine.requestHistory
                    .single()
                    .url.parameters["status"] == "accepted",
                "listConvos should filter status=accepted",
            )
            // The request cache stays untouched by an accepted-list refresh.
            assertNull(repo.observeRequestConvos().value)
        }

    @Test
    fun refreshRequestConvos_success_populatesRequestCacheAndSendsRequestStatus() =
        runTest {
            val (engine, repo) =
                newRepo(signedIn = true) {
                    okJson(listConvosJson(directConvoJson(id = "r1", otherDid = "did:plc:bob", otherHandle = "bob.test")))
                }

            val result = repo.refreshRequestConvos()

            assertTrue(result.isSuccess)
            assertEquals(1, repo.observeRequestConvos().value?.size)
            assertNull(repo.observeConvos().value, "accepted cache untouched by a request refresh")
            assertEquals(
                "request",
                engine.requestHistory
                    .single()
                    .url.parameters["status"],
            )
        }

    @Test
    fun refreshConvos_networkFailure_returnsFailureAndLeavesCacheUntouched() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }

            val result = repo.refreshConvos()

            assertTrue(result.isFailure)
            assertNull(repo.observeConvos().value, "a failed refresh must not clobber the cache")
        }

    @Test
    fun refreshConvos_signedOut_failsWithNoSessionAndSendsNoRequest() =
        runTest {
            val (engine, repo) = newRepo(signedIn = false) { error("must not hit the network signed out") }

            val result = repo.refreshConvos()

            assertNoSession(result)
            assertTrue(engine.requestHistory.isEmpty())
        }

    @Test
    fun refreshConvos_cancellation_propagates() {
        val (_, repo) = newRepo(signedIn = true) { throw CancellationException("cancelled") }
        assertThrows(CancellationException::class.java) { runBlocking { repo.refreshConvos() } }
    }

    // -------------------------------------------------------------------------
    // convo mutations (leave / accept / mute / markRead)
    // -------------------------------------------------------------------------

    @Test
    fun leaveConvo_success_returnsSuccess() =
        runTest {
            val (engine, repo) = newRepo(signedIn = true) { okJson("""{"convoId":"c1","rev":"5"}""") }

            assertTrue(repo.leaveConvo("c1").isSuccess)
            assertTrue(
                engine.requestHistory
                    .single()
                    .url.encodedPath
                    .endsWith("chat.bsky.convo.leaveConvo"),
            )
        }

    @Test
    fun leaveConvo_networkFailure_returnsFailure() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }
            assertTrue(repo.leaveConvo("c1").isFailure)
        }

    @Test
    fun leaveConvo_cancellation_propagates() {
        val (_, repo) = newRepo(signedIn = true) { throw CancellationException("cancelled") }
        assertThrows(CancellationException::class.java) { runBlocking { repo.leaveConvo("c1") } }
    }

    @Test
    fun acceptConvo_success_returnsSuccess() =
        runTest {
            // AcceptConvoResponse.rev is optional, so an empty object decodes.
            val (_, repo) = newRepo(signedIn = true) { okJson("{}") }
            assertTrue(repo.acceptConvo("c1").isSuccess)
        }

    @Test
    fun acceptConvo_networkFailure_returnsFailure() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }
            assertTrue(repo.acceptConvo("c1").isFailure)
        }

    @Test
    fun setMuted_true_success_returnsSuccess() =
        runTest {
            val (engine, repo) =
                newRepo(signedIn = true) { okJson("""{"convo":${directConvoJson("c1", "did:plc:alice", "alice.test", muted = true)}}""") }

            assertTrue(repo.setMuted("c1", muted = true).isSuccess)
            // endsWith the fully-qualified method: "muteConvo" is a substring of
            // "unmuteConvo", so a `contains` check wouldn't distinguish the two.
            assertTrue(
                engine.requestHistory
                    .single()
                    .url.encodedPath
                    .endsWith("chat.bsky.convo.muteConvo"),
            )
        }

    @Test
    fun setMuted_false_success_hitsUnmuteEndpoint() =
        runTest {
            val (engine, repo) =
                newRepo(signedIn = true) { okJson("""{"convo":${directConvoJson("c1", "did:plc:alice", "alice.test")}}""") }

            assertTrue(repo.setMuted("c1", muted = false).isSuccess)
            assertTrue(
                engine.requestHistory
                    .single()
                    .url.encodedPath
                    .endsWith("chat.bsky.convo.unmuteConvo"),
            )
        }

    @Test
    fun setMuted_networkFailure_returnsFailure() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }
            assertTrue(repo.setMuted("c1", muted = true).isFailure)
        }

    @Test
    fun markConvoRead_success_returnsSuccess() =
        runTest {
            val (_, repo) =
                newRepo(signedIn = true) { okJson("""{"convo":${directConvoJson("c1", "did:plc:alice", "alice.test")}}""") }
            assertTrue(repo.markConvoRead("c1").isSuccess)
        }

    @Test
    fun markConvoRead_networkFailure_returnsFailure() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }
            assertTrue(repo.markConvoRead("c1").isFailure)
        }

    // -------------------------------------------------------------------------
    // resolveConvo / getConvo
    // -------------------------------------------------------------------------

    @Test
    fun resolveConvo_success_picksTheOtherMember() =
        runTest {
            val (_, repo) =
                newRepo(signedIn = true) {
                    okJson(
                        """{"convo":${
                            convoJson(
                                id = "c1",
                                members =
                                    listOf(
                                        profileBasicJson(VIEWER_DID, "me.test"),
                                        profileBasicJson("did:plc:alice", "alice.test", displayName = "Alice"),
                                    ),
                            )
                        }}""",
                    )
                }

            val resolution = repo.resolveConvo("did:plc:alice").getOrThrow()

            assertEquals("c1", resolution.convoId)
            assertEquals("alice.test", resolution.otherUserHandle)
            assertEquals("Alice", resolution.otherUserDisplayName)
        }

    @Test
    fun resolveConvo_signedOut_failsWithNoSession() =
        runTest {
            val (engine, repo) = newRepo(signedIn = false) { error("no network") }
            assertNoSession(repo.resolveConvo("did:plc:alice"))
            assertTrue(engine.requestHistory.isEmpty())
        }

    @Test
    fun getConvo_success_returnsConvoId() =
        runTest {
            val (_, repo) =
                newRepo(signedIn = true) { okJson("""{"convo":${directConvoJson("c1", "did:plc:alice", "alice.test")}}""") }

            assertEquals("c1", repo.getConvo("c1").getOrThrow().convoId)
        }

    @Test
    fun getConvo_signedOut_failsWithNoSession() =
        runTest {
            val (engine, repo) = newRepo(signedIn = false) { error("no network") }
            assertNoSession(repo.getConvo("c1"))
            assertTrue(engine.requestHistory.isEmpty())
        }

    @Test
    fun getConvo_cancellation_propagates() {
        val (_, repo) = newRepo(signedIn = true) { throw CancellationException("cancelled") }
        assertThrows(CancellationException::class.java) { runBlocking { repo.getConvo("c1") } }
    }

    // -------------------------------------------------------------------------
    // getMessages / sendMessage
    // -------------------------------------------------------------------------

    @Test
    fun getMessages_success_mapsMessagesAndCursor() =
        runTest {
            val (engine, repo) =
                newRepo(signedIn = true) {
                    okJson(
                        """{"cursor":"next","messages":[${
                            messageViewJson(id = "m1", senderDid = "did:plc:alice", text = "hi")
                        }]}""",
                    )
                }

            val page = repo.getMessages(convoId = "c1", cursor = "cur", limit = 30).getOrThrow()

            assertEquals(1, page.messages.size)
            assertEquals("hi", page.messages.single().text)
            assertEquals("next", page.nextCursor)
            assertEquals(
                "cur",
                engine.requestHistory
                    .single()
                    .url.parameters["cursor"],
            )
        }

    @Test
    fun getMessages_signedOut_failsWithNoSession() =
        runTest {
            val (engine, repo) = newRepo(signedIn = false) { error("no network") }
            assertNoSession(repo.getMessages("c1", cursor = null, limit = 30))
            assertTrue(engine.requestHistory.isEmpty())
        }

    @Test
    fun getMessages_networkFailure_returnsFailure() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }
            assertTrue(repo.getMessages("c1", cursor = null, limit = 30).isFailure)
        }

    @Test
    fun sendMessage_success_returnsMappedMessage() =
        runTest {
            val (_, repo) =
                newRepo(signedIn = true) { okJson(messageViewJson(id = "m1", senderDid = VIEWER_DID, text = "hello")) }

            val message = repo.sendMessage(convoId = "c1", text = "hello", replyToMessageId = null).getOrThrow()

            assertEquals("hello", message.text)
            assertEquals("m1", message.id)
            assertTrue(message.isOutgoing, "a message from the viewer is outgoing")
        }

    @Test
    fun sendMessage_signedOut_failsWithNoSession() =
        runTest {
            val (engine, repo) = newRepo(signedIn = false) { error("no network") }
            assertNoSession(repo.sendMessage("c1", "hi", replyToMessageId = null))
            assertTrue(engine.requestHistory.isEmpty())
        }

    @Test
    fun sendMessage_networkFailure_returnsFailure() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }
            assertTrue(repo.sendMessage("c1", "hi", replyToMessageId = null).isFailure)
        }

    // -------------------------------------------------------------------------
    // addReaction / removeReaction
    // -------------------------------------------------------------------------

    @Test
    fun addReaction_success_returnsMappedMessage() =
        runTest {
            val (_, repo) =
                newRepo(signedIn = true) { okJson("""{"message":${messageViewJson("m1", "did:plc:alice", "hi")}}""") }

            assertEquals("m1", repo.addReaction("c1", "m1", "👍").getOrThrow().id)
        }

    @Test
    fun addReaction_networkFailure_returnsFailure() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }
            assertTrue(repo.addReaction("c1", "m1", "👍").isFailure)
        }

    @Test
    fun addReaction_cancellation_propagates() {
        val (_, repo) = newRepo(signedIn = true) { throw CancellationException("cancelled") }
        assertThrows(CancellationException::class.java) { runBlocking { repo.addReaction("c1", "m1", "👍") } }
    }

    @Test
    fun removeReaction_success_returnsMappedMessage() =
        runTest {
            val (_, repo) =
                newRepo(signedIn = true) { okJson("""{"message":${messageViewJson("m1", "did:plc:alice", "hi")}}""") }

            assertEquals("m1", repo.removeReaction("c1", "m1", "👍").getOrThrow().id)
        }

    @Test
    fun removeReaction_signedOut_failsWithNoSession() =
        runTest {
            val (engine, repo) = newRepo(signedIn = false) { error("no network") }
            assertNoSession(repo.removeReaction("c1", "m1", "👍"))
            assertTrue(engine.requestHistory.isEmpty())
        }

    // -------------------------------------------------------------------------
    // getProfiles — chunking + best-effort per-chunk degradation
    // -------------------------------------------------------------------------

    @Test
    fun getProfiles_emptyInput_returnsEmptyWithoutNetwork() =
        runTest {
            val (engine, repo) = newRepo(signedIn = true) { error("must not hit the network for empty dids") }

            val result = repo.getProfiles(emptyList())

            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().isEmpty())
            assertTrue(engine.requestHistory.isEmpty())
        }

    @Test
    fun getProfiles_over25Dids_pagesIntoTwoChunksAndToleratesPerChunkFailure() =
        runTest {
            // 26 dids → chunked at 25 → two getProfiles calls. Both error, but the
            // per-chunk catch swallows each, so the overall result is a success with
            // an empty list (best-effort degradation), having made exactly 2 calls.
            val (engine, repo) = newRepo(signedIn = true) { errorJson() }

            val dids = (1..26).map { "did:plc:actor$it" }
            val result = repo.getProfiles(dids)

            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().isEmpty())
            assertEquals(2, engine.requestHistory.size, "should page 26 dids into 2 getProfiles calls")
        }

    // -------------------------------------------------------------------------
    // getConvoMembers — guard + failure + cancellation (mapper covered elsewhere)
    // -------------------------------------------------------------------------

    @Test
    fun getConvoMembers_signedOut_failsWithNoSession() =
        runTest {
            val (engine, repo) = newRepo(signedIn = false) { error("no network") }
            assertNoSession(repo.getConvoMembers("c1", cursor = null))
            assertTrue(engine.requestHistory.isEmpty())
        }

    @Test
    fun getConvoMembers_networkFailure_returnsFailure() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }
            assertTrue(repo.getConvoMembers("c1", cursor = null).isFailure)
        }

    @Test
    fun getConvoMembers_cancellation_propagates() {
        val (_, repo) = newRepo(signedIn = true) { throw CancellationException("cancelled") }
        assertThrows(CancellationException::class.java) { runBlocking { repo.getConvoMembers("c1", cursor = null) } }
    }

    // -------------------------------------------------------------------------
    // group service — createGroup + member/link/join management
    // (failure + cancellation branch coverage; success mappers covered elsewhere)
    // -------------------------------------------------------------------------

    @Test
    fun createGroup_networkFailure_returnsFailure() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }
            assertTrue(repo.createGroup(name = "G", dids = listOf("did:plc:a")).isFailure)
        }

    @Test
    fun createGroup_cancellation_propagates() {
        val (_, repo) = newRepo(signedIn = true) { throw CancellationException("cancelled") }
        assertThrows(CancellationException::class.java) {
            runBlocking { repo.createGroup(name = "G", dids = listOf("did:plc:a")) }
        }
    }

    @Test
    fun addMembers_networkFailure_returnsFailure() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }
            assertTrue(repo.addMembers("c1", listOf("did:plc:a")).isFailure)
        }

    @Test
    fun removeMembers_networkFailure_returnsFailure() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }
            assertTrue(repo.removeMembers("c1", listOf("did:plc:a")).isFailure)
        }

    @Test
    fun addMembers_cancellation_propagates() {
        val (_, repo) = newRepo(signedIn = true) { throw CancellationException("cancelled") }
        assertThrows(CancellationException::class.java) { runBlocking { repo.addMembers("c1", listOf("did:plc:a")) } }
    }

    @Test
    fun getJoinRequests_networkFailure_returnsFailure() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }
            assertTrue(repo.getJoinRequests("c1", cursor = null).isFailure)
        }

    @Test
    fun getJoinRequests_cancellation_propagates() {
        val (_, repo) = newRepo(signedIn = true) { throw CancellationException("cancelled") }
        assertThrows(CancellationException::class.java) { runBlocking { repo.getJoinRequests("c1", cursor = null) } }
    }

    @Test
    fun approveJoinRequest_networkFailure_returnsFailure() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }
            assertTrue(repo.approveJoinRequest("c1", "did:plc:a").isFailure)
        }

    @Test
    fun rejectJoinRequest_networkFailure_returnsFailure() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }
            assertTrue(repo.rejectJoinRequest("c1", "did:plc:a").isFailure)
        }

    @Test
    fun getJoinLink_networkFailure_returnsFailure() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }
            assertTrue(repo.getJoinLink("c1").isFailure)
        }

    @Test
    fun getJoinLink_cancellation_propagates() {
        val (_, repo) = newRepo(signedIn = true) { throw CancellationException("cancelled") }
        assertThrows(CancellationException::class.java) { runBlocking { repo.getJoinLink("c1") } }
    }

    @Test
    fun createJoinLink_networkFailure_returnsFailure() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }
            assertTrue(repo.createJoinLink("c1", JoinRule.Anyone, requireApproval = false).isFailure)
        }

    @Test
    fun editJoinLink_networkFailure_returnsFailure() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }
            assertTrue(repo.editJoinLink("c1", JoinRule.Anyone, requireApproval = true).isFailure)
        }

    @Test
    fun enableJoinLink_networkFailure_returnsFailure() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }
            assertTrue(repo.enableJoinLink("c1").isFailure)
        }

    @Test
    fun disableJoinLink_networkFailure_returnsFailure() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }
            assertTrue(repo.disableJoinLink("c1").isFailure)
        }

    @Test
    fun createJoinLink_cancellation_propagates() {
        val (_, repo) = newRepo(signedIn = true) { throw CancellationException("cancelled") }
        assertThrows(CancellationException::class.java) {
            runBlocking { repo.createJoinLink("c1", JoinRule.Anyone, requireApproval = false) }
        }
    }

    @Test
    fun getGroupPublicInfo_networkFailure_returnsFailure() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }
            assertTrue(repo.getGroupPublicInfo("code123").isFailure)
        }

    @Test
    fun getGroupPublicInfo_cancellation_propagates() {
        val (_, repo) = newRepo(signedIn = true) { throw CancellationException("cancelled") }
        assertThrows(CancellationException::class.java) { runBlocking { repo.getGroupPublicInfo("code123") } }
    }

    @Test
    fun requestJoin_networkFailure_returnsFailure() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }
            assertTrue(repo.requestJoin("code123").isFailure)
        }

    @Test
    fun requestJoin_cancellation_propagates() {
        val (_, repo) = newRepo(signedIn = true) { throw CancellationException("cancelled") }
        assertThrows(CancellationException::class.java) { runBlocking { repo.requestJoin("code123") } }
    }

    @Test
    fun getLog_networkFailure_returnsFailure() =
        runTest {
            val (_, repo) = newRepo(signedIn = true) { errorJson() }
            assertTrue(repo.getLog(cursor = null).isFailure)
        }

    // -------------------------------------------------------------------------
    // Harness helpers
    // -------------------------------------------------------------------------

    private fun assertNoSession(result: Result<*>) {
        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull() is NoSessionException,
            "expected NoSessionException, got ${result.exceptionOrNull()}",
        )
    }

    private fun newRepo(
        signedIn: Boolean,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Pair<MockEngine, DefaultChatRepository> {
        val engine = MockEngine(handler)
        val xrpcClient =
            XrpcClient(
                baseUrl = "https://test.invalid",
                httpClient = HttpClient(engine),
                authProvider = NoAuth,
            )
        val repo =
            DefaultChatRepository(
                xrpcClientProvider =
                    object : XrpcClientProvider {
                        // Mirror production DefaultXrpcClientProvider: no session → throw,
                        // so the signed-out guard holds even if a method's call order changes.
                        override suspend fun authenticated(): XrpcClient = if (signedIn) xrpcClient else throw NoSessionException()
                    },
                sessionStateProvider =
                    object : SessionStateProvider {
                        override val state: StateFlow<SessionState> =
                            MutableStateFlow(
                                if (signedIn) SessionState.SignedIn(handle = "me.test", did = VIEWER_DID) else SessionState.SignedOut,
                            )

                        override suspend fun refresh() = Unit
                    },
                dispatcher = UnconfinedTestDispatcher(),
            )
        return engine to repo
    }

    private fun MockRequestHandleScope.okJson(json: String): HttpResponseData =
        respond(
            ByteReadChannel(json),
            HttpStatusCode.OK,
            headersOf("Content-Type", ContentType.Application.Json.toString()),
        )

    private fun MockRequestHandleScope.errorJson(): HttpResponseData =
        respond(
            ByteReadChannel("""{"error":"InternalServerError","message":"boom"}"""),
            HttpStatusCode.InternalServerError,
            headersOf("Content-Type", ContentType.Application.Json.toString()),
        )

    // --- wire JSON builders (atproto:models 9.7.4 chat lexicon shapes) --------

    private fun profileBasicJson(
        did: String,
        handle: String,
        displayName: String? = null,
    ): String {
        val name = displayName?.let { ""","displayName":"$it"""" } ?: ""
        return """{"${'$'}type":"chat.bsky.actor.defs#profileViewBasic","did":"$did","handle":"$handle"$name}"""
    }

    private fun convoJson(
        id: String,
        members: List<String>,
        muted: Boolean = false,
        unreadCount: Int = 0,
    ): String =
        """{"${'$'}type":"chat.bsky.convo.defs#convoView","id":"$id","members":[${members.joinToString(",")}],""" +
            """"muted":$muted,"rev":"1","unreadCount":$unreadCount}"""

    private fun directConvoJson(
        id: String,
        otherDid: String,
        otherHandle: String,
        muted: Boolean = false,
    ): String =
        convoJson(
            id = id,
            members = listOf(profileBasicJson(VIEWER_DID, "me.test"), profileBasicJson(otherDid, otherHandle)),
            muted = muted,
        )

    private fun listConvosJson(
        vararg convoJson: String,
        cursor: String? = null,
    ): String {
        val cursorField = cursor?.let { """"cursor":"$it",""" } ?: ""
        return """{$cursorField"convos":[${convoJson.joinToString(",")}]}"""
    }

    private fun messageViewJson(
        id: String,
        senderDid: String,
        text: String,
        sentAt: String = "2026-01-01T00:00:00Z",
    ): String =
        """{"${'$'}type":"chat.bsky.convo.defs#messageView","id":"$id","rev":"1",""" +
            """"sender":{"did":"$senderDid"},"sentAt":"$sentAt","text":"$text"}"""

    private companion object {
        const val VIEWER_DID = "did:plc:viewer000"
    }
}
