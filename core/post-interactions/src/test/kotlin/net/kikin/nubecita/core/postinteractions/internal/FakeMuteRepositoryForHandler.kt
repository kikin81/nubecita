package net.kikin.nubecita.core.postinteractions.internal

import net.kikin.nubecita.core.actors.MuteRepository

/**
 * Test double for [MuteRepository] used by
 * [DefaultPostInteractionHandlerTest].
 */
internal class FakeMuteRepositoryForHandler : MuteRepository {
    val muteActorCalls: MutableList<String> = mutableListOf()
    val unmuteActorCalls: MutableList<String> = mutableListOf()

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
