package net.kikin.nubecita.core.actors

/**
 * Mute relationships against actors: mute an account and unmute it.
 *
 * A deliberately small, reusable seam (separate from [ActorRepository] so
 * adding it doesn't churn that interface's implementors): muting is a
 * server-side XRPC call (`app.bsky.graph.muteActor` /
 * `app.bsky.graph.unmuteActor`) with no record creation — simpler than
 * blocking. Mutes are private in Bluesky.
 *
 * Network methods return [Result]; `CancellationException` always propagates.
 */
interface MuteRepository {
    /**
     * Mutes the account identified by [did] via `app.bsky.graph.muteActor`.
     * Mutes are private and do not create a graph record.
     */
    suspend fun muteActor(did: String): Result<Unit>

    /**
     * Unmutes the account identified by [did] via
     * `app.bsky.graph.unmuteActor`.
     */
    suspend fun unmuteActor(did: String): Result<Unit>
}
