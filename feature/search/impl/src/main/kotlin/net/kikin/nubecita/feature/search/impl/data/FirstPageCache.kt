package net.kikin.nubecita.feature.search.impl.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * A one-entry cache of the first page of a search, so two callers asking for
 * the same page share one network request.
 *
 * The entry is the key plus the [Deferred] of its request, started in [scope]
 * rather than in the caller, so one caller going away does not cancel the
 * request another caller is awaiting. [get] with the key already held awaits
 * that [Deferred]; any other key replaces the entry. A failed result removes
 * its entry so the next call retries. Each caller awaits the [Deferred] it
 * was handed, so a request that finishes after the key has changed can only
 * ever answer its own callers.
 */
internal class FirstPageCache<K : Any, V>(
    private val scope: CoroutineScope,
) {
    private class Entry<K, V>(
        val key: K,
        val result: Deferred<Result<V>>,
    )

    private val lock = Any()
    private var entry: Entry<K, V>? = null

    suspend fun get(
        key: K,
        fetch: suspend () -> Result<V>,
    ): Result<V> {
        val current =
            synchronized(lock) {
                entry?.takeIf { it.key == key } ?: start(key, fetch).also { entry = it }
            }
        return await(current)
    }

    /** Runs [fetch] again for [key], replacing whatever is held. */
    suspend fun refresh(
        key: K,
        fetch: suspend () -> Result<V>,
    ): Result<V> {
        val current = synchronized(lock) { start(key, fetch).also { entry = it } }
        return await(current)
    }

    fun clear() {
        synchronized(lock) { entry = null }
    }

    private fun start(
        key: K,
        fetch: suspend () -> Result<V>,
    ): Entry<K, V> =
        Entry(
            key,
            scope.async {
                try {
                    fetch()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    Result.failure(t)
                }
            },
        )

    private suspend fun await(current: Entry<K, V>): Result<V> {
        val result = current.result.await()
        if (result.isFailure) {
            synchronized(lock) { if (entry === current) entry = null }
        }
        return result
    }
}
