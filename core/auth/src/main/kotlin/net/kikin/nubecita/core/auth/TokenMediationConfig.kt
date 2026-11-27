package net.kikin.nubecita.core.auth

import java.net.URI
import java.net.URISyntaxException

/**
 * Where the token-mediating backend lives, if this build uses one.
 *
 * With no URL the app is inert with respect to the backend: every OAuth
 * request goes straight to the authorization server and no attestation token
 * is ever requested. That is what the offline bench flavor and any build
 * without the setting get.
 */
internal class TokenMediationConfig private constructor(
    /** The backend's base URL without a trailing slash, or `null` when token mediation is off. */
    val baseUrl: String?,
) {
    val isEnabled: Boolean get() = baseUrl != null

    companion object {
        // The only places plain http is tolerated: a backend emulator on the
        // development machine, reached from a device or an emulator.
        private val LOCAL_EMULATOR_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")

        /**
         * @throws IllegalArgumentException for a URL that is set but unusable. A
         *   wrong value is a build mistake; failing loudly beats silently
         *   falling back to the direct path and shipping sessions that never
         *   get the longer lifetime.
         */
        fun from(url: String): TokenMediationConfig {
            val trimmed = url.trim().trimEnd('/')
            if (trimmed.isEmpty()) return TokenMediationConfig(baseUrl = null)

            val uri =
                try {
                    URI(trimmed)
                } catch (e: URISyntaxException) {
                    throw IllegalArgumentException("OAuth mediator URL is not a URL: $url", e)
                }
            val host = uri.host
            require(!host.isNullOrEmpty()) { "OAuth mediator URL has no host: $url" }
            require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
                "OAuth mediator URL must be a plain base URL: $url"
            }
            when (uri.scheme) {
                "https" -> Unit
                "http" ->
                    require(host in LOCAL_EMULATOR_HOSTS) {
                        "OAuth mediator URL must be https unless it points at a local emulator: $url"
                    }
                else -> throw IllegalArgumentException("OAuth mediator URL must be https: $url")
            }
            return TokenMediationConfig(baseUrl = trimmed)
        }
    }
}
