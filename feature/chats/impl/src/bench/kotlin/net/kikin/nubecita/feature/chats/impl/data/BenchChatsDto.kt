package net.kikin.nubecita.feature.chats.impl.data

import kotlinx.serialization.Serializable

@Serializable
internal data class BenchConvoListDto(
    val convos: List<BenchConvoDto> = emptyList(),
)

@Serializable
internal data class BenchConvoDto(
    val convoId: String,
    /** `"direct"` (default) or `"group"` — selects which [ConvoRowUi] variant the mapper builds. */
    val kind: String = "direct",
    // Direct-only fields (defaulted so a group entry can omit them).
    val otherUserDid: String = "",
    val otherUserHandle: String = "",
    val displayName: String? = null,
    val avatarUrl: String? = null,
    // Group-only fields.
    val name: String? = null,
    val members: List<BenchMemberDto> = emptyList(),
    val lastMessageSnippet: String? = null,
    val lastMessageFromViewer: Boolean = false,
    val lastMessageIsAttachment: Boolean = false,
    val sentAt: String? = null,
    val messages: List<BenchMessageDto> = emptyList(),
)

@Serializable
internal data class BenchMemberDto(
    val did: String,
    val handle: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
internal data class BenchMessageDto(
    val id: String,
    val senderDid: String,
    val text: String,
    val isDeleted: Boolean = false,
    val sentAt: String,
    val reactions: List<BenchReactionDto> = emptyList(),
    val replyTo: BenchRepliedDto? = null,
)

/** The message a [BenchMessageDto] replies to, inlined for the bench reply-preview fixture. */
@Serializable
internal data class BenchRepliedDto(
    val id: String,
    val senderDid: String,
    val text: String,
    val isDeleted: Boolean = false,
)

@Serializable
internal data class BenchReactionDto(
    val emoji: String,
    val count: Int = 1,
    val reactedByViewer: Boolean = false,
)
