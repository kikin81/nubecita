package net.kikin.nubecita.core.auth

import kotlin.coroutines.cancellation.CancellationException

/**
 * Supplies a token attesting that a request comes from a genuine copy of the
 * app on a genuine device (Firebase App Check in the shipping app). The
 * token-mediating backend uses it to decide whether a sign-in may proceed.
 *
 * `:core:auth` stays free of Firebase: `:app` supplies the implementation.
 */
fun interface AttestationTokenProvider {
    /**
     * The current token, or `null` when none can be obtained. Never throws
     * (other than for cancellation): a request is sent without a token rather
     * than not at all, and the backend decides what that means.
     */
    suspend fun token(): String?

    companion object {
        /** For builds with no attestation service: never has a token. */
        val None: AttestationTokenProvider = AttestationTokenProvider { null }
    }
}

/**
 * Wraps a platform call that fetches a token so it honours
 * [AttestationTokenProvider.token]'s contract: a failure or a blank result
 * becomes `null` instead of an exception.
 */
fun attestationTokenProvider(fetch: suspend () -> String?): AttestationTokenProvider =
    AttestationTokenProvider {
        try {
            fetch()?.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
