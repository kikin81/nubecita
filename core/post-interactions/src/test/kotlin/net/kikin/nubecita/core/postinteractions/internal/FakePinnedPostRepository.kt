package net.kikin.nubecita.core.postinteractions.internal

import kotlinx.coroutines.CompletableDeferred
import net.kikin.nubecita.core.profile.PinnedPostRepository

/** Records calls; [gate], when set, holds every call open until completed. */
internal class FakePinnedPostRepository : PinnedPostRepository {
    val pinCalls = mutableListOf<Pair<String, String>>()
    val unpinCalls = mutableListOf<String>()
    var nextResult: Result<Unit> = Result.success(Unit)
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun pin(
        uri: String,
        cid: String,
    ): Result<Unit> {
        pinCalls += uri to cid
        gate?.await()
        return nextResult
    }

    override suspend fun unpin(uri: String): Result<Unit> {
        unpinCalls += uri
        gate?.await()
        return nextResult
    }
}
