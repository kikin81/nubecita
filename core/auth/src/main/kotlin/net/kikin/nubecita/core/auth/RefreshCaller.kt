package net.kikin.nubecita.core.auth

import kotlin.coroutines.CoroutineContext

/**
 * Coroutine-context tag naming the background entry point a request runs
 * under, so a token refresh it triggers can be attributed to it in telemetry
 * (nubecita-z2gt).
 *
 * The SDK refreshes inside whichever coroutine needed a token, so the tag set
 * by the entry point is still on the context when the refresh POST is sent:
 *
 * ```kotlin
 * override suspend fun doWork(): Result = withContext(RefreshCaller.DmPoll) { … }
 * ```
 *
 * Purely observational — it changes no auth behavior. An untagged refresh is
 * reported as `untagged`, which in practice means the app's own foreground
 * surfaces.
 */
enum class RefreshCaller : CoroutineContext.Element {
    DmPoll,
    Widget,
    PushRegistration,
    ;

    override val key: CoroutineContext.Key<*> get() = Key

    companion object Key : CoroutineContext.Key<RefreshCaller>
}
