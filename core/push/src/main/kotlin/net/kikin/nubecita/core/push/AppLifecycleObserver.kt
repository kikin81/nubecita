package net.kikin.nubecita.core.push

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
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
    private val lifecycle: Lifecycle = ProcessLifecycleOwner.get().lifecycle,
) : DefaultLifecycleObserver {
    /**
     * Registers the observer with [lifecycle] and triggers the disk-cache
     * hydration. Idempotent — re-registering an already-attached observer
     * is a no-op in [androidx.lifecycle.LifecycleRegistry].
     */
    fun start() {
        scope.launch { mutedActorRepository.loadFromDisk() }
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        scope.launch {
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
