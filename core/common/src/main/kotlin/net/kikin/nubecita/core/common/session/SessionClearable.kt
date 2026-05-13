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
     * Reset all session-scoped in-memory state.
     *
     * Called synchronously on the coroutine that drives sign-out, before the
     * network revocation request. Implementations must be non-suspending and
     * idempotent.
     */
    fun clearSession()
}
