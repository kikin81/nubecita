package net.kikin.nubecita.wear.feed

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.wearcontract.WearAction
import net.kikin.nubecita.core.wearcontract.WearFeedKey
import net.kikin.nubecita.core.wearcontract.WearPostAction
import net.kikin.nubecita.wear.data.PhoneActionSupport
import net.kikin.nubecita.wear.data.PhoneConnection
import net.kikin.nubecita.wear.data.WatchActionRepository
import net.kikin.nubecita.wear.data.WatchFeedRepository
import net.kikin.nubecita.wear.data.db.WatchFeedEntity
import net.kikin.nubecita.wear.data.db.WatchPendingActionEntity
import net.kikin.nubecita.wear.data.db.WatchPostEntity
import net.kikin.nubecita.wear.data.effectiveViewer
import timber.log.Timber
import javax.inject.Inject

/**
 * The synced feeds and the selected feed's posts, straight from the offline
 * store, with the user's own likes, reposts and bookmarks overlaid.
 * Activity-scoped: the feed list and the post screen read the same state, so
 * opening a post needs no second query.
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
        private val actions: WatchActionRepository,
        private val actionSupport: PhoneActionSupport,
        private val phone: PhoneConnection,
    ) : MviViewModel<WatchFeedState, WatchFeedEvent, WatchFeedEffect>(WatchFeedState()) {
        private val account = MutableStateFlow<String?>(null)
        private val selected = MutableStateFlow<String?>(null)
        private val actionsAvailable = MutableStateFlow(false)

        init {
            // Nested, not combined: posts are only ever queried with a feed key
            // that came from the same account's feeds, so an account switch can
            // never pair the new account with the previous account's feed key.
            val feed =
                account.flatMapLatest { did ->
                    if (did == null) {
                        flowOf(WatchFeedState())
                    } else {
                        repository.feeds(did).map { it.toTabs() }.flatMapLatest { tabs ->
                            selected.flatMapLatest { chosen ->
                                val key = (tabs.firstOrNull { it.key == chosen } ?: tabs.firstOrNull())?.key
                                if (key == null) {
                                    flowOf(WatchFeedState(status = WatchFeedStatus.NoFeeds))
                                } else {
                                    combine(repository.posts(did, key), actions.pending(did)) { posts, pending ->
                                        val byPost = pending.groupBy { it.postUri }
                                        WatchFeedState(
                                            status = WatchFeedStatus.Ready,
                                            feeds = tabs.toImmutableList(),
                                            selectedKey = key,
                                            posts = posts.map { it.toUi(byPost[it.uri].orEmpty()) }.toImmutableList(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            combine(feed, actionsAvailable) { state, available -> state.copy(actionsAvailable = available) }
                .onEach { state -> setState { state } }
                .launchIn(viewModelScope)
        }

        override fun handleEvent(event: WatchFeedEvent) {
            when (event) {
                is WatchFeedEvent.AccountChanged -> {
                    if (event.accountDid != account.value) selected.value = null
                    account.value = event.accountDid
                    if (event.accountDid != null) checkActionSupport()
                }
                is WatchFeedEvent.FeedSelected -> selected.value = event.feedKey
                is WatchFeedEvent.ToggleLike ->
                    submit(event.postUri) { post ->
                        when {
                            post.likePending -> null
                            !post.liked -> WearAction.Like(post.uri, post.cid)
                            else -> post.likeUri?.let { WearAction.Unlike(post.uri, it) }
                        }
                    }
                is WatchFeedEvent.ToggleRepost ->
                    submit(event.postUri) { post ->
                        when {
                            post.repostPending -> null
                            !post.reposted -> WearAction.Repost(post.uri, post.cid)
                            else -> post.repostUri?.let { WearAction.Unrepost(post.uri, it) }
                        }
                    }
                is WatchFeedEvent.OpenOnPhone -> bskyPostUrl(event.postUri)?.let(phone::openUrlOnPhone)
                is WatchFeedEvent.ToggleBookmark ->
                    submit(event.postUri) { post ->
                        when {
                            post.bookmarkPending -> null
                            !post.bookmarked -> WearAction.Bookmark(post.uri, post.cid)
                            else -> WearAction.Unbookmark(post.uri, post.cid)
                        }
                    }
            }
        }

        // A pending toggle (or an undo without its record yet) builds no action,
        // so a fast double tap can't queue two.
        private fun submit(
            postUri: String,
            build: (WatchPostUi) -> WearPostAction?,
        ) {
            val did = account.value ?: return
            val action =
                uiState.value.posts
                    .firstOrNull { it.uri == postUri }
                    ?.let(build) ?: return
            viewModelScope.launch { actions.submit(did, action) }
        }

        private fun checkActionSupport() {
            viewModelScope.launch {
                actionsAvailable.value =
                    try {
                        actionSupport.available()
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        Timber.w(error, "couldn't check the phone's action support")
                        false
                    }
            }
        }
    }

private fun List<WatchFeedEntity>.toTabs(): List<WatchFeedTab> =
    sortedBy { if (it.feedKey == WearFeedKey.FOLLOWING) 0 else 1 }
        .map { WatchFeedTab(key = it.feedKey, name = it.feedName, updatedAtMs = it.publishedAtMs) }

internal fun WatchPostEntity.toUi(pending: List<WatchPendingActionEntity> = emptyList()): WatchPostUi {
    val viewer = effectiveViewer(this, pending)
    return WatchPostUi(
        uri = uri,
        cid = cid,
        authorName = authorDisplayName ?: authorHandle,
        authorHandle = authorHandle,
        avatarPath = authorAvatarPath,
        text = text,
        createdAtMs = createdAtMs,
        replyCount = replyCount,
        repostCount = viewer.repostCount,
        likeCount = viewer.likeCount,
        liked = viewer.liked,
        bookmarked = viewer.bookmarked,
        reposted = viewer.reposted,
        likeUri = viewer.likeUri,
        repostUri = viewer.repostUri,
        likePending = viewer.likePending,
        repostPending = viewer.repostPending,
        bookmarkPending = viewer.bookmarkPending,
        repostedBy = repostedBy,
        imageCount = imageCount,
        isVideo = isVideo,
        hasContentWarning = hasContentWarning,
        thumbnailPath = thumbnailPath,
    )
}
