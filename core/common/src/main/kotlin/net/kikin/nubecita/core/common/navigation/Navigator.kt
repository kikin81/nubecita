package net.kikin.nubecita.core.common.navigation

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey

/**
 * App-wide navigation seam. Owns the back stack as a Compose-observable
 * [SnapshotStateList]; `:app`'s `MainNavigation` reads from [backStack]
 * and any class with access to a [Navigator] instance (typically a
 * `@HiltViewModel` or a `LaunchedEffect`) can mutate it via [goTo],
 * [goBack], or [replaceTo].
 *
 * Bound `@Singleton` in Hilt's `SingletonComponent`. The default
 * implementation is initialized with the start destination injected from
 * `:app` via [StartDestination].
 */
interface Navigator {
    /**
     * The live back stack. Top of the stack is `last()`. Mutations
     * (additions, removals) trigger Compose recomposition for any
     * composable that reads the list.
     */
    val backStack: SnapshotStateList<NavKey>

    /** Push [key] onto the top of the stack. */
    fun goTo(key: NavKey)

    /**
     * Pop the top of the stack. No-op if the stack is empty. Callers
     * shouldn't rely on the empty-stack case in production — `MainNavigation`
     * doesn't currently render a fallback when the stack is empty.
     */
    fun goBack()

    /**
     * Close the screen on top and open [key] inside `MainShell`.
     *
     * For an `@OuterShell` screen reaching a route that only exists on the
     * inner stack, which pushing with [goTo] would crash on: the outer
     * `NavDisplay` has no entry for it. `MainShell` may not be composed under
     * the outer screen, so the route waits in [DeepLinkRouter] until it is.
     */
    fun closeAndOpenInMainShell(key: NavKey)

    /** Clear the stack and push [key] as the sole entry. */
    fun replaceTo(key: NavKey)
}
