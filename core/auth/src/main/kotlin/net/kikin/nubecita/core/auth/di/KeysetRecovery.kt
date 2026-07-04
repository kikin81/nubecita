package net.kikin.nubecita.core.auth.di

import java.security.GeneralSecurityException

/**
 * The bounded regenerate-on-crypto-failure policy for the Tink session keyset,
 * extracted from [AuthDataStoreModule] so the recovery semantics are JVM-unit-
 * testable (the real `AndroidKeysetManager` build needs an Android runtime).
 */
internal object KeysetRecovery {
    /**
     * Runs [build]; on a [GeneralSecurityException] reports via [onRegenerated],
     * destroys the broken keyset via [reset], and retries [build] exactly once.
     * A second crypto failure — and any non-crypto failure — propagates: the
     * Keystore environment is unrecoverable in a way we cannot paper over, and
     * failing loudly beats silently losing future writes.
     */
    fun <T> buildWithRecovery(
        build: () -> T,
        reset: () -> Unit,
        onRegenerated: (GeneralSecurityException) -> Unit,
    ): T =
        try {
            build()
        } catch (e: GeneralSecurityException) {
            onRegenerated(e)
            reset()
            build()
        }
}
