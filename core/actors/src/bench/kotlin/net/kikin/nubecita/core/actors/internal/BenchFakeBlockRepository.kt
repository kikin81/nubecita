package net.kikin.nubecita.core.actors.internal

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.kikin.nubecita.core.actors.BlockRepository
import net.kikin.nubecita.data.models.BlockedAccount
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench-flavor [BlockRepository]: the offline bench build issues no network, so
 * this keeps an in-memory blocked-accounts list seeded with a couple of fakes —
 * enough to exercise the blocked-accounts screen + unblock end-to-end without a
 * PDS. `blockActor` is a success no-op (the feed/chat block surfaces don't add
 * to this list in bench; the screen is driven by the seed).
 */
@Singleton
internal class BenchFakeBlockRepository
    @Inject
    constructor() : BlockRepository {
        private val mutex = Mutex()
        private val blocked = SEED.toMutableList()

        override suspend fun blockActor(did: String): Result<Unit> = Result.success(Unit)

        override suspend fun blockedAccounts(): Result<List<BlockedAccount>> = mutex.withLock { Result.success(blocked.toList()) }

        override suspend fun unblockActor(blockUri: String): Result<Unit> =
            mutex.withLock {
                blocked.removeAll { it.blockUri == blockUri }
                Result.success(Unit)
            }

        private companion object {
            private fun seedAccount(
                did: String,
                handle: String,
                displayName: String?,
            ): BlockedAccount =
                BlockedAccount(
                    did = did,
                    handle = handle,
                    displayName = displayName,
                    avatarUrl = null,
                    blockUri = "at://$did/app.bsky.graph.block/$handle",
                )

            val SEED =
                listOf(
                    seedAccount("did:plc:blockedone", "spammer.bsky.social", "Spam Account"),
                    seedAccount("did:plc:blockedtwo", "troll.bsky.social", null),
                )
        }
    }
