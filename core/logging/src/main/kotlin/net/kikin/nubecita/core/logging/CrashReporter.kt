package net.kikin.nubecita.core.logging

/**
 * Thin seam over the Crashlytics non-fatal API.
 *
 * Lives in `:core:logging`'s `src/main` — provider-agnostic and public — so any
 * module (features included) can depend on it without pulling the Firebase SDK,
 * and so [CrashlyticsTree] and feature call sites can be exercised in JVM unit
 * tests with a fake instead of the Firebase singleton. The production binding is
 * `FirebaseCrashReporter` (`src/production`); the `bench` flavor binds
 * [NoOpCrashReporter].
 */
interface CrashReporter {
    /** Append a breadcrumb to the rolling buffer (context for the next crash). */
    fun log(message: String)

    /** Record a non-fatal exception with its stack trace. */
    fun recordException(throwable: Throwable)

    /**
     * Attach a custom key/value to subsequent crash + non-fatal reports.
     *
     * Values MUST be PII-free: keys are diagnostic dimensions (login stage,
     * failure reason, exception class, redirect kind), never user content
     * (handles, tokens, URIs).
     */
    fun setCustomKey(
        key: String,
        value: String,
    )
}
