package net.kikin.nubecita.core.actors

/**
 * Creates moderation block relationships against an actor.
 *
 * A deliberately small, reusable seam (separate from [ActorRepository] so adding
 * it doesn't churn that interface's implementors): blocking is a graph mutation
 * — an `app.bsky.graph.block` record keyed by the target's DID — shared by any
 * surface that needs it (the chat list's contextual "Block account", and the
 * profile / moderation surfaces when they wire block management).
 *
 * `blockActor` is one-way by design: it creates the block record. Removing a
 * block needs the block record's AT URI, which surfaces (e.g. on a profile view
 * as `viewer.blocking`) are better positioned to supply; unblock lands with the
 * moderation epic that owns block management. Network methods return [Result];
 * `CancellationException` always propagates.
 */
interface BlockRepository {
    /**
     * Blocks the account identified by [did] by creating an
     * `app.bsky.graph.block` record. Idempotent enough for UI purposes: the PDS
     * accepts a fresh record even if one already exists (a second record is
     * created); callers gate on viewer state to avoid redundant calls where they
     * can.
     */
    suspend fun blockActor(did: String): Result<Unit>
}
