package net.kikin.nubecita.wear.feed

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

/** A synced feed the user can switch to. [name] is null for Following (localized in the UI). */
@Immutable
data class WatchFeedTab(
    val key: String,
    val name: String?,
    val updatedAtMs: Long,
)

/**
 * One post as the watch shows it, with the user's own pending actions already
 * applied. Image fields are local file paths. `*Pending` is true while that
 * toggle's action has no answer from the phone yet.
 */
@Immutable
data class WatchPostUi(
    val uri: String,
    val cid: String = "",
    val authorName: String,
    val authorHandle: String,
    val avatarPath: String?,
    val text: String,
    val createdAtMs: Long,
    val replyCount: Int,
    val repostCount: Int,
    val likeCount: Int,
    val liked: Boolean,
    val bookmarked: Boolean,
    val reposted: Boolean = false,
    val likeUri: String? = null,
    val repostUri: String? = null,
    val likePending: Boolean = false,
    val repostPending: Boolean = false,
    val bookmarkPending: Boolean = false,
    val repostedBy: String?,
    val imageCount: Int,
    val isVideo: Boolean,
    val hasContentWarning: Boolean,
    val thumbnailPath: String?,
)

/** A post written on the watch that the phone hasn't posted yet (W6). */
@Immutable
data class WatchPendingPostUi(
    val actionId: String,
    val text: String,
    /** "Not posted — tap to retry" rather than "Posting…". */
    val failed: Boolean,
)

/** Exactly one applies. */
sealed interface WatchFeedStatus {
    /** The account isn't known yet or the store hasn't answered. */
    data object Loading : WatchFeedStatus

    /** Signed in, but the phone hasn't synced a feed yet. */
    data object NoFeeds : WatchFeedStatus

    data object Ready : WatchFeedStatus
}

data class WatchFeedState(
    val status: WatchFeedStatus = WatchFeedStatus.Loading,
    val feeds: ImmutableList<WatchFeedTab> = persistentListOf(),
    val selectedKey: String? = null,
    val posts: ImmutableList<WatchPostUi> = persistentListOf(),
    /** The paired phone runs watch actions, so the post screen offers them. */
    val actionsAvailable: Boolean = false,
    /** The paired phone posts from the watch, so the feed offers "New post". */
    val postingAvailable: Boolean = false,
    /** Posts written on the watch, oldest first, until the phone posts them. */
    val pendingPosts: ImmutableList<WatchPendingPostUi> = persistentListOf(),
) : UiState {
    val selectedFeed: WatchFeedTab? get() = feeds.firstOrNull { it.key == selectedKey }
}

sealed interface WatchFeedEvent : UiEvent {
    /** The signed-in account (from the gate). Null clears the feed. */
    data class AccountChanged(
        val accountDid: String?,
    ) : WatchFeedEvent

    data class FeedSelected(
        val feedKey: String,
    ) : WatchFeedEvent

    /** Like or unlike the post. Ignored while its like toggle is pending. */
    data class ToggleLike(
        val postUri: String,
    ) : WatchFeedEvent

    /** Repost or undo (the screen has already confirmed a repost). Ignored while pending. */
    data class ToggleRepost(
        val postUri: String,
    ) : WatchFeedEvent

    data class ToggleBookmark(
        val postUri: String,
    ) : WatchFeedEvent

    /** Post [text], already reviewed, through the phone. */
    data class PostSent(
        val text: String,
    ) : WatchFeedEvent

    /** Send a failed post again. */
    data class RetryPost(
        val actionId: String,
    ) : WatchFeedEvent

    /** Open the post in Nubecita on the phone (long threads, replying). */
    data class OpenOnPhone(
        val postUri: String,
    ) : WatchFeedEvent
}

/** No one-shot effects: navigation is driven by the screens. */
sealed interface WatchFeedEffect : UiEffect
