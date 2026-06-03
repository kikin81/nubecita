package net.kikin.nubecita.feature.chats.impl.data

import net.kikin.nubecita.feature.chats.impl.AllowIncoming
import javax.inject.Inject

/**
 * Bench-flavor fake: the fake-network build issues zero XRPC calls, so the
 * chat-settings screen reads a deterministic [AllowIncoming.Following] and
 * accepts writes as no-ops. Mirrors [BenchFakeChatRepository]'s role.
 */
internal class BenchFakeChatSettingsRepository
    @Inject
    constructor() : ChatSettingsRepository {
        override suspend fun getAllowIncoming(): Result<AllowIncoming> = Result.success(AllowIncoming.Following)

        override suspend fun setAllowIncoming(value: AllowIncoming): Result<Unit> = Result.success(Unit)
    }
