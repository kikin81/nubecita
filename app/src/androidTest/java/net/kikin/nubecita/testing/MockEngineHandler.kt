package net.kikin.nubecita.testing

import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ktor [io.ktor.client.engine.mock.MockEngine] handler — the lambda the
 * mock engine invokes for every request. Matches the upstream
 * `MockRequestHandler` shape so test code can use [MockRequestHandleScope]'s
 * `respond`, `respondError`, `respondOk`, etc. directly.
 */
typealias MockHandler = suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData

/**
 * Hilt-injected holder for the current [MockHandler]. Tests mutate
 * [handler] before triggering the code under test; the [MockEngineModule]'s
 * mock engine reads from the holder on every request.
 *
 * Singleton-scoped so all `HttpClient` consumers in the test process share
 * one mock engine. The default handler returns 501 NOT_IMPLEMENTED with a
 * diagnostic body so tests that forget to set up routes fail loudly with
 * the offending request path instead of timing out or silently 404-ing.
 *
 * Tests should reset between cases (e.g. in `@After`) by reassigning
 * [handler] or calling [reset]; otherwise prior-test handlers leak across
 * the suite.
 *
 * Example usage:
 * ```
 * @HiltAndroidTest
 * class FeedScreenInstrumentationTest {
 *     @Inject lateinit var mockEngine: MockEngineHandlerHolder
 *
 *     @Before fun setUp() {
 *         hiltRule.inject()
 *         mockEngine.handler = { request ->
 *             when (request.url.encodedPath) {
 *                 "/xrpc/app.bsky.feed.getTimeline" -> respond(
 *                     content = loadFixture("timeline_basic.json"),
 *                     status = HttpStatusCode.OK,
 *                     headers = headersOf(HttpHeaders.ContentType, "application/json"),
 *                 )
 *                 else -> respondError(HttpStatusCode.NotFound)
 *             }
 *         }
 *     }
 * }
 * ```
 */
@Singleton
class MockEngineHandlerHolder
    @Inject
    constructor() {
        @Volatile
        var handler: MockHandler = DEFAULT_HANDLER

        fun reset() {
            handler = DEFAULT_HANDLER
        }

        companion object {
            private val DEFAULT_HANDLER: MockHandler = { request ->
                respondError(
                    status = HttpStatusCode.NotImplemented,
                    content =
                        "MockEngineHandlerHolder has no handler set. Call " +
                            "holder.handler = { ... } in your test setup. " +
                            "Request was ${request.method.value} ${request.url.encodedPath}",
                )
            }
        }
    }
