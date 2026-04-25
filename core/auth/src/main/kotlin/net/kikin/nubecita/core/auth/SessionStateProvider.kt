package net.kikin.nubecita.core.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * Reactive [SessionState] view over [io.github.kikin81.atproto.oauth.OAuthSessionStore].
 * Hilt singleton; consumers (typically `MainActivity` and future
 * settings/profile screens) observe [state] and react to transitions.
 *
 * The provider does not eagerly query the store at construction —
 * callers drive [refresh] explicitly. `MainActivity` triggers the
 * initial refresh on cold start; `AuthRepository` triggers refreshes
 * after `completeLogin` and `signOut` so reactive consumers transition
 * automatically.
 *
 * Implementation backs [state] with a `MutableStateFlow` so the
 * synchronous `state.value` read used by the system SplashScreen API's
 * `setKeepOnScreenCondition` predicate works from the platform's frame
 * callback (no coroutine context needed).
 */
interface SessionStateProvider {
    /**
     * Hot stream of session states. Initial value is
     * [SessionState.Loading]; transitions to [SessionState.SignedIn] or
     * [SessionState.SignedOut] after the first [refresh].
     */
    val state: StateFlow<SessionState>

    /**
     * Re-query [io.github.kikin81.atproto.oauth.OAuthSessionStore.load]
     * and update [state]. Idempotent.
     */
    suspend fun refresh()
}
