package net.kikin.nubecita.feature.search.impl.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
                entry?.takeIf { it.key == key } ?: install(key, fetch)
            }
        return current.result.await()
    }

    /** Runs [fetch] again for [key], replacing whatever is held. */
    suspend fun refresh(
        key: K,
        fetch: suspend () -> Result<V>,
    ): Result<V> {
        val current = synchronized(lock) { install(key, fetch) }
        return current.result.await()
    }

    fun clear() {
        synchronized(lock) { entry = null }
    }

    /**
     * Makes a new request the held entry, and arranges for the entry to drop
     * itself the moment the request fails or is cancelled, whether or not any
     * caller is still waiting. The handler is registered after the entry is
     * stored so a request that completes at once still finds it.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun install(
        key: K,
        fetch: suspend () -> Result<V>,
    ): Entry<K, V> {
        val created = start(key, fetch)
        entry = created
        created.result.invokeOnCompletion {
            val unusable = created.result.isCancelled || created.result.getCompleted().isFailure
            if (unusable) {
                synchronized(lock) { if (entry === created) entry = null }
            }
        }
        return created
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
}
