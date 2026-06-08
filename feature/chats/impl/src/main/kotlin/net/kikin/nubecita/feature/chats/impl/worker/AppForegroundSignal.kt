package net.kikin.nubecita.feature.chats.impl.worker

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import javax.inject.Inject

/**
 * Whether the app process is currently in the foreground. Injected into
 * [DmPollRunner] so the background worker can suppress notifications while the
 * in-app surface is already showing unread (design D5), and fakeable in tests.
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
