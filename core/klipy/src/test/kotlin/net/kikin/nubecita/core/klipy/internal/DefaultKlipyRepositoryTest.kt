package net.kikin.nubecita.core.klipy.internal

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import net.kikin.nubecita.core.klipy.KlipyReportReason
import net.kikin.nubecita.data.models.KlipyMediaType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultKlipyRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun TestScope.buildRepo(
        appScope: CoroutineScope = backgroundScope,
        handler: MockRequestHandler,
    ): Pair<DefaultKlipyRepository, MutableList<HttpRequestData>> {
        val recorded = mutableListOf<HttpRequestData>()
        val client =
            HttpClient(
                MockEngine { request ->
                    recorded += request
                    handler(request)
                },
            ) {
                expectSuccess = true
                install(DefaultRequest) { url("https://api.klipy.com/api/v1/TESTKEY/") }
            }
        val store = mockk<KlipyCustomerIdStore> { coEvery { get() } returns "cid-1" }
        val repo =
            DefaultKlipyRepository(
                httpClient = client,
                json = json,
                customerIdStore = store,
                ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                appScope = appScope,
            )
        return repo to recorded
    }

    @Test
    fun `search maps items and hasNext`() =
        runTest {
            val (repo, _) = buildRepo { respond(SEARCH_PAGE, HttpStatusCode.OK, JSON_HEADERS) }

            val page = repo.search(KlipyMediaType.GIF, query = "cat", page = 1).getOrThrow()

            assertEquals(1, page.items.size)
            assertEquals("cat", page.items.first().slug)
            assertEquals("https://static.klipy.com/ii/hd/cat.gif", page.items.first().embedUrl)
            assertTrue(page.hasNext)
        }

    @Test
    fun `blank query falls back to the trending endpoint`() =
        runTest {
            val (repo, recorded) = buildRepo { respond(EMPTY_PAGE, HttpStatusCode.OK, JSON_HEADERS) }

            repo.search(KlipyMediaType.GIF, query = "   ", page = 1)

            assertTrue(
                recorded
                    .single()
                    .url.encodedPath
                    .endsWith("/gifs/trending"),
            )
        }

    @Test
    fun `search sends the query, page and customer id params`() =
        runTest {
            val (repo, recorded) = buildRepo { respond(EMPTY_PAGE, HttpStatusCode.OK, JSON_HEADERS) }

            repo.search(KlipyMediaType.STICKER, query = "dog", page = 3)

            val url = recorded.single().url
            assertTrue(url.encodedPath.endsWith("/stickers/search"))
            assertEquals("dog", url.parameters["q"])
            assertEquals("3", url.parameters["page"])
            assertEquals("cid-1", url.parameters["customer_id"])
        }

    @Test
    fun `categories lead with Recents and Trending`() =
        runTest {
            val (repo, _) =
                buildRepo {
                    respond("""{"result":true,"data":["Love","Happy"]}""", HttpStatusCode.OK, JSON_HEADERS)
                }

            val categories = repo.categories(KlipyMediaType.GIF).getOrThrow()

            assertEquals(listOf("Recents", "Trending", "Love", "Happy"), categories)
        }

    @Test
    fun `a malformed response is a failed result, not a thrown exception`() =
        runTest {
            val (repo, _) = buildRepo { respond("<<not json>>", HttpStatusCode.OK, JSON_HEADERS) }

            assertTrue(repo.trending(KlipyMediaType.GIF, page = 1).isFailure)
        }

    @Test
    fun `report posts the slug with the reason wire value`() =
        runTest {
            val (repo, recorded) = buildRepo { respond("{}", HttpStatusCode.OK, JSON_HEADERS) }

            val result = repo.report(KlipyMediaType.GIF, slug = "cat", reason = KlipyReportReason.SPAM)

            assertTrue(result.isSuccess)
            val request = recorded.single()
            assertTrue(request.url.encodedPath.endsWith("/gifs/report/cat"))
            assertTrue((request.body as TextContent).text.contains("spam"))
        }

    @Test
    fun `report surfaces a non-2xx status as a failure`() =
        runTest {
            // report decodes no body, so it relies on the client's expectSuccess to
            // turn a 5xx into a thrown response the repository maps to Result.failure.
            val (repo, _) = buildRepo { respondError(HttpStatusCode.InternalServerError) }

            val result = repo.report(KlipyMediaType.GIF, slug = "cat", reason = KlipyReportReason.SPAM)

            assertTrue(result.isFailure)
        }

    @Test
    fun `trackShare fires a share POST on the application scope`() =
        runTest {
            // Own scope so the fire-and-forget launch can be awaited deterministically
            // (the MockEngine POST resolves off the virtual clock, so joining the
            // launched child is reliable where advanceUntilIdle is not).
            val trackingScope = CoroutineScope(Job() + UnconfinedTestDispatcher(testScheduler))
            val (repo, recorded) =
                buildRepo(appScope = trackingScope) {
                    respond("{}", HttpStatusCode.OK, JSON_HEADERS)
                }

            repo.trackShare(KlipyMediaType.GIF, slug = "cat")
            trackingScope.coroutineContext.job.children
                .toList()
                .joinAll()

            assertTrue(recorded.any { it.url.encodedPath.endsWith("/gifs/share/cat") })
        }

    private companion object {
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")

        val SEARCH_PAGE =
            """
            {
              "result": true,
              "data": {
                "has_next": true,
                "data": [
                  {
                    "type": "gif", "slug": "cat",
                    "file": { "hd": { "gif": { "url": "https://static.klipy.com/ii/hd/cat.gif", "width": 480, "height": 360 } } }
                  }
                ]
              }
            }
            """.trimIndent()

        val EMPTY_PAGE = """{"result":true,"data":{"has_next":false,"data":[]}}"""
    }
}
