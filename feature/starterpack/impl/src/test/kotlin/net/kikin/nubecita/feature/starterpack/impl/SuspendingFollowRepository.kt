package net.kikin.nubecita.feature.starterpack.impl

import kotlinx.coroutines.CompletableDeferred
import net.kikin.nubecita.core.postinteractions.FollowRepository

/** A [FollowRepository] whose follow call waits on [gate], so a test can look at the in-between state. */
internal class SuspendingFollowRepository(
    private val gate: CompletableDeferred<Result<String>>,
) : FollowRepository {
    override suspend fun follow(subjectDid: String): Result<String> = gate.await()

    override suspend fun unfollow(followUri: String): Result<Unit> = Result.success(Unit)
}
