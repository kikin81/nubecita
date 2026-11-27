package net.kikin.nubecita.core.auth

import io.github.kikin81.atproto.oauth.DirectTokenEndpointTransport
import io.github.kikin81.atproto.oauth.TokenEndpointRequest
import io.github.kikin81.atproto.oauth.TokenEndpointRequestKind
import io.github.kikin81.atproto.oauth.TokenEndpointResponse
import io.github.kikin81.atproto.oauth.TokenEndpointResponseOrigin
import io.github.kikin81.atproto.oauth.TokenEndpointTransport
import io.github.kikin81.atproto.oauth.TokenRequestNotSentException
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.kikin.nubecita.core.analytics.TokenRequestPath
import java.net.ConnectException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Sends the OAuth requests that authenticate the client — pushed
 * authorization request, code exchange, refresh, revocation — through
 * Nubecita's token-mediating backend, which adds the client assertion that
 * makes the client confidential. XRPC calls never come through here.
 *
 * Two rules keep the backend from ever costing a user their session:
 *
 * - **Who answered is never guessed.** Only a response the backend marks as
 *   relayed is the authorization server's. Anything else — including a body
 *   that says `invalid_grant` — is the backend's own and is retryable.
 * - **The direct path is used only when the request provably was not
 *   forwarded** (the backend was unreachable, or said "bypass"), and never for
 *   a refresh token whose earlier attempt has an unknown outcome: that token
 *   may be spent, and replaying it deletes the session.
 */
internal class MediatedTokenEndpointTransport(
    private val baseUrl: String,
    /** Must never silently re-send a request, like the direct token client. */
    private val mediatorClient: HttpClient,
    private val direct: TokenEndpointTransport,
    private val attestation: AttestationTokenProvider,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : TokenEndpointTransport {
    // A lost refresh response must not be followed by a retry over the direct
    // path, so the SDK is asked to remember refreshes whose outcome is unknown.
    override val tracksRefreshOutcome: Boolean = true

    override suspend fun send(request: TokenEndpointRequest): TokenEndpointResponse {
        val attestationToken = attestation.token()
        val response =
            try {
                withContext(TokenPathElement(TokenRequestPath.Mediated)) { post(request, attestationToken) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Anything but a provable pre-send failure may have reached the
                // backend, and through it the authorization server.
                if (!e.neverLeft()) throw e
                return sendDirectOrFail(request, "backend unreachable", e)
            }

        if (response.headers[HEADER_ORIGIN] == ORIGIN_UPSTREAM) {
            return TokenEndpointResponse(
                status = response.status.value,
                body = response.bodyAsText(),
                dpopNonce = response.headers[HEADER_DPOP_NONCE],
                serverDate = response.headers[HEADER_UPSTREAM_DATE],
                origin = TokenEndpointResponseOrigin.AuthorizationServer,
                clientAuthKid = response.headers[HEADER_KID],
                clientAuthMethod = response.headers[HEADER_AUTH_METHOD],
                replayed = response.headers[HEADER_REPLAYED] == "1",
            )
        }

        val body = response.bodyAsText()
        val error = parse(body)
        val notForwarded = error?.get("forwarded")?.jsonPrimitive?.booleanOrNull == false
        val reason = error?.get("error")?.jsonPrimitive?.content
        return when {
            // Unknown or possible forwarding: hand the backend's answer to the
            // SDK, which treats it as retryable and keeps the token in doubt.
            !notForwarded -> mediatorResponse(response, body)
            reason == REASON_BYPASS -> sendDirectOrFail(request, "backend bypass", cause = null)
            reason == REASON_ATTESTATION_REQUIRED && request.kind.isSignIn() -> throw DeviceVerificationRequiredException()
            else -> throw TokenRequestNotSentException("Backend refused without forwarding (${reason ?: "no reason"})")
        }
    }

    private suspend fun post(
        request: TokenEndpointRequest,
        attestationToken: String?,
    ): HttpResponse =
        mediatorClient.submitForm(
            url = "$baseUrl/${request.kind.path()}",
            formParameters = Parameters.build { request.formParameters.forEach { (name, value) -> append(name, value) } },
        ) {
            // Longer than the backend's own limit on the authorization server
            // (25 s inside a 30 s function), so the backend always answers
            // first and says whether the request was forwarded.
            timeout {
                requestTimeoutMillis = MEDIATED_TIMEOUT_MILLIS
                socketTimeoutMillis = MEDIATED_TIMEOUT_MILLIS
            }
            // Made for the authorization server's own endpoint; the backend
            // forwards it untouched.
            header(HEADER_DPOP, request.dpopProof)
            header(HEADER_ISSUER, request.issuer)
            request.clientAuthKid?.let { header(HEADER_KID, it) }
            attestationToken?.let { header(HEADER_APP_CHECK, it) }
        }

    private suspend fun sendDirectOrFail(
        request: TokenEndpointRequest,
        why: String,
        cause: Throwable?,
    ): TokenEndpointResponse {
        if (request.outcomeInDoubt) {
            // An earlier attempt with this refresh token may have reached the
            // authorization server. Only the backend can answer it safely.
            throw TokenRequestNotSentException("Not sent ($why); refresh token in doubt, so not retried direct", cause)
        }
        return withContext(TokenPathElement(TokenRequestPath.DirectFallback)) { direct.send(request) }
    }

    private fun mediatorResponse(
        response: HttpResponse,
        body: String,
    ) = TokenEndpointResponse(
        status = response.status.value,
        body = body,
        origin = TokenEndpointResponseOrigin.Mediator,
    )

    private fun parse(body: String): JsonObject? = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()

    private fun TokenEndpointRequestKind.path(): String =
        when (this) {
            TokenEndpointRequestKind.PushedAuthorization -> "par"
            TokenEndpointRequestKind.CodeExchange, TokenEndpointRequestKind.Refresh -> "token"
            TokenEndpointRequestKind.Revoke -> "revoke"
        }

    private fun TokenEndpointRequestKind.isSignIn(): Boolean = this == TokenEndpointRequestKind.PushedAuthorization || this == TokenEndpointRequestKind.CodeExchange

    /**
     * True only when every attempt failed before reaching the backend. OkHttp
     * keeps a re-sent request's earlier failure as suppressed, and
     * kotlinx.coroutines can re-create an exception with the original as its
     * cause, so both lists are checked — the same test the refresh telemetry
     * applies (see `classifyRefreshFailure`).
     */
    private fun Throwable.neverLeft(): Boolean {
        val original = cause?.takeIf { it::class == this::class }
        val earlier = suppressed.toList() + (original?.suppressed?.toList() ?: emptyList())
        return isPreSend() && earlier.all { it.isPreSend() }
    }

    private fun Throwable.isPreSend(): Boolean =
        this is UnknownHostException ||
            this is ConnectException ||
            this is UnresolvedAddressException ||
            this is ConnectTimeoutException

    internal companion object {
        const val HEADER_ORIGIN = "Nubecita-Mediator-Origin"
        const val HEADER_ISSUER = "Nubecita-Issuer"
        const val HEADER_KID = "Nubecita-Client-Auth-Kid"
        const val HEADER_AUTH_METHOD = "Nubecita-Client-Auth-Method"
        const val HEADER_UPSTREAM_DATE = "Nubecita-Upstream-Date"
        const val HEADER_REPLAYED = "Nubecita-Replayed"
        const val HEADER_APP_CHECK = "X-Firebase-AppCheck"
        const val HEADER_DPOP = "DPoP"
        const val HEADER_DPOP_NONCE = "DPoP-Nonce"
        const val ORIGIN_UPSTREAM = "upstream"
        private const val REASON_BYPASS = "mediator_bypass"
        private const val REASON_ATTESTATION_REQUIRED = "mediator_attestation_required"
        private const val MEDIATED_TIMEOUT_MILLIS = 40_000L
    }
}

/**
 * The transport `AtOAuth` should use: through the backend when one is
 * configured, or `null` — the SDK's own direct path — when it is not.
 */
internal fun tokenTransportFor(
    config: TokenMediationConfig,
    httpClient: HttpClient,
    tokenHttpClient: HttpClient,
    attestation: AttestationTokenProvider,
): TokenEndpointTransport? {
    val baseUrl = config.baseUrl ?: return null
    return MediatedTokenEndpointTransport(
        baseUrl = baseUrl,
        // Mediated requests carry the same single-use tokens as direct ones,
        // so they go through the client whose engine never re-sends.
        mediatorClient = tokenHttpClient,
        direct = DirectTokenEndpointTransport(httpClient = httpClient, singleUseHttpClient = tokenHttpClient),
        attestation = attestation,
    )
}

/**
 * Marks the coroutine an OAuth client-authenticated request runs in with the
 * route it is taking, so telemetry that observes the HTTP call can record it.
 * Absent means the plain direct path.
 */
internal class TokenPathElement(
    val path: TokenRequestPath,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<TokenPathElement>
}
