package net.kikin.nubecita.core.common.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer

/**
 * Compose-owned multi-tab navigation state holder for `MainShell`.
 *
 * Models the Nav 3 `multiple-backstacks` recipe shape: one [NavBackStack]
 * per top-level route plus a single active-tab pointer ([topLevelKey]).
 * Sub-routes pushed via [add] land on the active tab's stack; tab switches
 * via [addTopLevel] flip the pointer without touching any stack contents,
 * so a tab that's been left and returned to picks up at the same depth.
 *
 * The "exit through home" rule (also from the recipe) governs [removeLast]:
 *
 * 1. If the active tab has sub-routes pushed, pop the top sub-route.
 * 2. Else, if the active tab is **not** the start route, switch active to
 *    the start route. The previously-active tab's stack is **not cleared**
 *    — re-selecting that tab will restore the user where they left off.
 * 3. Else (active is the start route and its stack contains only its own
 *    top-level key), there's nothing left to pop and the host should
 *    defer to system back. [removeLast] returns `false` in this case;
 *    every other case returns `true`.
 *
 * The [backStack] property is the flattened view that the inner
 * `NavDisplay` reads. It always contains the start tab's full stack, and
 * — when the active tab is not the start route — appends the active
 * tab's full stack on top. Equivalent to the recipe's "stacks in use"
 * concatenation; populated lazily by the mutation methods so Compose
 * snapshot observation in `NavDisplay` sees the correct value.
 *
 * # Construction & lifecycle
 *
 * Use [rememberMainShellNavState] inside `MainShell`'s composable body.
 * The factory wires [topLevelKey] persistence via `rememberSerializable`
 * and per-tab stacks via `rememberNavBackStack`, so configuration change
 * and process death restore prior state.
 *
 * The class is intentionally **not** `@Inject`-able. It is created in a
 * Composable, lives for the lifetime of the `Main` `NavEntry`, and is GC'd
 * when `Main` leaves the outer back stack (e.g., post-logout). ViewModels
 * MUST NOT receive a [MainShellNavState] — tab-internal navigation flows
 * through `UiEffect.Navigate(target)` collected by the screen Composable,
 * which then calls [add] on the state held by [LocalMainShellNavState].
 *
 * # Direct construction (tests only)
 *
 * The primary constructor is public so unit tests can construct an
 * instance without a Composable harness — pass `mutableStateOf(startRoute)`
 * for [topLevelKeyState] and `NavBackStack(key)` per top-level route in
 * [backStacks]. Production code SHOULD always go through
 * [rememberMainShellNavState].
 *
 * @property startRoute The top-level route the user always exits through.
 *   Must appear as a key in [backStacks].
 * @property topLevelKey The active tab. Mutating switches tabs.
 * @property backStack Flattened view fed to `NavDisplay`.
 */
class MainShellNavState(
    val startRoute: NavKey,
    topLevelKeyState: MutableState<NavKey>,
    private val backStacks: Map<NavKey, NavBackStack<NavKey>>,
) {
    init {
        require(startRoute in backStacks.keys) {
            "startRoute=$startRoute must be one of the top-level routes (got keys=${backStacks.keys})."
        }
        require(topLevelKeyState.value in backStacks.keys) {
            "topLevelKey=${topLevelKeyState.value} must be one of the top-level routes " +
                "(got keys=${backStacks.keys})."
        }
    }

    var topLevelKey: NavKey by topLevelKeyState
        private set

    private val _backStack: SnapshotStateList<NavKey> =
        mutableStateListOf<NavKey>().also { list ->
            list.addAll(backStacks.getValue(startRoute))
            if (topLevelKeyState.value != startRoute) {
                list.addAll(backStacks.getValue(topLevelKeyState.value))
            }
        }

    /**
     * The flattened view of the start tab's stack followed by the active
     * tab's stack (when the active tab isn't the start route). Suitable
     * for passing directly to `NavDisplay.backStack`.
     */
    val backStack: SnapshotStateList<NavKey>
        get() = _backStack

    /**
     * Switch the active tab to [key]. The outgoing tab's stack is
     * preserved — re-selecting it later restores its prior depth.
     *
     * @throws IllegalArgumentException if [key] is not one of the
     *   top-level routes this state holder was constructed with.
     */
    fun addTopLevel(key: NavKey) {
        require(key in backStacks.keys) {
            "addTopLevel($key) is not a known top-level route. Known: ${backStacks.keys}."
        }
        topLevelKey = key
        _backStack.rebuild()
    }

    /**
     * Push [key] onto the active tab's stack. Used for sub-route
     * navigation (e.g., a Profile pushed onto the Feed tab from a tap on
     * an author handle).
     *
     * **Single-top semantics.** Calling `add(key)` when [key] structurally
     * equals the current top of the active tab's stack is a silent no-op:
     * the stack is unchanged and [backStack] is not rebuilt. This prevents
     * the "tap-post-on-PostDetail-stacks-N-copies" bug — the user can
     * keep tapping the focused post on PostDetail without accumulating
     * duplicate `PostDetailRoute` entries. The guard only looks at the
     * top of the active tab's stack; pushing a key that appears earlier
     * in the stack but isn't on top is still a real push (e.g., from
     * `PostDetail(B)` whose parent thread shows `PostDetail(A)`'s root
     * link, tapping that link genuinely re-enters `A` so the user can see
     * it as a focused post, even if `A` is below `B` in the stack).
     *
     * Relies on every [NavKey] in this project being a `@Serializable
     * data class` / `data object`, so `==` is structural across instances.
     *
     * Worked examples (active tab stack top → bottom):
     * | Before | `add(X)` | After |
     * |---|---|---|
     * | `[Y]` | push | `[Y, X]` |
     * | `[Y, X]` | push | `[Y, X]` (no-op) |
     * | `[X]` | push | `[X]` (no-op; tab home == target) |
     * | `[X, Y]` | push | `[X, Y, X]` (X is in stack but not on top) |
     */
    fun add(key: NavKey) {
        val stack = backStacks.getValue(topLevelKey)
        if (stack.lastOrNull() == key) return
        stack.add(key)
        _backStack.rebuild()
    }

    /**
     * Pop per the "exit through home" rule (see class kdoc).
     *
     * @return `true` if a pop or tab switch occurred; `false` if the
     *   stack was already at the start tab's home and the host should
     *   defer to the system back handler.
     */
    fun removeLast(): Boolean {
        val activeStack = backStacks.getValue(topLevelKey)
        return when {
            // Sub-route on the active tab → pop within the tab.
            activeStack.size > 1 -> {
                activeStack.removeAt(activeStack.size - 1)
                _backStack.rebuild()
                true
            }
            // At a non-start top-level → switch back to the start tab.
            // The non-start tab's stack is NOT cleared; re-entry restores it.
            topLevelKey != startRoute -> {
                topLevelKey = startRoute
                _backStack.rebuild()
                true
            }
            // At the start tab's home → nothing left to pop.
            else -> false
        }
    }

    /**
     * Repopulate [_backStack] from the current [topLevelKey] + [backStacks]
     * map, mirroring the recipe's "stacks in use" rule: the start tab's
     * stack first, plus the active tab's stack on top when active isn't
     * the start route.
     */
    private fun SnapshotStateList<NavKey>.rebuild() {
        clear()
        addAll(backStacks.getValue(startRoute))
        if (topLevelKey != startRoute) {
            addAll(backStacks.getValue(topLevelKey))
        }
    }
}

/**
 * Compose-scoped factory for [MainShellNavState] that wires up
 * configuration-change and process-death persistence:
 *
 * - [topLevelKey][MainShellNavState.topLevelKey] is backed by a
 *   `rememberSerializable(MutableStateSerializer(NavKeySerializer()))`
 *   so the active tab survives `saveInstanceState → recreate()`.
 * - Per-tab back stacks are created via `rememberNavBackStack(key)`, so
 *   each tab's stack contents survive too.
 *
 * Call from inside `MainShell`'s composable body. Do not call from a
 * `ViewModel` or any non-Composable context.
 *
 * @param startRoute The top-level route the user always exits through.
 *   Must appear in [topLevelRoutes].
 * @param topLevelRoutes The ordered list of top-level routes the shell
 *   manages. **Iteration order is load-bearing**: this factory issues one
 *   `rememberNavBackStack(key)` call per element in order, and Compose
 *   keys those `remember` slots by composer position. A reordered list
 *   across recompositions would re-associate persisted stacks with the
 *   wrong keys. List was chosen over `Set` so the contract is enforced
 *   by the type system. Must contain unique keys (enforced via require)
 *   and must include [startRoute].
 */
@Composable
fun rememberMainShellNavState(
    startRoute: NavKey,
    topLevelRoutes: List<NavKey>,
): MainShellNavState {
    require(startRoute in topLevelRoutes) {
        "startRoute=$startRoute must be in topLevelRoutes=$topLevelRoutes."
    }
    require(topLevelRoutes.toSet().size == topLevelRoutes.size) {
        "topLevelRoutes must contain unique keys (got $topLevelRoutes)."
    }

    val topLevelKeyState =
        rememberSerializable(
            startRoute,
            topLevelRoutes,
            serializer = MutableStateSerializer(NavKeySerializer()),
        ) {
            mutableStateOf(startRoute)
        }

    // `rememberNavBackStack` is itself `rememberSerializable`-backed, so each
    // tab's stack survives process death independently.
    val backStacks: Map<NavKey, NavBackStack<NavKey>> =
        topLevelRoutes.associateWith { key -> rememberNavBackStack(key) }

    return remember(startRoute, topLevelRoutes) {
        MainShellNavState(
            startRoute = startRoute,
            topLevelKeyState = topLevelKeyState,
            backStacks = backStacks,
        )
    }
}
