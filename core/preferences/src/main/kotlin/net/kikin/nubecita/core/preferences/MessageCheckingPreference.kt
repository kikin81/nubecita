package net.kikin.nubecita.core.preferences

import kotlinx.coroutines.flow.Flow

/**
 * The single user-facing "check for new messages" toggle (design D6), default
 * **on**. When off it disables BOTH message pollers — v1's foreground unread
 * poll AND v2's background DM-notification worker — so a user who doesn't DM
 * pays zero polling/battery cost (no notifications and no unread badge).
 *
 * A genuine cross-cutting user setting (read by both pollers, written by the
 * Settings screen), but kept on its own accessor rather than
 * [UserPreferencesRepository] so unrelated fakes don't have to grow it.
 */
interface MessageCheckingPreference {
    /** Whether message checking is enabled. Emits `true` on a fresh install. */
    val enabled: Flow<Boolean>

    /** Persist the toggle. */
    suspend fun setEnabled(enabled: Boolean)
}
