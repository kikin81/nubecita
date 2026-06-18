package net.kikin.nubecita.logging

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

/**
 * A [Timber.Tree] that forwards error-priority logs to Crashlytics as
 * **non-fatals**, so `Timber.e(...)` / `Timber.wtf(...)` from anywhere in the
 * app become recorded exceptions with stack traces.
 *
 * `priority >= Log.WARN` is forwarded (see [isLoggable]); `v`/`d`/`i` are dropped.
 * The logging convention sets what each level does:
 *
 * - `Timber.e` / `Timber.wtf` → "unexpected, I want a stack trace" → breadcrumb
 *   **and** a non-fatal (`recordException`).
 * - `Timber.w` → "expected/benign" (offline, timeout, 404, user-cancelled) →
 *   breadcrumb only (`Crashlytics.log`), NOT a non-fatal. Breadcrumbs are a
 *   bounded rolling buffer, so they add context to whatever non-fatal/fatal
 *   fires next without flooding the issue stream (Crashlytics caps recorded
 *   exceptions per session — noise there crowds out signal).
 *
 * Planted only in `productionRelease` (see `NubecitaApplication`): debug builds
 * keep the logcat [Timber.DebugTree], and the bench flavor plants no tree at all
 * so Macrobench windows stay silent.
 *
 * The Crashlytics calls go through [CrashReporter] so the priority filter and
 * synthetic-exception path are unit-testable without the Firebase SDK.
 */
internal class CrashlyticsTree(
    private val reporter: CrashReporter = FirebaseCrashReporter(),
) : Timber.Tree() {
    public override fun isLoggable(
        tag: String?,
        priority: Int,
    ): Boolean = priority >= Log.WARN

    public override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        // Every forwarded log (WARN and ERROR) becomes a breadcrumb so the
        // run-up to a non-fatal/fatal has context.
        reporter.log(if (tag != null) "[$tag] $message" else message)
        // Only ERROR+ becomes a non-fatal. A message-only `Timber.e("…")` has no
        // throwable, so synthesize one — it still surfaces and clusters by its
        // call site rather than being silently dropped.
        if (priority >= Log.ERROR) {
            reporter.recordException(t ?: LoggedError(message))
        }
    }

    /** Synthetic throwable for message-only error logs (no real cause). */
    private class LoggedError(
        message: String,
    ) : Exception(message) {
        init {
            // This exception is constructed inside the tree, so its top stack
            // frames are always Timber + CrashlyticsTree. Crashlytics groups
            // non-fatals by the top frames, so without stripping them every
            // message-only Timber.e(...) in the app would cluster into ONE issue.
            // Drop the framework frames so grouping falls on the real call site.
            val trace = stackTrace
            val firstCaller =
                trace.indexOfFirst { frame ->
                    val cn = frame.className
                    !cn.startsWith("timber.") &&
                        cn != TREE_CLASS &&
                        !cn.startsWith("$TREE_CLASS\$")
                }
            if (firstCaller > 0) {
                stackTrace = trace.copyOfRange(firstCaller, trace.size)
            }
        }

        private companion object {
            const val TREE_CLASS = "net.kikin.nubecita.logging.CrashlyticsTree"
        }
    }
}

/**
 * Thin seam over the Crashlytics non-fatal API. Exists so [CrashlyticsTree] can
 * be exercised in JVM unit tests with a fake instead of the Firebase singleton.
 */
internal interface CrashReporter {
    fun log(message: String)

    fun recordException(throwable: Throwable)
}

/** Production [CrashReporter] backed by the Firebase Crashlytics singleton. */
internal class FirebaseCrashReporter : CrashReporter {
    private val crashlytics by lazy { FirebaseCrashlytics.getInstance() }

    override fun log(message: String) {
        crashlytics.log(message)
    }

    override fun recordException(throwable: Throwable) {
        crashlytics.recordException(throwable)
    }
}
