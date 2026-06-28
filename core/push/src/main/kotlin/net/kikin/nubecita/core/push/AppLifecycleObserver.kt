package net.kikin.nubecita.core.push

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Wires [MutedActorRepository] to the app's foreground lifecycle. Hydrates
 * the in-memory snapshot from DataStore at [start] time, then refreshes the
 * snapshot whenever the process enters the foreground via
 * `Lifecycle.Event.ON_START` on the [ProcessLifecycleOwner].
 *
 * The 12-hour debounce inside [MutedActorRepository.refresh] guards against
 * a burst of `ON_START` deliveries during quick app-switch flicker — only
 * the first cross-window refresh actually hits the network.
 *
 * Lifecycle dependency is injectable for tests (default
 * `ProcessLifecycleOwner.get().lifecycle`); instrumented coverage in
 * `:core:push/src/androidTest/...` exercises the real ProcessLifecycleOwner.
 */
class AppLifecycleObserver(
    private val mutedActorRepository: MutedActorRepository,
    private val scope: CoroutineScope,
    // Null in production; resolved on the main thread inside [start]. Kept out of
    // the constructor so the observer can be CONSTRUCTED off the main thread during
    // deferred startup — `ProcessLifecycleOwner.get()` is main-thread-only and was
    // blocking Application.onCreate (nubecita-jicb). Tests inject a fake directly.
    private val lifecycle: Lifecycle? = null,
) : DefaultLifecycleObserver {
    /**
     * Tracks the [start]-launched disk-hydration job so [onStart] can `join`
     * it before refreshing — guards against the race documented on
     * [onStart].
     */
    private var hydrationJob: Job? = null

    /**
     * Registers the observer with the process lifecycle and triggers the
     * disk-cache hydration. Idempotent — re-registering an already-attached
     * observer is a no-op in [androidx.lifecycle.LifecycleRegistry].
     *
     * MUST be invoked on the main thread: both [ProcessLifecycleOwner.get] and
     * [Lifecycle.addObserver] are main-thread-only.
     */
    fun start() {
        val lifecycle = lifecycle ?: ProcessLifecycleOwner.get().lifecycle
        hydrationJob = scope.launch { mutedActorRepository.loadFromDisk() }
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        scope.launch {
            // Wait for the initial disk hydration to finish before kicking
            // off the foreground refresh. `LifecycleRegistry.addObserver`
            // synchronously dispatches catch-up events up to the current
            // state, so if the process is already STARTED when `start()`
            // runs, this `onStart` fires immediately and can race the
            // launched `loadFromDisk` — without the join, a fast refresh
            // could write the fresh network snapshot first and then
            // `loadFromDisk` could overwrite it with stale disk data.
            hydrationJob?.join()
            mutedActorRepository
                .refresh()
                .onFailure {
                    Timber.tag(TAG).w(
                        it,
                        "MutedActorRepository.refresh() failed on ON_START — will retry on the next foreground event",
                    )
                }
        }
    }

    private companion object {
        const val TAG = "PushLifecycle"
    }
}
