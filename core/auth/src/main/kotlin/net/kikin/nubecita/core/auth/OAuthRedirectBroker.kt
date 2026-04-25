package net.kikin.nubecita.core.auth

import kotlinx.coroutines.flow.Flow

/**
 * Single-consumer pipe for OAuth redirect URIs delivered to the app
 * via Android's deep-link plumbing. `MainActivity` publishes URIs
 * captured in `onCreate` / `onNewIntent`; `LoginViewModel` subscribes
 * in `init` and runs `AuthRepository.completeLogin` for each emission.
 *
 * Backed by a buffered [kotlinx.coroutines.channels.Channel] (not a
 * `SharedFlow` with replay) so that a redirect arriving during cold-start
 * — before `LoginViewModel` exists — is buffered until the VM subscribes,
 * but isn't replayed to subsequent collectors after delivery.
 *
 * Bound `@Singleton` in Hilt's `SingletonComponent`.
 */
interface OAuthRedirectBroker {
    /**
     * Hot stream of redirect URIs. Each emission is delivered to at most
     * one collector. Multi-collector usage is not supported — it would
     * race over a single Channel and produce undefined assignments.
     */
    val redirects: Flow<String>

    /**
     * Publish a redirect URI captured from an Android `Intent`. Suspends
     * only if the buffered channel fills (the default buffer is 64; in
     * practice at most one redirect is in flight per OAuth flow).
     */
    suspend fun publish(redirectUri: String)
}
