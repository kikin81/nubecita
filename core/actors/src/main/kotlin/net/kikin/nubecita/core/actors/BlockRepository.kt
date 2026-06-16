package net.kikin.nubecita.core.actors

import net.kikin.nubecita.data.models.BlockedAccount

/**
 * Moderation block relationships against actors: create a block, list blocked
 * accounts, and remove a block.
 *
 * A deliberately small, reusable seam (separate from [ActorRepository] so adding
 * it doesn't churn that interface's implementors): blocking is a graph mutation
 * — an `app.bsky.graph.block` record keyed by the target's DID — shared by any
 * surface that needs it (the chat list's contextual "Block account", the feed
 * overflow, and the blocked-accounts management screen).
 *
 * Network methods return [Result]; `CancellationException` always propagates.
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

    /**
     * Lists the viewer's blocked accounts (`app.bsky.graph.getBlocks`), newest
     * first. Each [BlockedAccount] carries its block-record AT URI (for
     * [unblockActor]); profiles missing one are omitted since they can't be
     * unblocked from here.
     */
    suspend fun blockedAccounts(): Result<List<BlockedAccount>>

    /**
     * Removes a block by deleting its `app.bsky.graph.block` record, identified
     * by the block record's AT URI ([BlockedAccount.blockUri] / a profile's
     * `viewer.blocking`).
     */
    suspend fun unblockActor(blockUri: String): Result<Unit>
}
