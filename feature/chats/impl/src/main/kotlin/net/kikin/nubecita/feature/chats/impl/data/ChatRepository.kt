package net.kikin.nubecita.feature.chats.impl.data

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.feature.chats.impl.ConvoListItemUi

/**
 * `chat.bsky.convo.*` fetch surface scoped to `:feature:chats:impl`.
 *
 * `nn3.2` extends this interface with `resolveConvo` + `getMessages`
 * additively. No breaking changes between PRs.
 */
internal interface ChatRepository {
    suspend fun listConvos(
        cursor: String? = null,
        limit: Int = LIST_CONVOS_PAGE_LIMIT,
    ): Result<ConvoListPage>
}

internal data class ConvoListPage(
    val items: ImmutableList<ConvoListItemUi> = persistentListOf(),
    val nextCursor: String? = null,
)

internal const val LIST_CONVOS_PAGE_LIMIT: Int = 30
