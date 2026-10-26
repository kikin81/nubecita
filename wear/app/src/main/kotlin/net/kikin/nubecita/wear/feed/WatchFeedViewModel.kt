package net.kikin.nubecita.wear.feed

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.wearcontract.WearFeedKey
import net.kikin.nubecita.wear.data.WatchFeedRepository
import net.kikin.nubecita.wear.data.db.WatchFeedEntity
import net.kikin.nubecita.wear.data.db.WatchPostEntity
import javax.inject.Inject

/**
 * The synced feeds and the selected feed's posts, straight from the offline
 * store. Activity-scoped: the feed list and the post screen read the same
 * state, so opening a post needs no second query.
 *
 * Following comes first; otherwise feeds keep the store's order. The selected
 * feed falls back to the first one when the selection disappears (the user
 * deselected it on the phone).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WatchFeedViewModel
    @Inject
    constructor(
        private val repository: WatchFeedRepository,
    ) : MviViewModel<WatchFeedState, WatchFeedEvent, WatchFeedEffect>(WatchFeedState()) {
        private val account = MutableStateFlow<String?>(null)
        private val selected = MutableStateFlow<String?>(null)

        init {
            val feeds =
                account.flatMapLatest { did ->
                    if (did == null) flowOf(null) else repository.feeds(did).map { list -> list.toTabs() }
                }
            val selection =
                combine(feeds, selected) { tabs, chosen ->
                    tabs to (tabs?.firstOrNull { it.key == chosen } ?: tabs?.firstOrNull())?.key
                }
            combine(account, selection) { did, (tabs, key) -> Triple(did, tabs, key) }
                .flatMapLatest { (did, tabs, key) ->
                    when {
                        did == null || tabs == null -> flowOf(WatchFeedState())
                        key == null -> flowOf(WatchFeedState(status = WatchFeedStatus.NoFeeds))
                        else ->
                            repository.posts(did, key).map { posts ->
                                WatchFeedState(
                                    status = WatchFeedStatus.Ready,
                                    feeds = tabs.toImmutableList(),
                                    selectedKey = key,
                                    posts = posts.map { it.toUi() }.toImmutableList(),
                                )
                            }
                    }
                }.onEach { state -> setState { state } }
                .launchIn(viewModelScope)
        }

        override fun handleEvent(event: WatchFeedEvent) {
            when (event) {
                is WatchFeedEvent.AccountChanged -> {
                    if (event.accountDid != account.value) selected.value = null
                    account.value = event.accountDid
                }
                is WatchFeedEvent.FeedSelected -> selected.value = event.feedKey
            }
        }
    }

private fun List<WatchFeedEntity>.toTabs(): List<WatchFeedTab> =
    sortedBy { if (it.feedKey == WearFeedKey.FOLLOWING) 0 else 1 }
        .map { WatchFeedTab(key = it.feedKey, name = it.feedName, updatedAtMs = it.publishedAtMs) }

internal fun WatchPostEntity.toUi() =
    WatchPostUi(
        uri = uri,
        authorName = authorDisplayName ?: authorHandle,
        authorHandle = authorHandle,
        avatarPath = authorAvatarPath,
        text = text,
        createdAtMs = createdAtMs,
        replyCount = replyCount,
        repostCount = repostCount,
        likeCount = likeCount,
        liked = likeUri != null,
        bookmarked = bookmarked,
        repostedBy = repostedBy,
        imageCount = imageCount,
        isVideo = isVideo,
        hasContentWarning = hasContentWarning,
        thumbnailPath = thumbnailPath,
    )
