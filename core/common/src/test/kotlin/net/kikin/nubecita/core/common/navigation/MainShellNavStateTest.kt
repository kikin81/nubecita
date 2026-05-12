package net.kikin.nubecita.core.common.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [MainShellNavState]. Constructs the state holder via its
 * primary constructor (no Composable harness) — process-death persistence
 * is delegated to `rememberSerializable` + `rememberNavBackStack` and is
 * verified separately by the instrumented test in `:app` (see
 * `nubecita-8m4` task 7.1). The round-trip case here exercises the
 * structural reconstruction contract: given the persisted primitives that
 * `rememberSerializable` would hand back, a fresh `MainShellNavState`
 * exposes the same `topLevelKey` and per-tab stack contents.
 */
class MainShellNavStateTest {
    @Test
    fun `addTopLevel switches active tab and preserves outgoing tab stack`() {
        val state = newState(start = TabFeed, top = setOf(TabFeed, TabSearch, TabChats))
        state.add(SubProfile) // Feed: [Feed, Profile]
        assertEquals(listOf<NavKey>(TabFeed, SubProfile), state.backStack.toList())

        state.addTopLevel(TabSearch)

        assertSame(TabSearch, state.topLevelKey)
        // After switching, returning to Feed must restore the prior depth.
        state.addTopLevel(TabFeed)
        assertSame(TabFeed, state.topLevelKey)
        assertEquals(listOf<NavKey>(TabFeed, SubProfile), state.backStack.toList())
    }

    @Test
    fun `returning to previously active tab restores its stack at the same depth`() {
        val state = newState(start = TabFeed, top = setOf(TabFeed, TabSearch))
        state.addTopLevel(TabSearch)
        state.add(SubProfile) // Search: [Search, Profile]
        assertEquals(
            // Flattened: start tab home, then active tab's full stack.
            listOf<NavKey>(TabFeed, TabSearch, SubProfile),
            state.backStack.toList(),
        )

        state.addTopLevel(TabFeed) // Leave Search.
        state.addTopLevel(TabSearch) // Come back.

        assertSame(TabSearch, state.topLevelKey)
        assertEquals(
            listOf<NavKey>(TabFeed, TabSearch, SubProfile),
            state.backStack.toList(),
        )
    }

    @Test
    fun `removeLast from sub-route pops within active tab and does not switch tabs`() {
        val state = newState(start = TabFeed, top = setOf(TabFeed, TabSearch))
        state.addTopLevel(TabSearch)
        state.add(SubProfile) // Search: [Search, Profile]

        val popped = state.removeLast()

        assertTrue(popped, "removeLast on a sub-route should signal a successful pop")
        assertSame(TabSearch, state.topLevelKey)
        assertEquals(listOf<NavKey>(TabFeed, TabSearch), state.backStack.toList())
    }

    @Test
    fun `removeLast from non-start top-level switches active tab to start route without clearing the popped tab stack`() {
        val state = newState(start = TabFeed, top = setOf(TabFeed, TabSearch))
        state.addTopLevel(TabSearch)
        state.add(SubProfile) // Search: [Search, Profile]
        state.removeLast() // pop Profile → Search: [Search]
        assertSame(TabSearch, state.topLevelKey)

        val popped = state.removeLast()

        assertTrue(popped, "removeLast at non-start top-level should signal a tab switch")
        assertSame(TabFeed, state.topLevelKey)
        // Search's stack must still hold its top-level key so a re-switch
        // restores it. Verified by switching back and reading the flat view.
        state.addTopLevel(TabSearch)
        assertEquals(listOf<NavKey>(TabFeed, TabSearch), state.backStack.toList())
    }

    @Test
    fun `removeLast from start-route home returns false so the host can defer to system back`() {
        val state = newState(start = TabFeed, top = setOf(TabFeed, TabSearch))
        // Already at start tab home — backStack is just [Feed].
        assertEquals(listOf<NavKey>(TabFeed), state.backStack.toList())

        val popped = state.removeLast()

        assertFalse(popped, "removeLast at start-route home must return false to defer to system back")
        // No mutation should have occurred.
        assertSame(TabFeed, state.topLevelKey)
        assertEquals(listOf<NavKey>(TabFeed), state.backStack.toList())
    }

    @Test
    fun `add pushes a key that differs from current top`() {
        val state = newState(start = TabFeed, top = setOf(TabFeed, TabSearch))

        state.add(SubPost("at://x"))

        assertEquals(listOf<NavKey>(TabFeed, SubPost("at://x")), state.backStack.toList())
    }

    @Test
    fun `add is a silent no-op when key structurally equals the current top`() {
        val state = newState(start = TabFeed, top = setOf(TabFeed, TabSearch))
        state.add(SubPost("at://x")) // Feed: [Feed, Post(at://x)]
        assertEquals(listOf<NavKey>(TabFeed, SubPost("at://x")), state.backStack.toList())

        // Re-add the same payload via a fresh instance — structural equality
        // MUST cause the second push to be dropped (the "tap-post-on-PostDetail
        // -stacks-N-copies" bug).
        state.add(SubPost("at://x"))

        assertEquals(
            listOf<NavKey>(TabFeed, SubPost("at://x")),
            state.backStack.toList(),
            "add(top) MUST be a no-op when key equals the active tab's top",
        )
    }

    @Test
    fun `add is a no-op when key equals the active tab's home key`() {
        val state = newState(start = TabFeed, top = setOf(TabFeed, TabSearch))
        // Active tab is Feed with stack [Feed]; pushing Feed again would
        // create [Feed, Feed] which is meaningless. Guard must catch it.

        state.add(TabFeed)

        assertEquals(
            listOf<NavKey>(TabFeed),
            state.backStack.toList(),
            "add(home) MUST be a no-op when home is already the top of the active tab's stack",
        )
    }

    @Test
    fun `add pushes a key that appears earlier in the stack but is not on top`() {
        // Models: PostDetail(A) → tap reply → PostDetail(B) → tap A's root
        // link → user genuinely wants to re-focus on A even though A is in
        // the stack below B. The guard only looks at the top, so this push
        // is allowed and produces [A, B, A].
        val state = newState(start = TabFeed, top = setOf(TabFeed, TabSearch))
        state.add(SubPost("at://a")) // [Feed, A]
        state.add(SubPost("at://b")) // [Feed, A, B]

        state.add(SubPost("at://a")) // A is in the stack but NOT on top — push.

        assertEquals(
            listOf<NavKey>(TabFeed, SubPost("at://a"), SubPost("at://b"), SubPost("at://a")),
            state.backStack.toList(),
            "add(X) MUST push when X is in the stack but not at the top",
        )
    }

    @Test
    fun `persistence round-trip via saved primitives restores topLevelKey and per-tab stacks`() {
        val before = newState(start = TabFeed, top = setOf(TabFeed, TabSearch, TabChats))
        before.add(SubProfile) // Feed: [Feed, Profile]
        before.addTopLevel(TabSearch)
        before.add(SubSettings) // Search: [Search, Settings]

        // Snapshot the primitives that `rememberSerializable` /
        // `rememberNavBackStack` would persist for us in production.
        val savedTopLevelKey: NavKey = before.topLevelKey
        val savedStacks: Map<NavKey, List<NavKey>> = before.snapshotStacks()

        // Reconstruct as if Compose just restored from saved state.
        val after =
            MainShellNavState(
                startRoute = TabFeed,
                topLevelKeyState = mutableStateOf(savedTopLevelKey),
                backStacks =
                    savedStacks.mapValues { (_, snapshot) ->
                        NavBackStack<NavKey>(*snapshot.toTypedArray())
                    },
            )

        assertSame(TabSearch, after.topLevelKey)
        assertEquals(
            listOf<NavKey>(TabFeed, SubProfile, TabSearch, SubSettings),
            after.backStack.toList(),
        )
        // Switching back also exposes the preserved Feed stack — proves all
        // per-tab snapshots survived the round trip, not just the active one.
        after.addTopLevel(TabFeed)
        assertEquals(listOf<NavKey>(TabFeed, SubProfile), after.backStack.toList())
    }

    /**
     * Build a fresh state holder from a start route and top-level set,
     * mirroring what `rememberMainShellNavState` does in production but
     * without a Composable harness.
     */
    private fun newState(
        start: NavKey,
        top: Set<NavKey>,
    ): MainShellNavState =
        MainShellNavState(
            startRoute = start,
            topLevelKeyState = mutableStateOf(start),
            backStacks = top.associateWith { key -> NavBackStack<NavKey>(key) },
        )

    /**
     * Capture each per-tab stack as a plain `List<NavKey>` — the shape
     * `rememberNavBackStack`'s saver would hand back after process death.
     * Reflection over the private map keeps the test honest about which
     * tabs were touched (we want to round-trip *all* of them).
     */
    private fun MainShellNavState.snapshotStacks(): Map<NavKey, List<NavKey>> {
        val field = MainShellNavState::class.java.getDeclaredField("backStacks")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(this) as Map<NavKey, NavBackStack<NavKey>>
        return map.mapValues { (_, stack) -> stack.toList() }
    }
}

private data object TabFeed : NavKey

private data object TabSearch : NavKey

private data object TabChats : NavKey

private data object SubProfile : NavKey

private data object SubSettings : NavKey

/**
 * `data class` test fixture for single-top semantics: two `SubPost(uri)`
 * instances with the same `uri` are structurally equal, which is exactly
 * the property the production guard depends on (every real `NavKey` in
 * the project is `@Serializable data class` / `data object`).
 */
private data class SubPost(
    val uri: String,
) : NavKey
