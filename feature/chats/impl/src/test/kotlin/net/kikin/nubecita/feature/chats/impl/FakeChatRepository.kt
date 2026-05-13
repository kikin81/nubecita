package net.kikin.nubecita.feature.chats.impl

import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.ConvoListPage
import java.util.concurrent.atomic.AtomicInteger

internal class FakeChatRepository(
    var nextResult: Result<ConvoListPage> = Result.success(ConvoListPage(items = persistentListOf())),
) : ChatRepository {
    val listCalls = AtomicInteger(0)
    var lastCursor: String? = null

    override suspend fun listConvos(
        cursor: String?,
        limit: Int,
    ): Result<ConvoListPage> {
        listCalls.incrementAndGet()
        lastCursor = cursor
        return nextResult
    }
}
