package net.kikin.nubecita.bootstrap

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import javax.inject.Qualifier

/**
 * Runs [delegate] the first time the process reaches the foreground, instead
 * of during `Application.onCreate`, so a process the OS starts in the
 * background never runs it.
 *
 * For startup work that calls an authenticated endpoint. A push, a package
 * update or boot starts the process in the background, and none of that work
 * needs a token. Starting authenticated work there anyway refreshes an expired
 * access token in a process that aggressive OEMs (MIUI) freeze or kill moments
 * later. If the server rotated the token before the response was saved, the
 * next process replays the old token and the server deletes the whole
 * session: a silent logout (nubecita-25a5).
 *
 * [start] must be called on the main thread, as `NubecitaApplication` does:
 * registering a lifecycle observer is main-thread-only. If the process is
 * already in the foreground, [delegate] runs immediately.
 */
internal class StartOnFirstForeground(
    private val lifecycle: Lifecycle,
    private val delegate: AppInitializer,
) : AppInitializer {
    override fun start() {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            delegate.start()
            return
        }
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    lifecycle.removeObserver(this)
                    delegate.start()
                }
            },
        )
    }
}

/** The process-wide lifecycle (`ProcessLifecycleOwner`), as opposed to an activity's. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class ProcessLifecycle
