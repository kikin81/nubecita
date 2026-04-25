package net.kikin.nubecita.core.auth

/**
 * Reactive view of the user's authentication state, exposed via
 * [SessionStateProvider]. The single source of truth for "is the user
 * authenticated right now?" — every consumer (`MainActivity`'s splash
 * routing, future settings/profile screens, auth-required screens)
 * reads from a `Flow<SessionState>` rather than re-querying the
 * `OAuthSessionStore` directly.
 *
 * `SignedIn` exposes only the user-identifying parts of the persisted
 * `OAuthSession`. Tokens and DPoP key material stay inside the store and
 * are reachable only through the auth flow's authenticated `XrpcClient`.
 */
sealed interface SessionState {
    /**
     * Initial state, before [SessionStateProvider.refresh] has been
     * called for the first time. The system splash overlays the UI while
     * this state is observed.
     */
    data object Loading : SessionState

    /** No session in the store. */
    data object SignedOut : SessionState

    /** A session is persisted. The two fields are the user-identifying parts. */
    data class SignedIn(
        val handle: String,
        val did: String,
    ) : SessionState
}
