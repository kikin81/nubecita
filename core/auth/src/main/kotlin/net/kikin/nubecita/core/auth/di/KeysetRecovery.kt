package net.kikin.nubecita.core.auth.di

import java.security.GeneralSecurityException

/**
 * The bounded regenerate-on-crypto-failure policy for the Tink session keyset,
 * extracted from [AuthDataStoreModule] so the recovery semantics are JVM-unit-
 * testable (the real `AndroidKeysetManager` build needs an Android runtime).
 */
internal object KeysetRecovery {
    /**
     * How long to wait before the non-destructive retry. A transiently
     * unavailable Keystore (just after boot / device unlock) presents as the
     * same [GeneralSecurityException] a corrupted keyset does; a short pause
     * lets it settle. Kept small: this blocks the first injection of the
     * session DataStore, and only ever runs on the (rare) failure path.
     */
    private const val TRANSIENT_RETRY_DELAY_MILLIS = 200L

    /**
     * Runs [build]; on a [GeneralSecurityException] retries once after a short
     * [sleep] WITHOUT destroying anything — a transient Keystore hiccup must
     * not cost the user their session. Only when the retry also fails is the
     * failure treated as a genuinely corrupted keyset: report via
     * [onRegenerated], destroy via [reset], and rebuild exactly once. A
     * failure after the rebuild — and any non-crypto failure — propagates:
     * the Keystore environment is unrecoverable in a way we cannot paper
     * over, and failing loudly beats silently losing future writes.
     */
    fun <T> buildWithRecovery(
        build: () -> T,
        reset: () -> Unit,
        onRegenerated: (GeneralSecurityException) -> Unit,
        sleep: (Long) -> Unit = ::interruptPreservingSleep,
    ): T {
        val firstFailure =
            try {
                return build()
            } catch (e: GeneralSecurityException) {
                e
            }

        sleep(TRANSIENT_RETRY_DELAY_MILLIS)
        val retryFailure =
            try {
                return build()
            } catch (e: GeneralSecurityException) {
                // Identity guard: a security provider can rethrow a cached
                // instance, and addSuppressed(itself) throws
                // IllegalArgumentException ("Self-suppression not permitted").
                if (e !== firstFailure) e.addSuppressed(firstFailure)
                e
            }

        onRegenerated(retryFailure)
        reset()
        return build()
    }

    /**
     * [Thread.sleep] that preserves the interrupt flag instead of letting
     * [InterruptedException] (which clears it) escape a DI provider: the
     * remaining delay is skipped and the retry proceeds immediately, while
     * callers relying on interruption for cancellation still observe it.
     */
    internal fun interruptPreservingSleep(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
