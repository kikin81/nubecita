package net.kikin.nubecita.feature.feed.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Cid
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.feature.feed.impl.data.FeedRepository
import net.kikin.nubecita.feature.feed.impl.data.LikeRepostRepository
import net.kikin.nubecita.feature.feed.impl.data.TimelinePage
import net.kikin.nubecita.feature.feed.impl.data.dedupeClusterContext
import net.kikin.nubecita.feature.feed.impl.share.toShareIntent
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
internal class FeedViewModel
    @Inject
    constructor(
        private val feedRepository: FeedRepository,
        private val likeRepostRepository: LikeRepostRepository,
    ) : MviViewModel<FeedState, FeedEvent, FeedEffect>(FeedState()) {
        override fun handleEvent(event: FeedEvent) {
            when (event) {
                FeedEvent.Load -> load()
                FeedEvent.Refresh -> refresh()
                FeedEvent.LoadMore -> loadMore()
                FeedEvent.Retry -> load()
                FeedEvent.ClearError -> Unit
                is FeedEvent.OnPostTapped -> sendEffect(FeedEffect.NavigateToPost(event.post))
                is FeedEvent.OnAuthorTapped -> sendEffect(FeedEffect.NavigateToAuthor(event.authorDid))
                is FeedEvent.OnLikeClicked -> toggleLike(event.post)
                is FeedEvent.OnRepostClicked -> toggleRepost(event.post)
                is FeedEvent.OnReplyClicked -> Unit
                is FeedEvent.OnShareClicked -> sendEffect(FeedEffect.SharePost(event.post.toShareIntent()))
                is FeedEvent.OnShareLongPressed ->
                    sendEffect(FeedEffect.CopyPermalink(event.post.toShareIntent().permalink))
            }
        }

        private fun load() {
            // Allow initial load (from Idle, the default) and Retry from
            // InitialError. Any other status (InitialLoading, Refreshing,
            // Appending) means a fetch is already in flight — second Load
            // is a no-op so the UI can dispatch on every recomposition
            // without N concurrent fetches racing on setState.
            val status = uiState.value.loadStatus
            if (status != FeedLoadStatus.Idle && status !is FeedLoadStatus.InitialError) return
            setState { copy(loadStatus = FeedLoadStatus.InitialLoading) }
            viewModelScope.launch {
                feedRepository
                    .getTimeline(cursor = null)
                    .onSuccess { page -> applyInitialPage(page) }
                    .onFailure { throwable ->
                        setState {
                            copy(loadStatus = FeedLoadStatus.InitialError(throwable.toFeedError()))
                        }
                    }
            }
        }

        private fun refresh() {
            // Refresh and append/initial-load are mutually exclusive per the
            // mvi-foundation spec: dispatching Refresh while another load is
            // in flight is a no-op (drop the event rather than queue, so the
            // back-pressure on a flapping pull-to-refresh gesture stays at the
            // user's wrist).
            if (uiState.value.loadStatus != FeedLoadStatus.Idle) return
            setState { copy(loadStatus = FeedLoadStatus.Refreshing) }
            viewModelScope.launch {
                feedRepository
                    .getTimeline(cursor = null)
                    .onSuccess { page ->
                        // Replace head; cursor becomes the response cursor (may be null
                        // when the entire feed fits in one page).
                        setState {
                            copy(
                                feedItems = page.feedItems.dedupeClusterContext().toImmutableList(),
                                nextCursor = page.nextCursor,
                                endReached = page.nextCursor == null,
                                loadStatus = FeedLoadStatus.Idle,
                            )
                        }
                    }.onFailure { throwable ->
                        // Preserve feedItems on refresh failure; surface as a snackbar.
                        setState { copy(loadStatus = FeedLoadStatus.Idle) }
                        sendEffect(FeedEffect.ShowError(throwable.toFeedError()))
                    }
            }
        }

        private fun loadMore() {
            val current = uiState.value
            // End-of-feed: do nothing. Idempotent — repeat LoadMore once
            // endReached has flipped is a no-op.
            if (current.endReached) return
            // Mutually exclusive with refresh / initial-load: if any fetch is
            // already in flight, drop the LoadMore event. Prevents the
            // overlapping-fetch race where two getTimeline calls both update
            // feedItems + nextCursor and the last writer wins non-deterministically.
            if (current.loadStatus != FeedLoadStatus.Idle) return
            setState { copy(loadStatus = FeedLoadStatus.Appending) }
            viewModelScope.launch {
                feedRepository
                    .getTimeline(cursor = current.nextCursor)
                    .onSuccess { page ->
                        setState {
                            // De-dupe by FeedItemUi.key so a server returning a page
                            // that overlaps the current tail (rare but possible during
                            // cursor resyncs) doesn't show the same item twice. The
                            // key is the leaf URI for ReplyCluster and the post URI
                            // for Single — stable across paging.
                            val seen = feedItems.mapTo(HashSet()) { it.key }
                            val merged = (feedItems + page.feedItems.filter { seen.add(it.key) })
                            copy(
                                feedItems = merged.dedupeClusterContext().toImmutableList(),
                                nextCursor = page.nextCursor,
                                endReached = page.nextCursor == null,
                                loadStatus = FeedLoadStatus.Idle,
                            )
                        }
                    }.onFailure { throwable ->
                        // Preserve feedItems AND cursor on append failure so the user
                        // can retry from the same page boundary.
                        setState { copy(loadStatus = FeedLoadStatus.Idle) }
                        sendEffect(FeedEffect.ShowError(throwable.toFeedError()))
                    }
            }
        }

        private fun applyInitialPage(page: TimelinePage) {
            val deduped = page.feedItems.dedupeClusterContext().toImmutableList()
            setState {
                copy(
                    feedItems = deduped,
                    nextCursor = page.nextCursor,
                    endReached = page.nextCursor == null || deduped.isEmpty(),
                    loadStatus = FeedLoadStatus.Idle,
                )
            }
        }

        /**
         * Optimistic like / unlike. Flips `viewer.isLikedByViewer` and adjusts
         * `stats.likeCount` immediately, then fires the repository call. On
         * success, rewrites `viewer.likeUri` (a like creates a new record
         * whose AT URI is unknown until the server responds). On failure,
         * restores the pre-tap snapshot of the affected post and emits a
         * non-sticky [FeedEffect.ShowError]. The post is located by id so a
         * stale [PostUi] reference (from a recomposition that happened while
         * the call was in flight) doesn't desync the rollback target.
         */
        private fun toggleLike(post: PostUi) {
            val currentItems = uiState.value.feedItems
            val snapshot = currentItems.findPost(post.id) ?: return
            val wasLiked = snapshot.viewer.isLikedByViewer
            val optimistic =
                snapshot.copy(
                    viewer = snapshot.viewer.copy(isLikedByViewer = !wasLiked),
                    stats =
                        snapshot.stats.copy(
                            likeCount =
                                (snapshot.stats.likeCount + if (wasLiked) -1 else 1)
                                    .coerceAtLeast(0),
                        ),
                )
            setState { copy(feedItems = feedItems.replacePost(optimistic)) }

            viewModelScope.launch {
                val result =
                    if (wasLiked) {
                        val likeUri = snapshot.viewer.likeUri
                        if (likeUri == null) {
                            // No prior likeUri means we never received the
                            // server-assigned URI from a previous like (the
                            // mapper omits it for never-liked posts, and a
                            // tap-tap-fast race could land here). Treat as a
                            // failure so the optimistic toggle rolls back —
                            // we'd otherwise have no way to call unlike.
                            Result.failure(IllegalStateException("no likeUri to unlike"))
                        } else {
                            likeRepostRepository.unlike(AtUri(likeUri))
                        }
                    } else {
                        likeRepostRepository.like(StrongRef(uri = AtUri(snapshot.id), cid = Cid(snapshot.cid)))
                    }
                result
                    .onSuccess { value ->
                        // Like → store the new likeUri (an AtUri); unlike →
                        // clear it. Re-fetch the post by id so a concurrent
                        // refresh that landed mid-call doesn't lose its
                        // updates.
                        setState {
                            val currentPost = feedItems.findPost(post.id) ?: return@setState this
                            val newViewer =
                                if (wasLiked) {
                                    currentPost.viewer.copy(likeUri = null)
                                } else {
                                    currentPost.viewer.copy(likeUri = (value as AtUri).raw)
                                }
                            copy(feedItems = feedItems.replacePost(currentPost.copy(viewer = newViewer)))
                        }
                    }.onFailure { throwable ->
                        setState { copy(feedItems = feedItems.replacePost(snapshot)) }
                        sendEffect(FeedEffect.ShowError(throwable.toFeedError()))
                    }
            }
        }

        /**
         * Optimistic repost / unrepost. Mirrors [toggleLike] in shape — the
         * only differences are which fields move (`isRepostedByViewer`,
         * `repostCount`, `repostUri`) and which repository entry points we
         * call. Kept as a separate function rather than parameterized over
         * the like/repost projection because the combined version is harder
         * to read and the per-toggle handlers are short.
         */
        private fun toggleRepost(post: PostUi) {
            val currentItems = uiState.value.feedItems
            val snapshot = currentItems.findPost(post.id) ?: return
            val wasReposted = snapshot.viewer.isRepostedByViewer
            val optimistic =
                snapshot.copy(
                    viewer = snapshot.viewer.copy(isRepostedByViewer = !wasReposted),
                    stats =
                        snapshot.stats.copy(
                            repostCount =
                                (snapshot.stats.repostCount + if (wasReposted) -1 else 1)
                                    .coerceAtLeast(0),
                        ),
                )
            setState { copy(feedItems = feedItems.replacePost(optimistic)) }

            viewModelScope.launch {
                val result =
                    if (wasReposted) {
                        val repostUri = snapshot.viewer.repostUri
                        if (repostUri == null) {
                            Result.failure(IllegalStateException("no repostUri to unrepost"))
                        } else {
                            likeRepostRepository.unrepost(AtUri(repostUri))
                        }
                    } else {
                        likeRepostRepository.repost(StrongRef(uri = AtUri(snapshot.id), cid = Cid(snapshot.cid)))
                    }
                result
                    .onSuccess { value ->
                        setState {
                            val currentPost = feedItems.findPost(post.id) ?: return@setState this
                            val newViewer =
                                if (wasReposted) {
                                    currentPost.viewer.copy(repostUri = null)
                                } else {
                                    currentPost.viewer.copy(repostUri = (value as AtUri).raw)
                                }
                            copy(feedItems = feedItems.replacePost(currentPost.copy(viewer = newViewer)))
                        }
                    }.onFailure { throwable ->
                        setState { copy(feedItems = feedItems.replacePost(snapshot)) }
                        sendEffect(FeedEffect.ShowError(throwable.toFeedError()))
                    }
            }
        }

        private fun Throwable.toFeedError(): FeedError =
            when (this) {
                is NoSessionException -> FeedError.Unauthenticated
                is IOException -> FeedError.Network
                else -> FeedError.Unknown(cause = message)
            }
    }

/**
 * Locate a [PostUi] by id across all visible feed entries — both the
 * lone post inside a [FeedItemUi.Single] and any of root / parent / leaf
 * inside a [FeedItemUi.ReplyCluster]. Returns the first match (each post
 * id is globally unique within the feed by construction; the de-dupe
 * pass in the repository enforces this).
 */
private fun ImmutableList<FeedItemUi>.findPost(id: String): PostUi? =
    firstNotNullOfOrNull { item ->
        when (item) {
            is FeedItemUi.Single -> item.post.takeIf { it.id == id }
            is FeedItemUi.ReplyCluster ->
                when (id) {
                    item.root.id -> item.root
                    item.parent.id -> item.parent
                    item.leaf.id -> item.leaf
                    else -> null
                }
        }
    }

/**
 * Replace the post with id == [updated.id] inside the list, preserving
 * the surrounding [FeedItemUi.Single] / [FeedItemUi.ReplyCluster] shape.
 * No-ops the entries that don't match. The cluster branch swaps only
 * the matching slot (root / parent / leaf) and reuses untouched
 * references via referential equality so unrelated cluster posts don't
 * trigger Compose recomposition.
 */
private fun ImmutableList<FeedItemUi>.replacePost(updated: PostUi): ImmutableList<FeedItemUi> {
    val targetId = updated.id
    return map { item ->
        when (item) {
            is FeedItemUi.Single ->
                if (item.post.id == targetId) item.copy(post = updated) else item
            is FeedItemUi.ReplyCluster -> {
                val root = if (item.root.id == targetId) updated else item.root
                val parent = if (item.parent.id == targetId) updated else item.parent
                val leaf = if (item.leaf.id == targetId) updated else item.leaf
                if (root === item.root && parent === item.parent && leaf === item.leaf) {
                    item
                } else {
                    item.copy(root = root, parent = parent, leaf = leaf)
                }
            }
        }
    }.toImmutableList()
}
