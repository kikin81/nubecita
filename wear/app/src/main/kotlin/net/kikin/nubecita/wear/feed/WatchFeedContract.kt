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

/** One post as the watch shows it. Image fields are local file paths. */
@Immutable
data class WatchPostUi(
    val uri: String,
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
    val repostedBy: String?,
    val imageCount: Int,
    val isVideo: Boolean,
    val hasContentWarning: Boolean,
    val thumbnailPath: String?,
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
}

/** No one-shot effects: navigation is driven by the screens. */
sealed interface WatchFeedEffect : UiEffect
