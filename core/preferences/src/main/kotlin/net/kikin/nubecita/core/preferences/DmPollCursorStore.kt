package net.kikin.nubecita.core.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Per-account persistence for the background DM-poll `chat.bsky.convo.getLog`
 * cursor (the v2 background-notifications worker, nubecita-1fy.15).
 *
 * This is an internal sync bookmark, not a user-facing setting, so it lives in
 * its own accessor rather than on [UserPreferencesRepository]. It shares the
 * `:core:preferences` (non-encrypted) DataStore — the cursor is an opaque
 * server token, not sensitive. Keyed by the viewer DID so switching accounts
 * never resumes from another account's position.
 */
interface DmPollCursorStore {
    /**
     * The last persisted getLog cursor for [did], or `null` until the worker
     * has completed a poll for that account at least once (a `null`/first poll
     * establishes the baseline without notifying for the existing backlog).
     */
    fun cursor(did: String): Flow<String?>

    /** Persist the advanced getLog [cursor] for [did]. */
    suspend fun setCursor(
        did: String,
        cursor: String,
    )
}
