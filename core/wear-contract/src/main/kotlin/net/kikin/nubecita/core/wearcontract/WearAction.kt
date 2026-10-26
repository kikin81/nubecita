package net.kikin.nubecita.core.wearcontract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An action the watch queued, written at [WearPaths.action] as an urgent
 * Data Layer item. Items persist across disconnection, so an action taken
 * with the phone out of reach is delivered when it comes back.
 *
 * The phone runs it, answers with a [WearActionResult] at
 * [WearPaths.actionResult], and deletes the action item.
 */
@Serializable
data class WearActionRequest(
    override val schemaVersion: Int = WearSchema.CURRENT,
    /** Client-generated id; also the last segment of both item paths. */
    val actionId: String,
    /** The account the watch acted as. The phone refuses any other account. */
    val accountDid: String,
    val createdAtMs: Long,
    val action: WearAction,
) : WearPayload

/**
 * What the watch asked for. Encoded with a `type` discriminator, so an action
 * kind this phone build doesn't know fails to decode and is answered
 * [WearActionFailure.UNSUPPORTED] rather than guessed at.
 */
@Serializable
sealed interface WearAction {
    @Serializable
    @SerialName("like")
    data class Like(
        override val postUri: String,
        val postCid: String,
    ) : WearPostAction

    @Serializable
    @SerialName("unlike")
    data class Unlike(
        override val postUri: String,
        /** The viewer's like record, from the synced post. */
        val likeUri: String,
    ) : WearPostAction

    @Serializable
    @SerialName("repost")
    data class Repost(
        override val postUri: String,
        val postCid: String,
    ) : WearPostAction

    @Serializable
    @SerialName("unrepost")
    data class Unrepost(
        override val postUri: String,
        /** The viewer's repost record, from the synced post. */
        val repostUri: String,
    ) : WearPostAction

    @Serializable
    @SerialName("bookmark")
    data class Bookmark(
        override val postUri: String,
        val postCid: String,
    ) : WearPostAction

    @Serializable
    @SerialName("unbookmark")
    data class Unbookmark(
        override val postUri: String,
        val postCid: String,
    ) : WearPostAction
}

/**
 * An action on one post: like, repost or bookmark, or an undo. Its own sealed
 * family so the phone's post handler claims exactly these, and not the DM
 * actions that join [WearAction] later.
 */
@Serializable
sealed interface WearPostAction : WearAction {
    /** The post the action is about. */
    val postUri: String
}

/** The phone's answer to a [WearActionRequest], written at [WearPaths.actionResult]. */
@Serializable
data class WearActionResult(
    override val schemaVersion: Int = WearSchema.CURRENT,
    val actionId: String,
    val accountDid: String,
    val completedAtMs: Long,
    /** Null when the action succeeded. */
    val failure: WearActionFailure? = null,
    /**
     * The record a like or repost created, so the watch can undo it before the
     * next snapshot carries it. Null for every other action.
     */
    val recordUri: String? = null,
) : WearPayload {
    val isOk: Boolean get() = failure == null
}

/** Why the phone didn't complete an action. The watch reverts silently either way. */
@Serializable
enum class WearActionFailure {
    /** The action was for an account that isn't signed in on the phone. */
    @SerialName("account")
    ACCOUNT,

    /** This phone build doesn't know the action, or it came from a newer contract. */
    @SerialName("unsupported")
    UNSUPPORTED,

    /** The phone tried and Bluesky (or the network) refused. */
    @SerialName("error")
    ERROR,
}
