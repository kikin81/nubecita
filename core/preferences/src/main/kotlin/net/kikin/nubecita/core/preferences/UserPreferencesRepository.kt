package net.kikin.nubecita.core.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Reactive view over non-sensitive app-wide user preferences (onboarding
 * status, future settings toggles like adult-content / theme / etc.).
 *
 * Sensitive material — OAuth tokens, DPoP keys — does NOT live here; that
 * stays in `:core:auth`'s encrypted DataStore. This repository is the
 * natural home for global flags that survive sign-out and are safe to
 * read in plaintext on disk.
 */
interface UserPreferencesRepository {
    /**
     * `true` once the user has either completed or skipped the onboarding
     * flow. New installs start at `false`; once flipped to `true` the flag
     * persists across sign-out so existing users don't re-see onboarding
     * after signing out.
     */
    val hasSeenOnboarding: Flow<Boolean>

    /** Persist that onboarding was completed or skipped. Idempotent. */
    suspend fun markOnboardingSeen()
}
