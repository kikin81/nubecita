package net.kikin.nubecita.core.videoupload.internal

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import timber.log.Timber

/**
 * Pins the log **severity** of terminal failures, because severity is what
 * decides whether we can see them in production at all.
 *
 * `CrashlyticsTree` forwards `Timber.e` as a recorded non-fatal and `Timber.w`
 * as a breadcrumb only. Video upload shipped broken on every fresh install
 * (nubecita-3ug0) and generated zero Crashlytics issues purely because these
 * paths logged at WARN — the bug was reported by a user, not by telemetry.
 *
 * So the rule this file enforces: a failure the user cannot recover from logs
 * at ERROR; anything retried or expected stays at WARN, where it adds context
 * without crowding the issue stream.
 */
class FailureSeverityTest {
    private val logged = mutableListOf<Pair<Int, String?>>()

    private val recorder =
        object : Timber.Tree() {
            override fun log(
                priority: Int,
                tag: String?,
                message: String,
                t: Throwable?,
            ) {
                logged += priority to message
            }
        }

    @BeforeEach fun plant() = Timber.plant(recorder)

    @AfterEach fun uproot() = Timber.uproot(recorder)

    private fun severityOf(fragment: String): Int? = logged.firstOrNull { it.second?.contains(fragment) == true }?.first

    private fun poller(
        status: HttpStatusCode,
        body: String,
        maxAttempts: Int,
    ): JobStatusPoller {
        val engine =
            MockEngine {
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val factory =
            VideoServiceClientFactory(
                httpClient = HttpClient(engine),
                serviceAuthProvider =
                    object : ServiceAuthProvider {
                        override suspend fun videoServiceToken(lxm: String): String = "jwt"

                        override suspend fun blobUploadToken(): String = "jwt"
                    },
            )
        return JobStatusPoller(factory, pollIntervalMs = 0, maxAttempts = maxAttempts)
    }

    /**
     * A job that never resolves means the user cannot post. That is terminal,
     * so it must reach Crashlytics as a non-fatal rather than a breadcrumb.
     */
    @Test
    fun `a job that never completes is logged at ERROR`() =
        runTest {
            val running = """{"jobStatus":{"jobId":"job-1","did":"did:plc:example","state":"JOB_STATE_ENCODING"}}"""

            poller(HttpStatusCode.OK, running, maxAttempts = 2).awaitBlob("job-1") {}

            assertEquals(
                Log.ERROR,
                severityOf("did not complete"),
                "a terminal processing failure must be a non-fatal, not a breadcrumb",
            )
        }

    /**
     * The per-attempt failures underneath it are retried, so they stay WARN —
     * raising them would fire a non-fatal for every transient socket timeout
     * and crowd out the failures that matter.
     */
    @Test
    fun `a retried poll attempt stays at WARN`() =
        runTest {
            poller(HttpStatusCode.ServiceUnavailable, "", maxAttempts = 2).awaitBlob("job-1") {}

            assertEquals(
                Log.WARN,
                severityOf("getJobStatus failed on attempt"),
                "a retried attempt must not fire a non-fatal",
            )
        }

    /**
     * A server policy refusal — unverified email, exhausted quota — is an
     * answer, not a defect. It stays a breadcrumb, but it must be logged at
     * least at WARN: CrashlyticsTree drops anything below that, so the INFO it
     * used to be reached nothing at all.
     */
    @Test
    fun `a limits refusal is a breadcrumb, not a non-fatal`() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"canUpload":false,"message":"Account email must be verified"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val factory =
                VideoServiceClientFactory(
                    httpClient = HttpClient(engine),
                    serviceAuthProvider =
                        object : ServiceAuthProvider {
                            override suspend fun videoServiceToken(lxm: String): String = "jwt"

                            override suspend fun blobUploadToken(): String = "jwt"
                        },
                )

            UploadLimitsProbe(factory).check()

            val severity = severityOf("getUploadLimits refused")
            assertTrue(severity != null, "the refusal must be logged at all — below WARN reaches nothing")
            assertEquals(Log.WARN, severity, "a policy answer is not our defect")
        }
}
