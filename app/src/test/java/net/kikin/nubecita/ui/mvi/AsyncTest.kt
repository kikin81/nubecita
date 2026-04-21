package net.kikin.nubecita.ui.mvi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

internal class AsyncTest {
    @Test
    fun `getOrNull returns value for Success`() {
        assertEquals("hello", Async.Success("hello").getOrNull())
    }

    @Test
    fun `getOrNull returns null for Uninitialized Loading and Failure`() {
        assertNull(Async.Uninitialized.getOrNull())
        assertNull(Async.Loading.getOrNull())
        assertNull(Async.Failure(RuntimeException("boom")).getOrNull())
    }

    @Test
    fun `map on Success transforms the value`() {
        val result = Async.Success(listOf(1, 2, 3)).map { it.size }
        assertEquals(Async.Success(3), result)
    }

    @Test
    fun `map on Loading returns the same singleton`() {
        val result: Async<Int> = Async.Loading.map<Nothing, Int> { throw AssertionError("not invoked") }
        assertSame(Async.Loading, result)
    }

    @Test
    fun `map on Uninitialized returns the same singleton`() {
        val result: Async<Int> = Async.Uninitialized.map<Nothing, Int> { throw AssertionError("not invoked") }
        assertSame(Async.Uninitialized, result)
    }

    @Test
    fun `map on Failure preserves the throwable and does not invoke transform`() {
        val error = RuntimeException("boom")
        val failure: Async<Int> = Async.Failure(error)
        var invoked = false
        val result =
            failure.map {
                invoked = true
                it + 1
            }
        assertFalse(invoked)
        assertEquals(Async.Failure(error), result)
        assertSame(error, (result as Async.Failure).error)
    }
}
