package net.kikin.nubecita.core.common.session

/**
 * Marker for singleton objects that hold session-scoped state and must be
 * reset when the user signs out.
 *
 * Implementations are contributed via Hilt multibinding (`@IntoSet`) in their
 * own module's Hilt module. [DefaultAuthRepository][net.kikin.nubecita.core.auth.DefaultAuthRepository]
 * injects `Set<SessionClearable>` and iterates it inside `signOut()`.
 *
 * # Why a multibinding instead of a direct dep?
 *
 * `:core:post-interactions` already depends on `:core:auth` (for
 * `XrpcClientProvider` / `SessionStateProvider`). Injecting
 * `PostInteractionsCache` directly into `:core:auth` would create a circular
 * module dependency. The multibinding in the neutral `:core:common` module
 * breaks the cycle: `:core:auth` sees only `Set<SessionClearable>`, and
 * `:core:post-interactions` sees only the same interface.
 */
fun interface SessionClearable {
    /**
     * Reset all session-scoped state — and, when needed, issue a network
     * call that requires the still-valid session.
     *
     * Called sequentially from the coroutine that drives sign-out, BEFORE
     * `atOAuth.logout()` revokes the OAuth tokens. Implementations may suspend
     * to make authenticated XRPC calls (e.g. `:core:push`'s
     * `unregisterPush`) — those calls require a live session, so doing them
     * here is the only correct path. Implementations must be idempotent and
     * should fail-fast on errors rather than hanging the sign-out flow.
     *
     * Non-suspending implementations (e.g. in-memory cache reset) are fine;
     * `suspend` is a superset.
     */
    suspend fun clearSession()
}
