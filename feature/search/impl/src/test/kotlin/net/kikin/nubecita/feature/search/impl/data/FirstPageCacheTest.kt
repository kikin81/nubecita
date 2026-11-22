package net.kikin.nubecita.feature.search.impl.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirstPageCacheTest {
    @Test
    fun `two concurrent gets with one key run fetch once`() =
        runTest {
            val cache = FirstPageCache<String, String>(backgroundScope)
            val gate = CompletableDeferred<Result<String>>()
            var calls = 0
            val fetch: suspend () -> Result<String> = {
                calls++
                gate.await()
            }

            val first = async { cache.get("a", fetch) }
            val second = async { cache.get("a", fetch) }
            runCurrent()
            gate.complete(Result.success("value"))

            assertEquals("value", first.await().getOrThrow())
            assertEquals("value", second.await().getOrThrow())
            assertEquals(1, calls)
        }

    @Test
    fun `a failed fetch is not cached`() =
        runTest {
            val cache = FirstPageCache<String, String>(backgroundScope)
            var calls = 0

            val failed = cache.get("a") { calls++.let { Result.failure(IllegalStateException("boom")) } }
            val retried = cache.get("a") { calls++.let { Result.success("ok") } }

            assertTrue(failed.isFailure)
            assertEquals("ok", retried.getOrThrow())
            assertEquals(2, calls)
        }

    @Test
    fun `a different key replaces the entry`() =
        runTest {
            val cache = FirstPageCache<String, String>(backgroundScope)
            var calls = 0

            cache.get("a") { calls++.let { Result.success("a1") } }
            cache.get("b") { calls++.let { Result.success("b1") } }
            val again = cache.get("a") { calls++.let { Result.success("a2") } }

            assertEquals("a2", again.getOrThrow(), "key a was replaced by b, so it must refetch")
            assertEquals(3, calls)
        }

    @Test
    fun `refresh runs fetch again and returns the new value`() =
        runTest {
            val cache = FirstPageCache<String, String>(backgroundScope)
            var calls = 0

            cache.get("a") { calls++.let { Result.success("old") } }
            val refreshed = cache.refresh("a") { calls++.let { Result.success("new") } }
            val afterwards = cache.get("a") { calls++.let { Result.success("unexpected") } }

            assertEquals("new", refreshed.getOrThrow())
            assertEquals("new", afterwards.getOrThrow(), "the refreshed value is the cached one")
            assertEquals(2, calls)
        }

    @Test
    fun `a get that started before a key change never returns under the new key`() =
        runTest {
            val cache = FirstPageCache<String, String>(backgroundScope)
            val gateA = CompletableDeferred<Result<String>>()
            var bCalls = 0

            val callerA = async { cache.get("a") { gateA.await() } }
            runCurrent()
            val callerB = async { cache.get("b") { bCalls++.let { Result.success("b-value") } } }
            runCurrent()
            gateA.complete(Result.success("a-value"))

            assertEquals("a-value", callerA.await().getOrThrow())
            assertEquals("b-value", callerB.await().getOrThrow())
            val followUp = cache.get("b") { bCalls++.let { Result.success("refetched") } }
            assertEquals("b-value", followUp.getOrThrow())
            assertEquals(1, bCalls, "a late completion for A must not disturb B's entry")
        }
}
