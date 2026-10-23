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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.image.EncodedImage
import net.kikin.nubecita.core.image.ImageEncoder
import net.kikin.nubecita.core.moderation.ContentLabel
import net.kikin.nubecita.core.moderation.LabelVisibility
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.core.postinteractions.FollowRepository
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

    // ---------- links ----------

    private fun linkUri(rkey: String) = "at://$testDid/app.bsky.actor.link/$rkey"

    private fun saved(
        rkey: String,
        url: String,
        title: String? = null,
        cid: String = "cid-$rkey",
    ) = EditableProfileLink(
        localId = linkUri(rkey),
        url = url,
        title = title,
        uri = linkUri(rkey),
        cid = cid,
        record =
            Json
                .parseToJsonElement(
                    """{"${'$'}type":"app.bsky.actor.link","url":"$url","createdAt":"2026-10-01T12:00:00Z",""" +
                        """"icon":{"${'$'}type":"blob","ref":{"${'$'}link":"bafold$rkey"},"mimeType":"image/png","size":10}}""",
                ).jsonObject,
    )

    private val HttpRequestData.isLinkWrite: Boolean
        get() = body.toBodyString().contains("\"collection\":\"app.bsky.actor.link\"")

    /** Routes the full links save sequence; [linkPutCid] answers a link putRecord. */
    private suspend fun MockRequestHandleScope.linksHandler(
        request: HttpRequestData,
        profileBody: CompletableDeferred<String>,
        linkBodies: MutableList<String>,
        profileValue: String = """{"${'$'}type":"app.bsky.actor.profile"}""",
        profilePut: (MockRequestHandleScope.() -> HttpResponseData)? = null,
        deleteResponse: (MockRequestHandleScope.() -> HttpResponseData)? = null,
        uploadFails: Boolean = false,
    ): HttpResponseData {
        val path = request.url.encodedPath
        return when {
            request.isGetRecord() -> okJson(getRecordJson(cid = "bafcid", value = profileValue))
            path.endsWith("uploadBlob") ->
                if (uploadFails) {
                    errorJson(HttpStatusCode.InternalServerError, "InternalError", "boom")
                } else {
                    okJson(uploadBlobJson(link = "bafnewicon", mime = "image/png", size = 3))
                }
            path.endsWith("createRecord") -> {
                linkBodies += request.body.toBodyString()
                okJson("""{"uri":"${linkUri("new1")}","cid":"cid-new1"}""")
            }
            request.isPutRecord() && request.isLinkWrite -> {
                val body = request.body.toBodyString()
                linkBodies += body
                val rkey = checkNotNull(Regex("\"rkey\":\"([^\"]+)\"").find(body)).groupValues[1]
                okJson("""{"uri":"${linkUri(rkey)}","cid":"cid-$rkey-v2"}""")
            }
            request.isPutRecord() -> {
                profileBody.complete(request.body.toBodyString())
                profilePut?.invoke(this) ?: okJson(putRecordJson(cid = "bafnew"))
            }
            path.endsWith("deleteRecord") -> deleteResponse?.invoke(this) ?: okJson("{}")
            else -> error("Unexpected request: ${request.url}")
        }
    }

    @Test
    fun unchangedLinks_touchNoLinkRecord_andPreserveTheLinksKey() =
        runTest {
            val profileBody = CompletableDeferred<String>()
            val linkBodies = mutableListOf<String>()
            val (engine, repo) =
                newRepo(signedIn = true) { request ->
                    linksHandler(
                        request,
                        profileBody,
                        linkBodies,
                        profileValue = """{"${'$'}type":"app.bsky.actor.profile","links":[{"uri":"${linkUri("a")}","cid":"cid-a"}]}""",
                    )
                }

            val result = repo.updateProfile("n", "d", ImageChange.Unchanged, ImageChange.Unchanged)

            assertTrue(result.isSuccess, "got ${result.exceptionOrNull()}")
            assertTrue(profileBody.await().contains(linkUri("a")), "links key must survive")
            assertTrue(engine.requestHistory.none { it.url.encodedPath.endsWith("createRecord") || it.url.encodedPath.endsWith("deleteRecord") })
            assertTrue(linkBodies.isEmpty())
        }

    @Test
    fun mixedLinkChanges_writeRecordsBeforeProfile_andDeleteAfter() =
        runTest {
            val profileBody = CompletableDeferred<String>()
            val linkBodies = mutableListOf<String>()
            val (engine, repo) = newRepo(signedIn = true) { request -> linksHandler(request, profileBody, linkBodies) }
            val a = saved("a", "https://a.example", "A")
            val b = saved("b", "https://b.example", "B")
            val d = saved("d", "https://d.example")
            val c = EditableProfileLink(localId = "new-1", url = "https://c.example", title = "C")

            val result =
                repo.updateProfile(
                    "n",
                    "d",
                    ImageChange.Unchanged,
                    ImageChange.Unchanged,
                    links = LinksChange.Replaced(links = listOf(a, b.copy(title = "B2"), c), original = listOf(a, b, d)),
                )

            assertTrue(result.isSuccess, "got ${result.exceptionOrNull()}")
            val paths = engine.requestHistory.map { it.url.encodedPath.substringAfterLast('.') }
            val profilePut = paths.lastIndexOf("putRecord")
            assertTrue(paths.indexOf("createRecord") in 0 until profilePut, "create must precede the profile write: $paths")
            assertTrue(paths.indexOf("deleteRecord") > profilePut, "delete must follow the profile write: $paths")
            assertEquals(1, paths.count { it == "createRecord" })
            assertEquals(1, paths.count { it == "deleteRecord" })

            val body = profileBody.await()
            val expected =
                """"links":[{"uri":"${linkUri("a")}","cid":"cid-a"},{"uri":"${linkUri("b")}","cid":"cid-b-v2"},""" +
                    """{"uri":"${linkUri("new1")}","cid":"cid-new1"}]"""
            assertTrue(body.contains(expected), "links refs wrong: $body")
            // Retitled B keeps its stored icon and createdAt (URL unchanged).
            val bPut = linkBodies.single { it.contains("\"rkey\":\"b\"") }
            assertTrue(bPut.contains("\"title\":\"B2\"") && bPut.contains("bafoldb") && bPut.contains("2026-10-01T12:00:00Z"), bPut)
            assertTrue(
                engine.requestHistory
                    .last { it.url.encodedPath.endsWith("deleteRecord") }
                    .body
                    .toBodyString()
                    .contains("\"rkey\":\"d\""),
            )
        }

    @Test
    fun urlChangedWithoutNewIcon_dropsTheOldIcon() =
        runTest {
            val linkBodies = mutableListOf<String>()
            val (_, repo) = newRepo(signedIn = true) { request -> linksHandler(request, CompletableDeferred(), linkBodies) }
            val b = saved("b", "https://b.example")

            repo.updateProfile(
                "n",
                "d",
                ImageChange.Unchanged,
                ImageChange.Unchanged,
                links = LinksChange.Replaced(links = listOf(b.copy(url = "https://elsewhere.example")), original = listOf(b)),
            )

            val put = linkBodies.single()
            assertTrue(put.contains("https://elsewhere.example") && !put.contains("bafoldb"), put)
        }

    @Test
    fun pendingIcon_isUploadedAsPng_andSetOnTheRecord() =
        runTest {
            val linkBodies = mutableListOf<String>()
            val (engine, repo) = newRepo(signedIn = true) { request -> linksHandler(request, CompletableDeferred(), linkBodies) }
            val c = EditableProfileLink(localId = "new-1", url = "https://c.example", title = null, pendingIcon = PendingIcon(byteArrayOf(1, 2, 3)))

            val result =
                repo.updateProfile("n", "d", ImageChange.Unchanged, ImageChange.Unchanged, LinksChange.Replaced(listOf(c), emptyList()))

            assertTrue(result.isSuccess)
            val upload = engine.requestHistory.single { it.isUploadBlob() }
            assertEquals("image/png", upload.body.contentType?.toString())
            assertTrue(linkBodies.single().contains("bafnewicon"), linkBodies.single())
        }

    @Test
    fun iconUploadFailure_savesTheLinkWithoutAnIcon() =
        runTest {
            val linkBodies = mutableListOf<String>()
            val (_, repo) =
                newRepo(signedIn = true) { request -> linksHandler(request, CompletableDeferred(), linkBodies, uploadFails = true) }
            val c = EditableProfileLink(localId = "new-1", url = "https://c.example", title = null, pendingIcon = PendingIcon(byteArrayOf(1)))

            val result =
                repo.updateProfile("n", "d", ImageChange.Unchanged, ImageChange.Unchanged, LinksChange.Replaced(listOf(c), emptyList()))

            assertTrue(result.isSuccess, "got ${result.exceptionOrNull()}")
            assertFalse(linkBodies.single().contains("\"icon\""), linkBodies.single())
        }

    @Test
    fun removingEveryLink_dropsTheKey_andDeletesTheRecords() =
        runTest {
            val profileBody = CompletableDeferred<String>()
            val (engine, repo) =
                newRepo(signedIn = true) { request ->
                    linksHandler(
                        request,
                        profileBody,
                        mutableListOf(),
                        profileValue = """{"${'$'}type":"app.bsky.actor.profile","links":[{"uri":"${linkUri("a")}","cid":"cid-a"}]}""",
                    )
                }
            val a = saved("a", "https://a.example")

            repo.updateProfile("n", "d", ImageChange.Unchanged, ImageChange.Unchanged, LinksChange.Replaced(emptyList(), listOf(a)))

            assertFalse(profileBody.await().contains("\"links\""))
            assertEquals(1, engine.requestHistory.count { it.url.encodedPath.endsWith("deleteRecord") })
        }

    @Test
    fun profileSwapConflict_deletesNothing() =
        runTest {
            val (engine, repo) =
                newRepo(signedIn = true) { request ->
                    linksHandler(
                        request,
                        CompletableDeferred(),
                        mutableListOf(),
                        profilePut = { errorJson(HttpStatusCode.BadRequest, "InvalidSwap", "stale") },
                    )
                }
            val a = saved("a", "https://a.example")

            val result =
                repo.updateProfile("n", "d", ImageChange.Unchanged, ImageChange.Unchanged, LinksChange.Replaced(emptyList(), listOf(a)))

            assertEquals(ProfileUpdateError.SwapConflict, result.exceptionOrNull())
            assertTrue(engine.requestHistory.none { it.url.encodedPath.endsWith("deleteRecord") })
        }

    @Test
    fun deleteFailure_doesNotFailTheSave() =
        runTest {
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    linksHandler(
                        request,
                        CompletableDeferred(),
                        mutableListOf(),
                        deleteResponse = { errorJson(HttpStatusCode.InternalServerError, "InternalError", "boom") },
                    )
                }
            val a = saved("a", "https://a.example")

            val result =
                repo.updateProfile("n", "d", ImageChange.Unchanged, ImageChange.Unchanged, LinksChange.Replaced(emptyList(), listOf(a)))

            assertTrue(result.isSuccess, "got ${result.exceptionOrNull()}")
        }

    @Test
    fun fetchOwnLinks_readsRecordsInRefOrder_skipsDangling_andJoinsIcons() =
        runTest {
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    val path = request.url.encodedPath
                    val collection = request.url.parameters["collection"]
                    val rkey = request.url.parameters["rkey"]
                    when {
                        request.isGetRecord() && collection == "app.bsky.actor.profile" ->
                            okJson(
                                getRecordJson(
                                    cid = "bafcid",
                                    value =
                                        """{"${'$'}type":"app.bsky.actor.profile","links":[""" +
                                            """{"uri":"${linkUri("b")}","cid":"x"},{"uri":"${linkUri("gone")}","cid":"x"},{"uri":"${linkUri("a")}","cid":"x"}]}""",
                                ),
                            )
                        request.isGetRecord() && rkey == "gone" -> errorJson(HttpStatusCode.BadRequest, "RecordNotFound", "gone")
                        request.isGetRecord() ->
                            okJson(
                                """{"uri":"${linkUri(checkNotNull(rkey))}","cid":"cid-$rkey","value":""" +
                                    """{"${'$'}type":"app.bsky.actor.link","url":"https://$rkey.example","title":"T$rkey","createdAt":"2026-10-01T12:00:00Z"}}""",
                            )
                        path.endsWith("getProfile") ->
                            okJson(
                                """{"did":"$testDid","handle":"alice.test","links":[""" +
                                    """{"uri":"${linkUri("a")}","cid":"cid-a","url":"https://a.example","icon":"https://cdn.example/a.png"}]}""",
                            )
                        else -> error("Unexpected request: ${request.url}")
                    }
                }

            val links = repo.fetchOwnLinks().getOrThrow()

            assertEquals(listOf(linkUri("b"), linkUri("a")), links.map { it.uri })
            assertEquals(listOf("https://b.example", "https://a.example"), links.map { it.url })
            assertEquals(listOf("cid-b", "cid-a"), links.map { it.cid })
            assertEquals(listOf("Tb", "Ta"), links.map { it.title })
            assertEquals(listOf(null, "https://cdn.example/a.png"), links.map { it.iconPreviewUrl })
        }

    @Test
    fun fetchOwnLinks_skipsMalformedRefsAndRecords_insteadOfFailing() =
        runTest {
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    val collection = request.url.parameters["collection"]
                    val rkey = request.url.parameters["rkey"]
                    when {
                        request.isGetRecord() && collection == "app.bsky.actor.profile" ->
                            okJson(
                                getRecordJson(
                                    cid = "bafcid",
                                    value =
                                        """{"${'$'}type":"app.bsky.actor.profile","links":[""" +
                                            """{"uri":{"nested":true},"cid":"x"},{"uri":"${linkUri("bad")}","cid":"x"},{"uri":"${linkUri("ok")}","cid":"x"}]}""",
                                ),
                            )
                        request.isGetRecord() && rkey == "bad" ->
                            okJson("""{"uri":"${linkUri("bad")}","cid":"cid-bad","value":{"url":{"not":"a string"},"createdAt":"2026-10-01T12:00:00Z"}}""")
                        request.isGetRecord() ->
                            okJson("""{"uri":"${linkUri("ok")}","cid":"cid-ok","value":{"url":"https://ok.example","title":["x"],"createdAt":"2026-10-01T12:00:00Z"}}""")
                        request.url.encodedPath.endsWith("getProfile") -> okJson("""{"did":"$testDid","handle":"alice.test"}""")
                        else -> error("Unexpected request: ${request.url}")
                    }
                }

            val links = repo.fetchOwnLinks().getOrThrow()

            assertEquals(listOf(linkUri("ok")), links.map { it.uri })
            assertEquals(listOf<String?>(null), links.map { it.title })
        }

    @Test
    fun fetchOwnLinks_noProfileRecord_isEmpty() =
        runTest {
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    if (request.isGetRecord()) errorJson(HttpStatusCode.BadRequest, "RecordNotFound", "none") else error("Unexpected ${request.url}")
                }

            assertEquals(emptyList<EditableProfileLink>(), repo.fetchOwnLinks().getOrThrow())
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
                moderationPreferences = inertModerationPrefs(),
                encoder = encoder,
                followRepository = inertFollowRepository(),
                dispatcher = UnconfinedTestDispatcher(),
            )
        return engine to repo
    }

    // updateProfile never touches follow/unfollow; an inert delegate
    // satisfies the constructor without issuing any network call.
    private fun inertFollowRepository(): FollowRepository =
        object : FollowRepository {
            override suspend fun follow(subjectDid: String): Result<String> = error("follow not exercised in updateProfile tests")

            override suspend fun unfollow(followUri: String): Result<Unit> = error("unfollow not exercised in updateProfile tests")
        }

    // updateProfile never reads moderation prefs; an inert DEFAULT repo
    // satisfies the constructor without touching the gate. fetchTab moderation
    // is covered separately in AuthorFeedMapperTest.
    private fun inertModerationPrefs(): ModerationPreferencesRepository =
        object : ModerationPreferencesRepository {
            override val prefs = MutableStateFlow(ModerationPrefs.DEFAULT)

            override suspend fun refresh() = Unit

            override fun resetToDefault() = Unit

            override suspend fun setAdultContentEnabled(enabled: Boolean) = Unit

            override suspend fun setVisibility(
                label: ContentLabel,
                visibility: LabelVisibility,
            ) = Unit
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
