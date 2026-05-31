package net.kikin.nubecita.feature.profile.impl.data

import app.cash.turbine.test
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
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.image.EncodedImage
import net.kikin.nubecita.core.image.ImageEncoder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DefaultProfileRepository.updateProfile].
 *
 * Strategy mirrors `:core:posting/DefaultPostingRepositoryTest`: stand
 * up a real [XrpcClient] over a Ktor [MockEngine] so the SDK's full
 * `getRecord → (uploadBlob) → putRecord` codepath runs end-to-end
 * against deterministic responses. Asserts work on the recorded
 * request history (`engine.requestHistory`) and on captured request
 * bodies (substring checks, not JSON parsing — same convention as the
 * posting test's reply-mode assertions).
 *
 * The [ImageEncoder] is faked with a pass-through (or byte-rewriting)
 * stub so tests don't need an Android Bitmap runtime.
 *
 * Coverage (from the profile-editing design's "Testing" section):
 *   - Happy path: getRecord → merge edited displayName/description →
 *     putRecord with swapRecord = present(cid); untouched fields
 *     (pinnedPost) preserved, edited fields set.
 *   - Create-if-missing: getRecord 400 RecordNotFound → putRecord
 *     WITHOUT swapRecord, with `$type` set.
 *   - Swap conflict: putRecord 400 InvalidSwap → ProfileUpdateError.SwapConflict.
 *   - Image handling: Replaced avatar uploads ENCODED bytes once and
 *     lands the returned blob ref; Unchanged banner preserves its blob
 *     ref with NO uploadBlob; Removed drops the key.
 *   - Field preservation: an unknown/extra key survives into putRecord.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultProfileRepositoryUpdateProfileTest {
    private val testDid = "did:plc:abc"
    private val selfUri = "at://$testDid/app.bsky.actor.profile/self"

    @Test
    fun happyPath_mergesEditedFields_preservesUntouched_swapsOnCid() =
        runTest {
            val putBody = CompletableDeferred<String>()
            val (engine, repo) =
                newRepo(signedIn = true) { request ->
                    when {
                        request.isGetRecord() ->
                            okJson(
                                getRecordJson(
                                    cid = "bafcid",
                                    value =
                                        """{"${'$'}type":"app.bsky.actor.profile",""" +
                                            """"displayName":"old name","description":"old bio",""" +
                                            """"pinnedPost":{"uri":"at://$testDid/app.bsky.feed.post/pinned","cid":"bafpin"}}""",
                                ),
                            )
                        request.isPutRecord() -> {
                            putBody.complete(request.body.toBodyString())
                            okJson(putRecordJson(cid = "bafnew"))
                        }
                        else -> error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.updateProfile(
                    displayName = "new name",
                    description = "new bio",
                    avatar = ImageChange.Unchanged,
                    banner = ImageChange.Unchanged,
                )

            assertTrue(result.isSuccess, "expected success, got: ${result.exceptionOrNull()}")
            // Exactly getRecord + putRecord, no blob uploads.
            assertEquals(2, engine.requestHistory.size)
            assertTrue(engine.requestHistory.none { it.url.encodedPath.endsWith("uploadBlob") })

            val body = putBody.await()
            // Edited fields overridden on the wire.
            assertTrue(body.contains("\"displayName\":\"new name\""), "displayName not merged: $body")
            assertTrue(body.contains("\"description\":\"new bio\""), "description not merged: $body")
            assertFalse(body.contains("old name"), "stale displayName leaked: $body")
            assertFalse(body.contains("old bio"), "stale description leaked: $body")
            // Untouched managed-adjacent field preserved verbatim.
            assertTrue(
                body.contains("at://$testDid/app.bsky.feed.post/pinned"),
                "pinnedPost must be preserved: $body",
            )
            // Compare-and-swap on the fetched record CID.
            assertTrue(body.contains("\"swapRecord\":\"bafcid\""), "swapRecord(cid) missing: $body")
        }

    @Test
    fun successfulSave_emitsOwnProfileUpdateSignal() =
        runTest {
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    when {
                        request.isGetRecord() ->
                            okJson(getRecordJson(cid = "bafcid", value = """{"${'$'}type":"app.bsky.actor.profile"}"""))
                        request.isPutRecord() -> okJson(putRecordJson(cid = "bafnew"))
                        else -> error("Unexpected request: ${request.url}")
                    }
                }

            // replay = 0, so collect BEFORE the write; the own-profile screen
            // refetches its header off this emission.
            repo.ownProfileUpdates.test {
                val result =
                    repo.updateProfile(
                        displayName = "new name",
                        description = "new bio",
                        avatar = ImageChange.Unchanged,
                        banner = ImageChange.Unchanged,
                    )
                assertTrue(result.isSuccess, "expected success, got: ${result.exceptionOrNull()}")
                assertEquals(Unit, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun createIfMissing_recordNotFound_putsWithoutSwap_andSetsType() =
        runTest {
            val putBody = CompletableDeferred<String>()
            val (engine, repo) =
                newRepo(signedIn = true) { request ->
                    when {
                        request.isGetRecord() ->
                            errorJson(HttpStatusCode.BadRequest, "RecordNotFound", "Could not locate record")
                        request.isPutRecord() -> {
                            putBody.complete(request.body.toBodyString())
                            okJson(putRecordJson(cid = "bafnew"))
                        }
                        else -> error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.updateProfile(
                    displayName = "fresh name",
                    description = null,
                    avatar = ImageChange.Unchanged,
                    banner = ImageChange.Unchanged,
                )

            assertTrue(result.isSuccess, "expected success, got: ${result.exceptionOrNull()}")
            val body = putBody.await()
            // create-if-missing: $type stamped, displayName set, and NO
            // swapRecord (we have no prior CID to compare against).
            assertTrue(body.contains("\"\$type\":\"app.bsky.actor.profile\""), "\$type missing on create: $body")
            assertTrue(body.contains("\"displayName\":\"fresh name\""), "displayName missing: $body")
            assertFalse(body.contains("swapRecord"), "swapRecord must be omitted on create path: $body")
        }

    @Test
    fun swapConflict_invalidSwap_surfacesDistinctSwapConflict() =
        runTest {
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    when {
                        request.isGetRecord() ->
                            okJson(getRecordJson(cid = "stalecid", value = """{"displayName":"x"}"""))
                        request.isPutRecord() ->
                            errorJson(HttpStatusCode.BadRequest, "InvalidSwap", "Record was at a different cid")
                        else -> error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.updateProfile(
                    displayName = "y",
                    description = null,
                    avatar = ImageChange.Unchanged,
                    banner = ImageChange.Unchanged,
                )

            assertTrue(result.isFailure)
            assertEquals(
                ProfileUpdateError.SwapConflict,
                result.exceptionOrNull(),
                "expected distinct SwapConflict, got: ${result.exceptionOrNull()}",
            )
        }

    @Test
    fun replacedAvatar_uploadsEncodedBytesOnce_landsBlobRef() =
        runTest {
            val rawBytes = byteArrayOf(1, 2, 3, 4)
            val encodedBytes = byteArrayOf(9, 9, 9)
            val encoderInputs = mutableListOf<Pair<Int, String>>()
            val rewriteEncoder =
                object : ImageEncoder {
                    override suspend fun encodeForUpload(
                        bytes: ByteArray,
                        sourceMimeType: String,
                        maxBytes: Long,
                    ): EncodedImage {
                        encoderInputs += bytes.size to sourceMimeType
                        return EncodedImage(bytes = encodedBytes, mimeType = "image/webp")
                    }
                }
            val putBody = CompletableDeferred<String>()

            val (engine, repo) =
                newRepo(signedIn = true, encoder = rewriteEncoder) { request ->
                    when {
                        request.isGetRecord() ->
                            okJson(getRecordJson(cid = "bafcid", value = """{"displayName":"name"}"""))
                        request.isUploadBlob() ->
                            okJson(uploadBlobJson(link = "bafavatar", mime = "image/webp", size = 3))
                        request.isPutRecord() -> {
                            putBody.complete(request.body.toBodyString())
                            okJson(putRecordJson(cid = "bafnew"))
                        }
                        else -> error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.updateProfile(
                    displayName = null,
                    description = null,
                    avatar = ImageChange.Replaced(bytes = rawBytes, mimeType = "image/jpeg"),
                    banner = ImageChange.Unchanged,
                )

            assertTrue(result.isSuccess, "expected success, got: ${result.exceptionOrNull()}")
            // Encoder saw the raw bytes + picker MIME, exactly once.
            assertEquals(listOf(rawBytes.size to "image/jpeg"), encoderInputs)
            val uploads = engine.requestHistory.filter { it.url.encodedPath.endsWith("uploadBlob") }
            assertEquals(1, uploads.size, "exactly one uploadBlob expected")
            // uploadBlob carried the ENCODED bytes, not the raw ones.
            assertEquals(
                encodedBytes.toList(),
                uploads
                    .single()
                    .body
                    .toBodyBytes()
                    .toList(),
            )
            assertEquals(ContentType.parse("image/webp"), uploads.single().body.contentType)
            // The returned blob ref landed in the record under "avatar".
            val body = putBody.await()
            assertTrue(body.contains("\"avatar\""), "avatar key missing: $body")
            assertTrue(body.contains("bafavatar"), "uploaded blob ref missing in record: $body")
        }

    @Test
    fun unchangedBanner_preservesExistingBlobRef_noUpload() =
        runTest {
            val putBody = CompletableDeferred<String>()
            val (engine, repo) =
                newRepo(signedIn = true) { request ->
                    when {
                        request.isGetRecord() ->
                            okJson(
                                getRecordJson(
                                    cid = "bafcid",
                                    value =
                                        """{"displayName":"name",""" +
                                            """"banner":{"${'$'}type":"blob","ref":{"${'$'}link":"bafexistingbanner"},""" +
                                            """"mimeType":"image/jpeg","size":1234}}""",
                                ),
                            )
                        request.isPutRecord() -> {
                            putBody.complete(request.body.toBodyString())
                            okJson(putRecordJson(cid = "bafnew"))
                        }
                        else -> error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.updateProfile(
                    displayName = "name",
                    description = null,
                    avatar = ImageChange.Unchanged,
                    banner = ImageChange.Unchanged,
                )

            assertTrue(result.isSuccess, "expected success, got: ${result.exceptionOrNull()}")
            // No uploadBlob for an unchanged image.
            assertTrue(engine.requestHistory.none { it.url.encodedPath.endsWith("uploadBlob") })
            val body = putBody.await()
            // Existing banner blob ref preserved verbatim.
            assertTrue(body.contains("bafexistingbanner"), "existing banner blob ref dropped: $body")
        }

    @Test
    fun removedBanner_dropsKeyFromRecord() =
        runTest {
            val putBody = CompletableDeferred<String>()
            val (engine, repo) =
                newRepo(signedIn = true) { request ->
                    when {
                        request.isGetRecord() ->
                            okJson(
                                getRecordJson(
                                    cid = "bafcid",
                                    value =
                                        """{"displayName":"name",""" +
                                            """"banner":{"${'$'}type":"blob","ref":{"${'$'}link":"bafexistingbanner"},""" +
                                            """"mimeType":"image/jpeg","size":1234}}""",
                                ),
                            )
                        request.isPutRecord() -> {
                            putBody.complete(request.body.toBodyString())
                            okJson(putRecordJson(cid = "bafnew"))
                        }
                        else -> error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.updateProfile(
                    displayName = "name",
                    description = null,
                    avatar = ImageChange.Unchanged,
                    banner = ImageChange.Removed,
                )

            assertTrue(result.isSuccess, "expected success, got: ${result.exceptionOrNull()}")
            assertTrue(engine.requestHistory.none { it.url.encodedPath.endsWith("uploadBlob") })
            val body = putBody.await()
            // Removed → key (and its blob ref) gone from the record.
            assertFalse(body.contains("\"banner\""), "removed banner key must be dropped: $body")
            assertFalse(body.contains("bafexistingbanner"), "removed banner blob ref leaked: $body")
        }

    @Test
    fun unknownField_inFetchedRecord_survivesIntoPutRecord() =
        runTest {
            val putBody = CompletableDeferred<String>()
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    when {
                        request.isGetRecord() ->
                            okJson(
                                getRecordJson(
                                    cid = "bafcid",
                                    value =
                                        """{"displayName":"name","createdAt":"2024-01-01T00:00:00Z",""" +
                                            """"pronouns":"they/them","someFutureKey":{"nested":["a","b"]}}""",
                                ),
                            )
                        request.isPutRecord() -> {
                            putBody.complete(request.body.toBodyString())
                            okJson(putRecordJson(cid = "bafnew"))
                        }
                        else -> error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.updateProfile(
                    displayName = "edited",
                    description = null,
                    avatar = ImageChange.Unchanged,
                    banner = ImageChange.Unchanged,
                )

            assertTrue(result.isSuccess, "expected success, got: ${result.exceptionOrNull()}")
            val body = putBody.await()
            // Every non-managed key survives byte-for-byte.
            assertTrue(body.contains("\"createdAt\":\"2024-01-01T00:00:00Z\""), "createdAt dropped: $body")
            assertTrue(body.contains("\"pronouns\":\"they/them\""), "pronouns dropped: $body")
            assertTrue(body.contains("\"someFutureKey\""), "unknown key dropped: $body")
            assertTrue(body.contains("\"nested\""), "unknown nested key dropped: $body")
            // And the edit still landed.
            assertTrue(body.contains("\"displayName\":\"edited\""), "edit not applied: $body")
        }

    @Test
    fun blankDisplayName_dropsKey() =
        runTest {
            val putBody = CompletableDeferred<String>()
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    when {
                        request.isGetRecord() ->
                            okJson(getRecordJson(cid = "bafcid", value = """{"displayName":"old","description":"keep"}"""))
                        request.isPutRecord() -> {
                            putBody.complete(request.body.toBodyString())
                            okJson(putRecordJson(cid = "bafnew"))
                        }
                        else -> error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.updateProfile(
                    displayName = "   ",
                    description = "keep",
                    avatar = ImageChange.Unchanged,
                    banner = ImageChange.Unchanged,
                )

            assertTrue(result.isSuccess, "expected success, got: ${result.exceptionOrNull()}")
            val body = putBody.await()
            assertFalse(body.contains("\"displayName\""), "blank displayName must drop the key: $body")
            assertTrue(body.contains("\"description\":\"keep\""), "description should remain: $body")
        }

    @Test
    fun signedOut_returnsUnauthorized_withoutTouchingNetwork() =
        runTest {
            val (engine, repo) =
                newRepo(signedIn = false) {
                    error("Unauthorized path must not hit the network")
                }

            val result =
                repo.updateProfile(
                    displayName = "x",
                    description = null,
                    avatar = ImageChange.Unchanged,
                    banner = ImageChange.Unchanged,
                )

            assertTrue(result.isFailure)
            assertEquals(ProfileUpdateError.Unauthorized, result.exceptionOrNull())
            assertEquals(0, engine.requestHistory.size)
        }

    @Test
    fun blobUploadFailure_surfacesBlobUploadFailed_noPutRecord() =
        runTest {
            val (engine, repo) =
                newRepo(signedIn = true) { request ->
                    when {
                        request.isGetRecord() ->
                            okJson(getRecordJson(cid = "bafcid", value = """{"displayName":"name"}"""))
                        request.isUploadBlob() ->
                            errorJson(HttpStatusCode.BadRequest, "BlobTooLarge", "blob exceeds limit")
                        request.isPutRecord() ->
                            error("putRecord must not run after a blob upload failure")
                        else -> error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.updateProfile(
                    displayName = null,
                    description = null,
                    avatar = ImageChange.Replaced(bytes = byteArrayOf(1), mimeType = "image/jpeg"),
                    banner = ImageChange.Unchanged,
                )

            assertTrue(result.isFailure)
            val cause = result.exceptionOrNull()
            assertNotNull(cause as? ProfileUpdateError.BlobUploadFailed, "expected BlobUploadFailed, got: $cause")
            assertTrue(engine.requestHistory.none { it.url.encodedPath.endsWith("putRecord") })
        }

    // ---------- harness ----------

    private fun HttpRequestData.isGetRecord(): Boolean = url.encodedPath.endsWith("getRecord")

    private fun HttpRequestData.isPutRecord(): Boolean = url.encodedPath.endsWith("putRecord")

    private fun HttpRequestData.isUploadBlob(): Boolean = url.encodedPath.endsWith("uploadBlob")

    private fun getRecordJson(
        cid: String,
        value: String,
    ): String = """{"uri":"$selfUri","cid":"$cid","value":$value}"""

    private fun putRecordJson(cid: String): String = """{"uri":"$selfUri","cid":"$cid"}"""

    private fun uploadBlobJson(
        link: String,
        mime: String,
        size: Int,
    ): String = """{"blob":{"${'$'}type":"blob","ref":{"${'$'}link":"$link"},"mimeType":"$mime","size":$size}}"""

    private fun MockRequestHandleScope.okJson(json: String): HttpResponseData =
        respond(
            ByteReadChannel(json),
            HttpStatusCode.OK,
            headersOf("Content-Type", ContentType.Application.Json.toString()),
        )

    private fun MockRequestHandleScope.errorJson(
        status: HttpStatusCode,
        error: String,
        message: String,
    ): HttpResponseData =
        respond(
            ByteReadChannel("""{"error":"$error","message":"$message"}"""),
            status,
            headersOf("Content-Type", ContentType.Application.Json.toString()),
        )

    private fun newRepo(
        signedIn: Boolean,
        encoder: ImageEncoder = passthroughEncoder(),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Pair<MockEngine, DefaultProfileRepository> {
        val engine = MockEngine(handler)
        val xrpcClient =
            XrpcClient(
                baseUrl = "https://test.invalid",
                httpClient = HttpClient(engine),
                authProvider = NoAuth,
            )
        val repo =
            DefaultProfileRepository(
                xrpcClientProvider =
                    object : XrpcClientProvider {
                        override suspend fun authenticated(): XrpcClient = xrpcClient
                    },
                sessionStateProvider =
                    object : SessionStateProvider {
                        override val state: StateFlow<SessionState> =
                            MutableStateFlow(
                                if (signedIn) {
                                    SessionState.SignedIn(handle = "alice.test", did = testDid)
                                } else {
                                    SessionState.SignedOut
                                },
                            )

                        override suspend fun refresh() = Unit
                    },
                encoder = encoder,
                dispatcher = UnconfinedTestDispatcher(),
            )
        return engine to repo
    }

    private fun passthroughEncoder(): ImageEncoder =
        object : ImageEncoder {
            override suspend fun encodeForUpload(
                bytes: ByteArray,
                sourceMimeType: String,
                maxBytes: Long,
            ): EncodedImage = EncodedImage(bytes = bytes, mimeType = sourceMimeType)
        }
}

private fun OutgoingContent.toBodyString(): String =
    when (this) {
        is OutgoingContent.ByteArrayContent -> bytes().decodeToString()
        is io.ktor.http.content.TextContent -> text
        else -> ""
    }

private fun OutgoingContent.toBodyBytes(): ByteArray =
    when (this) {
        is OutgoingContent.ByteArrayContent -> bytes()
        else -> ByteArray(0)
    }
