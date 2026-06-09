package net.kikin.nubecita.core.widgetsync.worker

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import javax.inject.Inject

/**
 * Whether the app process is currently in the foreground. Injected into
 * [WidgetRefreshRunner] so the background worker can skip the cache write while
 * the app's own feed `PagingSource` may be actively collecting (the foreground
 * guard, D-B4), and fakeable in tests.
 *
 * NOTE: this mirrors `:feature:chats:impl`'s (`internal`) `AppForegroundSignal`.
 * Extracting a single shared `AppForegroundSignal` into `:core:common` is a
 * future DRY follow-up; for now each background worker owns a local copy so the
 * modules stay decoupled.
 */
internal interface AppForegroundSignal {
    fun isForegrounded(): Boolean
}

internal class ProcessLifecycleForegroundSignal
    @Inject
    constructor() : AppForegroundSignal {
        override fun isForegrounded(): Boolean =
            ProcessLifecycleOwner
                .get()
                .lifecycle
                .currentState
                .isAtLeast(Lifecycle.State.STARTED)
    }
