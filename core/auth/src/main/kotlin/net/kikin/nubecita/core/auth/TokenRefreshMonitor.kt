package net.kikin.nubecita.core.auth

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.kikin.nubecita.core.analytics.TokenRefreshCaller
import net.kikin.nubecita.core.analytics.TokenRefreshOutcome
import java.net.ConnectException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import kotlin.time.TimeSource

/**
 * Ktor plugin on the shared [io.ktor.client.HttpClient] that observes every
 * refresh-token POST and hands it to [TokenRefreshTelemetry].
 *
 * The SDK's `DpopAuthProvider` uses this same client as its refresh client,
 * so this is the one place that sees every refresh regardless of which
 * provider instance or caller triggered it. Recognized by the form body
 * (`grant_type=refresh_token`), so the login code exchange is not counted.
 *
 * Observe-only: the request and the response pass through untouched, and an
 * exception is always rethrown. The response body can be read here because
 * Ktor's default `SaveBody` plugin buffers it, so the SDK still reads it after.
 */
internal fun tokenRefreshMonitor(telemetry: TokenRefreshTelemetry): ClientPlugin<Unit> =
    createClientPlugin("TokenRefreshMonitor") {
        on(Send) { request ->
            if (!request.isRefreshTokenGrant()) return@on proceed(request)

            val caller = currentCoroutineContext()[RefreshCaller].toAnalyticsCaller()
            telemetry.onSending(caller)
            val started = TimeSource.Monotonic.markNow()
            val call =
                try {
                    proceed(request)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    telemetry.onAttempt(
                        caller = caller,
                        outcome = classifyRefreshFailure(e),
                        durationMillis = started.elapsedNow().inWholeMilliseconds,
                        cause = e,
                    )
                    throw e
                }
            val status = call.response.status.value
            val rejection =
                if (status == HTTP_OK) {
                    null
                } else {
                    // Guarded: telemetry must not fail the call the SDK is about to read.
                    val body =
                        try {
                            call.response.bodyAsText()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }
                    classifyRefreshRejection(status, body)
                }
            telemetry.onAttempt(
                caller = caller,
                outcome = rejection?.outcome ?: TokenRefreshOutcome.Ok,
                durationMillis = started.elapsedNow().inWholeMilliseconds,
                errorDescription = rejection?.description,
            )
            call
        }
    }

internal data class RefreshRejection(
    val outcome: TokenRefreshOutcome,
    val description: String?,
)

/**
 * Classifies a non-200 token-endpoint response by its OAuth `error` field
 * (RFC 6749 §5.2). An unparseable body falls back to the status alone.
 */
internal fun classifyRefreshRejection(
    status: Int,
    body: String?,
): RefreshRejection {
    val fields =
        body?.let {
            runCatching { refreshErrorJson.parseToJsonElement(it).jsonObject }.getOrNull()
        }
    val error = fields?.get("error")?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
    val description = fields?.get("error_description")?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
    val outcome =
        when {
            error == "invalid_grant" -> TokenRefreshOutcome.InvalidGrant
            error == "use_dpop_nonce" -> TokenRefreshOutcome.NonceChallenge
            status in 400..499 -> TokenRefreshOutcome.Rejected
            else -> TokenRefreshOutcome.ServerError
        }
    return RefreshRejection(outcome = outcome, description = description)
}

/**
 * Not sent only when every attempt failed before reaching the server, so the
 * token was provably not consumed. Everything else may have been sent and its
 * response lost.
 *
 * "Every attempt" because OkHttp re-sends a request whose connection dropped
 * mid-flight and keeps the earlier failure as suppressed. A DNS failure on that
 * retry says nothing about the first POST, which may already have consumed the
 * token: a Pixel was logged not_sent this way and signed out 16 minutes later.
 */
internal fun classifyRefreshFailure(e: Throwable): TokenRefreshOutcome =
    if (e.isPreSendFailure() && e.earlierAttempts().all { it.isPreSendFailure() }) {
        TokenRefreshOutcome.NotSent
    } else {
        TokenRefreshOutcome.LostResponse
    }

/**
 * The failures OkHttp kept from attempts before this one. kotlinx.coroutines can
 * re-create an exception as it crosses a coroutine boundary, with the original
 * as its cause, so a same-type cause's suppressed list counts too. Not the rest
 * of the cause chain: a genuine DNS failure is caused by an Android
 * `GaiException`, which would wrongly read as a request that went out.
 */
private fun Throwable.earlierAttempts(): List<Throwable> {
    val original = cause?.takeIf { it::class == this::class }
    return suppressed.toList() + (original?.suppressed?.toList() ?: emptyList())
}

private fun Throwable.isPreSendFailure(): Boolean =
    this is UnknownHostException ||
        this is ConnectException ||
        this is UnresolvedAddressException ||
        this is ConnectTimeoutException

private fun RefreshCaller?.toAnalyticsCaller(): TokenRefreshCaller =
    when (this) {
        RefreshCaller.DmPoll -> TokenRefreshCaller.DmPoll
        RefreshCaller.Widget -> TokenRefreshCaller.Widget
        RefreshCaller.PushRegistration -> TokenRefreshCaller.PushRegistration
        null -> TokenRefreshCaller.Untagged
    }

private fun HttpRequestBuilder.isRefreshTokenGrant(): Boolean = (body as? FormDataContent)?.formData?.get("grant_type") == "refresh_token"

private const val HTTP_OK = 200

private val refreshErrorJson = Json { ignoreUnknownKeys = true }
