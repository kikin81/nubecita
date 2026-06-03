package net.kikin.nubecita.feature.chats.impl.data

import net.kikin.nubecita.feature.chats.impl.AllowIncoming

/**
 * Reads and writes the viewer's account-global DM availability preference —
 * the `chat.bsky.actor.declaration/self` record's `allowIncoming` field.
 *
 * Server-side only: there is no local mirror. [getAllowIncoming] fetches the
 * record on demand (defaulting to [AllowIncoming.Following] when the account
 * has no declaration yet), and [setAllowIncoming] writes it back.
 */
internal interface ChatSettingsRepository {
    suspend fun getAllowIncoming(): Result<AllowIncoming>

    suspend fun setAllowIncoming(value: AllowIncoming): Result<Unit>
}
