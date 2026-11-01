package net.kikin.nubecita.core.postinteractions.internal

import kotlinx.coroutines.delay
import net.kikin.nubecita.core.postinteractions.ThreadMuteRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Offline thread mute for the bench flavor: always succeeds after a short, visible delay. */
@Singleton
internal class BenchFakeThreadMuteRepository
    @Inject
    constructor() : ThreadMuteRepository {
        override suspend fun mute(rootUri: String): Result<Unit> {
            delay(FAKE_LATENCY_MS)
            return Result.success(Unit)
        }

        override suspend fun unmute(rootUri: String): Result<Unit> {
            delay(FAKE_LATENCY_MS)
            return Result.success(Unit)
        }

        private companion object {
            const val FAKE_LATENCY_MS = 150L
        }
    }
