package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.convo.GetMessagesResponseMessagesUnion
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.feature.chats.impl.MessageUi

// TEMPORARY stub — Task 6 replaces with full TDD'd implementation.
internal fun List<GetMessagesResponseMessagesUnion>.toMessageUis(viewerDid: String): ImmutableList<MessageUi> = persistentListOf()
