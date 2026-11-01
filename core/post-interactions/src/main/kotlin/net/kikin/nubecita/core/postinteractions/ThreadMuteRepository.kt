package net.kikin.nubecita.core.postinteractions

/**
 * Mutes / unmutes a thread for the signed-in viewer via
 * `app.bsky.graph.muteThread` / `unmuteThread`. Server-side only — no record
 * is created. A thread is identified by its **root** post's AT URI: muting
 * from a reply must pass the reply's `reply.root.uri`, never its own URI.
 */
interface ThreadMuteRepository {
    suspend fun mute(rootUri: String): Result<Unit>

    suspend fun unmute(rootUri: String): Result<Unit>
}
