package net.kikin.nubecita.core.auth

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.kikin.nubecita.core.analytics.ClientAuthRegime
import net.kikin.nubecita.core.analytics.IssuerKind
import net.kikin.nubecita.core.analytics.RefreshErrorDetail
import net.kikin.nubecita.core.analytics.TokenRefreshCaller
import net.kikin.nubecita.core.analytics.TokenRefreshOutcome
import net.kikin.nubecita.core.analytics.TokenRequestPath
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
            // Absent means the plain direct path: only the mediated transport tags its requests.
            val path = currentCoroutineContext()[TokenPathElement]?.path ?: TokenRequestPath.Direct
            val issuer = request.issuerKind()
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
                        path = path,
                        issuer = issuer,
                    )
                    throw e
                }
            val status = call.response.status.value
            // Through the backend, only a response it marks as relayed is the
            // authorization server's. Anything else is the backend speaking
            // for itself, whatever its status or body say.
            val fromBackendItself =
                path == TokenRequestPath.Mediated &&
                    call.response.headers[MediatedTokenEndpointTransport.HEADER_ORIGIN] != MediatedTokenEndpointTransport.ORIGIN_UPSTREAM
            val rejection =
                if (status == HTTP_OK && !fromBackendItself) {
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
                    if (fromBackendItself) classifyMediatorAnswer(body) else classifyRefreshRejection(status, body)
                }
            telemetry.onAttempt(
                caller = caller,
                outcome = rejection?.outcome ?: TokenRefreshOutcome.Ok,
                durationMillis = started.elapsedNow().inWholeMilliseconds,
                errorCode = rejection?.error,
                errorDescription = rejection?.description,
                path = path,
                clientAuth = ClientAuthRegime.fromAuthMethod(call.response.headers[MediatedTokenEndpointTransport.HEADER_AUTH_METHOD]),
                replayed = call.response.headers[MediatedTokenEndpointTransport.HEADER_REPLAYED] == "1",
                issuer = issuer,
            )
            call
        }
    }

internal data class RefreshRejection(
    val outcome: TokenRefreshOutcome,
    val description: String?,
    /** The OAuth `error` code, when it is shaped like one. */
    val error: String? = null,
)

/**
 * Sorts a rejection into [RefreshErrorDetail] by the reference authorization
 * server's own wording (bluesky-social/atproto `oauth-provider`). Matching is
 * on that wording because nothing else separates these cases: the server
 * sends "DPoP proof replayed" and "Failed to verify DPoP proof" under one
 * error code. A server that words things differently lands in `other`.
 */
internal fun refreshErrorDetail(
    error: String?,
    description: String?,
): RefreshErrorDetail {
    val text = description?.trim().orEmpty()
    return when {
        text.equals("Refresh token replayed", ignoreCase = true) -> RefreshErrorDetail.RefreshTokenReplayed
        text.equals("Session expired", ignoreCase = true) -> RefreshErrorDetail.SessionExpired
        text.equals("Refresh token expired", ignoreCase = true) -> RefreshErrorDetail.RefreshTokenExpired
        text.equals("DPoP proof replayed", ignoreCase = true) -> RefreshErrorDetail.DpopProofReplayed
        text.startsWith("Failed to verify DPoP proof", ignoreCase = true) -> RefreshErrorDetail.DpopProofUnverified
        text.equals("Invalid DPoP key binding", ignoreCase = true) -> RefreshErrorDetail.DpopKeyBinding
        error == "invalid_dpop_proof" -> RefreshErrorDetail.DpopProofOther
        text.isEmpty() -> RefreshErrorDetail.None
        else -> RefreshErrorDetail.Other
    }
}

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
    return RefreshRejection(outcome = outcome, description = description, error = error?.takeIf { OAUTH_ERROR_CODE.matches(it) })
}

/**
 * An answer the token-mediating backend produced itself. Its `error` is a
 * fixed `mediator_<reason>` code, which is kept as the description: it says
 * why the backend did not relay, and carries nothing about the user.
 */
internal fun classifyMediatorAnswer(body: String?): RefreshRejection {
    val reason =
        body
            ?.let {
                runCatching {
                    refreshErrorJson
                        .parseToJsonElement(it)
                        .jsonObject["error"]
                        ?.jsonPrimitive
                        ?.content
                }.getOrNull()
            }?.takeIf { MEDIATOR_REASON.matches(it) }
    return RefreshRejection(outcome = TokenRefreshOutcome.MediatorError, description = reason)
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

/**
 * Whose authorization server this refresh is for: named in a header on a
 * mediated request, and the request's own host on a direct one.
 */
private fun HttpRequestBuilder.issuerKind(): IssuerKind {
    val named = headers[MediatedTokenEndpointTransport.HEADER_ISSUER]
    val host = if (named != null) runCatching { Url(named).host }.getOrNull() else url.host
    return IssuerKind.ofHost(host)
}

private fun HttpRequestBuilder.isRefreshTokenGrant(): Boolean = (body as? FormDataContent)?.formData?.get("grant_type") == "refresh_token"

private const val HTTP_OK = 200

private val MEDIATOR_REASON = Regex("mediator_[a-z_]{1,40}")

// An OAuth error code is a short lowercase token; anything else is not logged.
private val OAUTH_ERROR_CODE = Regex("[a-z_]{1,40}")

private val refreshErrorJson = Json { ignoreUnknownKeys = true }
