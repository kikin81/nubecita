package net.kikin.nubecita.feature.postdetail.impl

import net.kikin.nubecita.core.actors.MuteRepository

/**
 * Test double for [MuteRepository]. Records calls and returns
 * configurable results.
 */
internal class FakeMuteRepository : MuteRepository {
    val muteActorCalls = mutableListOf<String>()
    val unmuteActorCalls = mutableListOf<String>()

    var nextMuteResult: Result<Unit> = Result.success(Unit)
    var nextUnmuteResult: Result<Unit> = Result.success(Unit)

    override suspend fun muteActor(did: String): Result<Unit> {
        muteActorCalls += did
        return nextMuteResult
    }

    override suspend fun unmuteActor(did: String): Result<Unit> {
        unmuteActorCalls += did
        return nextUnmuteResult
    }
}
