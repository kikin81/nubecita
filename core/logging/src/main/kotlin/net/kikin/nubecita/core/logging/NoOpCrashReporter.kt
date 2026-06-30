package net.kikin.nubecita.core.logging

import javax.inject.Inject

/**
 * Inert [CrashReporter] that drops every call.
 *
 * Lives in `src/main` (not `src/bench`) so unit tests and downstream feature
 * instrumentation tests can reuse it via `@TestInstallIn`. The `bench` flavor's
 * `LoggingModule` binds it so screenshot / baseline-profile / Macrobenchmark
 * runs emit zero crash reports and never link Firebase.
 */
class NoOpCrashReporter
    @Inject
    constructor() : CrashReporter {
        override fun log(message: String) = Unit

        override fun recordException(throwable: Throwable) = Unit

        override fun setCustomKey(
            key: String,
            value: String,
        ) = Unit
    }
