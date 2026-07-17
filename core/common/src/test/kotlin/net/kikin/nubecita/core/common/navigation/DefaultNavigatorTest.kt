package net.kikin.nubecita.core.common.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class DefaultNavigatorTest {
    @Test
    fun `back stack starts with the injected start destination`() {
        val navigator = DefaultNavigator(start = TestStart)

        assertEquals(1, navigator.backStack.size)
        assertSame(TestStart, navigator.backStack.first())
    }

    @Test
    fun `goTo appends to the top of the stack`() {
        val navigator = DefaultNavigator(start = TestStart)

        navigator.goTo(TestProfile)

        assertEquals(listOf<NavKey>(TestStart, TestProfile), navigator.backStack.toList())
    }

    @Test
    fun `goBack pops the top of the stack`() {
        val navigator = DefaultNavigator(start = TestStart)
        navigator.goTo(TestProfile)

        navigator.goBack()

        assertEquals(listOf<NavKey>(TestStart), navigator.backStack.toList())
    }

    @Test
    fun `goBack never empties the stack`() {
        val navigator = DefaultNavigator(start = TestStart)

        // Single-entry stack: goBack must NOT pop the last entry. The outer
        // NavDisplay requires >= 1 entry, and a double-pop (an effect-driven
        // goBack racing the NavDisplay back handler, or a rapid double back)
        // would otherwise crash it with "NavDisplay backstack cannot be empty"
        // (nubecita-yqva). At the root, the platform's back handling takes over.
        navigator.goBack()
        assertEquals(listOf<NavKey>(TestStart), navigator.backStack.toList())

        // A second (racing) goBack is still a safe no-op — the stack stays put.
        navigator.goBack()
        assertEquals(listOf<NavKey>(TestStart), navigator.backStack.toList())
    }

    @Test
    fun `replaceTo clears the stack and pushes the key`() {
        val navigator = DefaultNavigator(start = TestStart)
        navigator.goTo(TestProfile)
        navigator.goTo(TestSettings)

        navigator.replaceTo(TestStart)

        assertEquals(listOf<NavKey>(TestStart), navigator.backStack.toList())
    }

    @Test
    fun `replaceTo is idempotent when the same key is already the sole entry`() {
        val navigator = DefaultNavigator(start = TestStart)
        navigator.replaceTo(TestProfile)
        val firstReference = navigator.backStack.toList()

        navigator.replaceTo(TestProfile)

        // Same key; the no-op guard prevents the clear+re-add cycle so any
        // destination-scoped state (rememberSaveable, ViewModel) is preserved.
        assertEquals(firstReference, navigator.backStack.toList())
    }

    @Test
    fun `replaceTo with a different key still clears even if the previous top matches`() {
        val navigator = DefaultNavigator(start = TestStart)
        navigator.goTo(TestProfile) // stack: [TestStart, TestProfile]

        navigator.replaceTo(TestStart)

        // The guard only fires when the back stack is a SINGLE entry equal to
        // the target — a multi-entry stack with the target on top must still
        // reset down to a single entry. Verifies the guard isn't over-eager.
        assertEquals(listOf<NavKey>(TestStart), navigator.backStack.toList())
    }
}

private data object TestStart : NavKey

private data object TestProfile : NavKey

private data object TestSettings : NavKey
