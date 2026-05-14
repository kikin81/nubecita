package net.kikin.nubecita.feature.chats.impl

import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.ConvoListPage
import net.kikin.nubecita.feature.chats.impl.data.ConvoResolution
import net.kikin.nubecita.feature.chats.impl.data.MessagePage
import java.util.concurrent.atomic.AtomicInteger

/**
 * androidTest-classpath copy of the unit-test `FakeChatRepository`.
 *
 * The unit-test source set (`src/test/`) is not on the androidTest
 * compile classpath, so the fake is duplicated here. If the
 * `ChatRepository` interface grows new methods, both copies need to
 * be updated.
 */
internal class FakeChatRepository(
    var nextListResult: Result<ConvoListPage> = Result.success(ConvoListPage(items = persistentListOf())),
    var nextResolveResult: Result<ConvoResolution> =
        Result.success(
            ConvoResolution(
                convoId = "convo-1",
                otherUserHandle = "alice.bsky.social",
                otherUserDisplayName = "Alice",
                otherUserAvatarUrl = null,
                otherUserAvatarHue = 0,
            ),
        ),
    var nextMessagesResult: Result<MessagePage> = Result.success(MessagePage(messages = persistentListOf())),
) : ChatRepository {
    val listCalls = AtomicInteger(0)
    val resolveCalls = AtomicInteger(0)
    val messagesCalls = AtomicInteger(0)
    var lastListCursor: String? = null
    var lastResolvedDid: String? = null
    var lastMessagesConvoId: String? = null
    var lastMessagesCursor: String? = null

    override suspend fun listConvos(
        cursor: String?,
        limit: Int,
    ): Result<ConvoListPage> {
        listCalls.incrementAndGet()
        lastListCursor = cursor
        return nextListResult
    }

    override suspend fun resolveConvo(otherUserDid: String): Result<ConvoResolution> {
        resolveCalls.incrementAndGet()
        lastResolvedDid = otherUserDid
        return nextResolveResult
    }

    override suspend fun getMessages(
        convoId: String,
        cursor: String?,
        limit: Int,
    ): Result<MessagePage> {
        messagesCalls.incrementAndGet()
        lastMessagesConvoId = convoId
        lastMessagesCursor = cursor
        return nextMessagesResult
    }
}
