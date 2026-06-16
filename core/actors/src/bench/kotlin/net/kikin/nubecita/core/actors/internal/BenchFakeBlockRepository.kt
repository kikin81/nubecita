package net.kikin.nubecita.core.actors.internal

import net.kikin.nubecita.core.actors.BlockRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench-flavor [BlockRepository]: the offline bench build issues no network, so
 * blocking is a no-op that reports success. Keeps the bench graph resolvable
 * (the chat contextual menu's Block action constructs cleanly) without a PDS.
 */
@Singleton
internal class BenchFakeBlockRepository
    @Inject
    constructor() : BlockRepository {
        override suspend fun blockActor(did: String): Result<Unit> = Result.success(Unit)
    }
