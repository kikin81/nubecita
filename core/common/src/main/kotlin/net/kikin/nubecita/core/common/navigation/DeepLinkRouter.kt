package net.kikin.nubecita.core.common.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.Flow

/**
 * Single-consumer pipe for deep-link navigation targets resolved at the
 * Activity boundary. `MainActivity.handleIntent` matches an incoming
 * `Intent` against the Hilt-bound `Set<NavKeyDeepLinkMatcher>` and, on
 * match, publishes the resolved [NavKey] here; `MainShell`'s
 * `LaunchedEffect` collects the flow and pushes each emission onto the
 * inner `MainShellNavState` back stack.
 *
 * Backed by a buffered [kotlinx.coroutines.channels.Channel] (not a
 * `SharedFlow` with replay) so that a deep link arriving during
 * cold-start — before `MainShell` exists — is buffered until the shell
 * subscribes, but isn't replayed to subsequent collectors after
 * delivery. Mirrors the shape used by `OAuthRedirectBroker` for the
 * cold-start OAuth redirect path.
 *
 * Bound `@Singleton` in Hilt's `SingletonComponent` via
 * [DeepLinkRouterModule].
 */
interface DeepLinkRouter {
    /**
     * Hot stream of resolved deep-link targets. Each emission is
     * delivered to at most one collector. Multi-collector usage is not
     * supported — it would race over a single Channel and produce
     * undefined assignments. `MainShell` is the single intended
     * consumer.
     */
    val pendingDeepLinks: Flow<NavKey>

    /**
     * Publish a [NavKey] resolved from an incoming `Intent`. Suspends
     * only if the buffered channel fills (the default buffer is 64; in
     * practice deep links arrive one at a time per Activity intent).
     */
    suspend fun publish(target: NavKey)
}
