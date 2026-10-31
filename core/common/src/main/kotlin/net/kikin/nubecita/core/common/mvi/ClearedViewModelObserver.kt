package net.kikin.nubecita.core.common.mvi

import androidx.lifecycle.ViewModel

/**
 * Process-wide hook notified when any [MviViewModel] is cleared.
 *
 * Exists for debug leak detection: LeakCanary only watches ViewModels owned by
 * an Activity or Fragment `ViewModelStore`, not the per-entry stores created by
 * Navigation 3's `rememberViewModelStoreNavEntryDecorator`. The debug build sets
 * [onCleared] to hand each cleared ViewModel to LeakCanary's object watcher;
 * release builds leave it `null`, so the hook costs one null check per clear.
 *
 * Invoked on the main thread, from the ViewModel's own `addCloseable` callback.
 */
object ClearedViewModelObserver {
    @Volatile
    var onCleared: ((ViewModel) -> Unit)? = null
}
