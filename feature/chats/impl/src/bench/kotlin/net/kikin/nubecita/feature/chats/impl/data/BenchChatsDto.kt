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
    /**
     * Mirrors `chat.bsky.convo.defs#convoStatus`: `"accepted"` (default) or
     * `"request"`. Defaulted so every pre-existing fixture entry keeps landing
     * in the Chats segment without being touched.
     */
    val status: String = STATUS_ACCEPTED,
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
) {
    /**
     * Whether this fixture is a pending message request. Anything that is not
     * exactly `"request"` reads as accepted, matching how production treats the
     * open-string `convoStatus` — an unrecognised value must not strand a
     * conversation behind an accept surface.
     */
    val isRequest: Boolean get() = status == STATUS_REQUEST
}

internal const val STATUS_ACCEPTED = "accepted"
internal const val STATUS_REQUEST = "request"

/**
 * The convo ids the fake routes to its Requests segment. Everything not named
 * here is accepted, so a fixture with a mistyped status fails toward the Chats
 * segment rather than stranding a conversation behind an accept surface.
 */
internal fun List<BenchConvoDto>.requestConvoIds(): Set<String> = filter { it.isRequest }.mapTo(mutableSetOf()) { it.convoId }

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
