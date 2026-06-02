package net.kikin.nubecita.feature.chats.impl.data

import kotlinx.serialization.Serializable

@Serializable
internal data class BenchConvoListDto(
    val convos: List<BenchConvoDto> = emptyList(),
)

@Serializable
internal data class BenchConvoDto(
    val convoId: String,
    val otherUserDid: String,
    val otherUserHandle: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val lastMessageSnippet: String? = null,
    val lastMessageFromViewer: Boolean = false,
    val lastMessageIsAttachment: Boolean = false,
    val sentAt: String? = null,
    val messages: List<BenchMessageDto> = emptyList(),
)

@Serializable
internal data class BenchMessageDto(
    val id: String,
    val senderDid: String,
    val text: String,
    val isDeleted: Boolean = false,
    val sentAt: String,
)
