package net.kikin.nubecita.core.common.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun `goBack on empty stack is a no-op`() {
        val navigator = DefaultNavigator(start = TestStart)
        navigator.goBack() // pops TestStart
        assertTrue(navigator.backStack.isEmpty())

        navigator.goBack() // no-op

        assertTrue(navigator.backStack.isEmpty())
    }

    @Test
    fun `replaceTo clears the stack and pushes the key`() {
        val navigator = DefaultNavigator(start = TestStart)
        navigator.goTo(TestProfile)
        navigator.goTo(TestSettings)

        navigator.replaceTo(TestStart)

        assertEquals(listOf<NavKey>(TestStart), navigator.backStack.toList())
    }
}

private data object TestStart : NavKey

private data object TestProfile : NavKey

private data object TestSettings : NavKey
