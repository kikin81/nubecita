package net.kikin.nubecita.core.actors.internal

import net.kikin.nubecita.core.actors.MuteRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench-flavor [MuteRepository]: the offline bench build issues no network,
 * so mute/unmute are success no-ops. The bench flavor has no mute management
 * screen, so a seed list is not needed.
 */
@Singleton
internal class BenchFakeMuteRepository
    @Inject
    constructor() : MuteRepository {
        override suspend fun muteActor(did: String): Result<Unit> = Result.success(Unit)

        override suspend fun unmuteActor(did: String): Result<Unit> = Result.success(Unit)
    }
