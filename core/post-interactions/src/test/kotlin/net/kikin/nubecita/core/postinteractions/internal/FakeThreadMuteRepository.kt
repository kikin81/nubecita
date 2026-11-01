package net.kikin.nubecita.core.postinteractions.internal

import kotlinx.coroutines.CompletableDeferred
import net.kikin.nubecita.core.postinteractions.ThreadMuteRepository

/** Records calls; [gate], when set, holds every call open until completed. */
internal class FakeThreadMuteRepository : ThreadMuteRepository {
    val muteCalls = mutableListOf<String>()
    val unmuteCalls = mutableListOf<String>()
    var nextResult: Result<Unit> = Result.success(Unit)
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun mute(rootUri: String): Result<Unit> {
        muteCalls += rootUri
        gate?.await()
        return nextResult
    }

    override suspend fun unmute(rootUri: String): Result<Unit> {
        unmuteCalls += rootUri
        gate?.await()
        return nextResult
    }
}
